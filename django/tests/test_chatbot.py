from datetime import datetime, timezone
from unittest.mock import patch, MagicMock
from django.contrib.auth.models import User
from django.urls import reverse
from rest_framework import status
from chatbot.models import Conversation, Interaction
from chatbot.views import title_from
from tests.base import BaseAPITestCase


class GeminiPatchMixin:
    """Mocks the Gemini client; the reply text is in `self.mock_chat.send_message`."""

    def setUp(self):
        super().setUp()
        self.mock_genai = patch("chatbot.views.genai").start()
        patch("chatbot.views.env", return_value="fake-api-key").start()
        self.addCleanup(patch.stopall)

        self.mock_client = MagicMock()
        self.mock_genai.Client.return_value = self.mock_client
        self.mock_chat = MagicMock()
        self.mock_client.chats.create.return_value = self.mock_chat
        mock_response = MagicMock()
        mock_response.text = "You should invest in low-cost index funds!"
        self.mock_chat.send_message.return_value = mock_response


class TitleFromTest(BaseAPITestCase):
    def test_short_message_kept_whole(self):
        self.assertEqual(title_from("  Roth or   traditional? "), "Roth or traditional?")

    def test_long_message_cut_at_word_boundary(self):
        title = title_from("How should I split my money between US and international index funds over time?")
        self.assertTrue(title.endswith("…"))
        self.assertLessEqual(len(title), 61)
        self.assertNotIn("internati…", title)


class ConversationListTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('conversation-list')

    def test_list_most_recent_first(self):
        older = Conversation.objects.create(user=self.user, title='Older')
        newer = Conversation.objects.create(user=self.user, title='Newer')
        Conversation.objects.filter(pk=older.pk).update(last_message_at='2026-01-01T00:00:00Z')
        Conversation.objects.filter(pk=newer.pk).update(last_message_at='2026-02-01T00:00:00Z')
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual([c['title'] for c in response.data], ['Newer', 'Older'])
        self.assertNotIn('interactions', response.data[0])

    def test_list_scoped_to_user(self):
        other_user = User.objects.create_user(username='otheruser', password='pass')
        Conversation.objects.create(user=self.user, title='Mine')
        Conversation.objects.create(user=other_user, title='Secret')
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual([c['title'] for c in response.data], ['Mine'])

    def test_list_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class ConversationCreateTest(GeminiPatchMixin, BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('conversation-list')

    def test_create_asks_first_question(self):
        self.authenticate_client()
        response = self.client.post(self.url, {"message": "What should I invest in?"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['title'], "What should I invest in?")
        self.assertEqual(len(response.data['interactions']), 1)
        self.assertIn("index funds", response.data['interactions'][0]['output_text'].lower())
        conversation = Conversation.objects.get()
        self.assertEqual(conversation.user, self.user)

    def test_new_conversation_starts_without_context(self):
        """Other conversations never feed a new one."""
        earlier = Conversation.objects.create(user=self.user, title='Earlier')
        Interaction.objects.create(conversation=earlier, input_text='Old', output_text='Old answer')
        self.authenticate_client()
        self.client.post(self.url, {"message": "Fresh topic"}, format='json')
        self.assertEqual(self.mock_client.chats.create.call_args.kwargs['history'], [])

    def test_failed_model_call_creates_nothing(self):
        self.mock_chat.send_message.side_effect = RuntimeError("Gemini down")
        self.authenticate_client()
        with self.assertRaises(RuntimeError):
            self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertFalse(Conversation.objects.exists())

    def test_create_empty_message_rejected(self):
        self.authenticate_client()
        response = self.client.post(self.url, {}, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertFalse(Conversation.objects.exists())

    def test_create_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class ConversationDetailTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.conversation = Conversation.objects.create(user=self.user, title='Index funds')
        Interaction.objects.create(conversation=self.conversation, input_text='First', output_text='One')
        Interaction.objects.create(conversation=self.conversation, input_text='Second', output_text='Two')
        self.url = reverse('conversation-detail', args=[self.conversation.pk])
        other_user = User.objects.create_user(username='otheruser', password='pass')
        self.other_conversation = Conversation.objects.create(user=other_user, title='Secret')
        self.other_url = reverse('conversation-detail', args=[self.other_conversation.pk])

    def test_get_shows_interactions_oldest_first(self):
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual([i['input_text'] for i in response.data['interactions']], ['First', 'Second'])

    def test_rename(self):
        self.authenticate_client()
        last_message_at = self.conversation.last_message_at
        response = self.client.patch(self.url, {"title": "Three-fund portfolio"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.conversation.refresh_from_db()
        self.assertEqual(self.conversation.title, "Three-fund portfolio")
        # Renaming isn't activity, so the conversation keeps its place in the list
        self.assertEqual(self.conversation.last_message_at, last_message_at)

    def test_rename_blank_rejected(self):
        self.authenticate_client()
        response = self.client.patch(self.url, {"title": "   "}, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_put_not_allowed(self):
        self.authenticate_client()
        response = self.client.put(self.url, {"title": "New"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_405_METHOD_NOT_ALLOWED)

    def test_delete_removes_conversation_and_messages(self):
        self.authenticate_client()
        response = self.client.delete(self.url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Conversation.objects.filter(pk=self.conversation.pk).exists())
        self.assertFalse(Interaction.objects.filter(conversation_id=self.conversation.pk).exists())

    def test_other_users_conversation_is_not_found(self):
        self.authenticate_client()
        self.assertEqual(self.client.get(self.other_url).status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(
            self.client.patch(self.other_url, {"title": "Mine now"}, format='json').status_code,
            status.HTTP_404_NOT_FOUND,
        )
        self.assertEqual(self.client.delete(self.other_url).status_code, status.HTTP_404_NOT_FOUND)
        self.assertTrue(Conversation.objects.filter(pk=self.other_conversation.pk, title='Secret').exists())

    def test_detail_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class ConversationMessageTest(GeminiPatchMixin, BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.conversation = Conversation.objects.create(user=self.user, title='Index funds')
        Interaction.objects.create(conversation=self.conversation, input_text='Earlier', output_text='Earlier answer')
        Conversation.objects.filter(pk=self.conversation.pk).update(last_message_at='2026-01-01T00:00:00Z')
        self.url = reverse('conversation-messages', args=[self.conversation.pk])

    def test_post_message(self):
        self.authenticate_client()
        response = self.client.post(self.url, {"message": "What should I invest in?"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("index funds", response.data["message"].lower())
        self.assertEqual(self.conversation.interactions.count(), 2)
        self.conversation.refresh_from_db()
        # A new message moves the conversation to the top of the list
        self.assertGreater(self.conversation.last_message_at, datetime(2026, 1, 1, tzinfo=timezone.utc))

    def test_context_is_only_this_conversation(self):
        other = Conversation.objects.create(user=self.user, title='Other')
        Interaction.objects.create(conversation=other, input_text='Unrelated', output_text='Nope')
        self.authenticate_client()
        self.client.post(self.url, {"message": "Follow-up"}, format='json')
        history = self.mock_client.chats.create.call_args.kwargs['history']
        texts = [content.parts[0].text for content in history]
        self.assertEqual(texts, ['Earlier', 'Earlier answer'])

    def test_post_to_other_users_conversation_is_not_found(self):
        other_user = User.objects.create_user(username='otheruser', password='pass')
        other = Conversation.objects.create(user=other_user, title='Secret')
        self.authenticate_client()
        url = reverse('conversation-messages', args=[other.pk])
        response = self.client.post(url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.mock_chat.send_message.assert_not_called()

    def test_post_empty_message_rejected(self):
        self.authenticate_client()
        response = self.client.post(self.url, {}, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_message_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
