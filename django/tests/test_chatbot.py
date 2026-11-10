import json
from datetime import date, datetime, timezone
from pathlib import Path
from unittest.mock import patch, MagicMock
from botocore.exceptions import ClientError, EndpointConnectionError
from django.contrib.auth.models import User
from django.core.cache import cache
from django.db import DatabaseError
from django.test import override_settings
from django.urls import reverse
from rest_framework import status
from chatbot.models import AdvisorUsage, Conversation, Interaction
from chatbot.prompt import CONTEXT_DIR, build_system_prompt, reference_text
from chatbot.views import AdvisorThrottle, bedrock_client, current_month, title_from
from tests.base import BaseAPITestCase


def converse_reply(text: str, input_tokens: int = 100, output_tokens: int = 50) -> dict:
    return {
        "output": {"message": {"role": "assistant", "content": [{"text": text}]}},
        "stopReason": "end_turn",
        "usage": {"inputTokens": input_tokens, "outputTokens": output_tokens, "totalTokens": input_tokens + output_tokens},
    }


class BedrockPatchMixin:
    """Mocks the Bedrock client; the reply is what `self.mock_client.converse` returns."""

    def setUp(self):
        super().setUp()
        self.mock_client = MagicMock()
        patch("chatbot.views.bedrock_client", return_value=self.mock_client).start()
        self.addCleanup(patch.stopall)
        self.mock_client.converse.return_value = converse_reply("You should invest in low-cost index funds!")

    def sent_messages(self) -> list[tuple[str, str]]:
        messages = self.mock_client.converse.call_args.kwargs["messages"]
        return [(m["role"], m["content"][0]["text"]) for m in messages]


class TitleFromTest(BaseAPITestCase):
    def test_short_message_kept_whole(self):
        self.assertEqual(title_from("  Roth or   traditional? "), "Roth or traditional?")

    def test_long_message_cut_at_word_boundary(self):
        title = title_from("How should I split my money between US and international index funds over time?")
        self.assertTrue(title.endswith("…"))
        self.assertLessEqual(len(title), 61)
        self.assertNotIn("internati…", title)


class SystemPromptTest(BaseAPITestCase):
    def test_includes_every_context_file_in_order(self):
        prompt = build_system_prompt(date(2026, 11, 10))
        headings = ["# Who you are", "# Fattore Street principles", "# IRAs", "# 401(k)"]
        positions = [prompt.index(heading) for heading in headings]
        self.assertEqual(positions, sorted(positions))
        self.assertIn("Never tell the user to buy or sell a specific security", prompt)
        self.assertTrue(prompt.endswith("Today's date is 2026-11-10."))

    def test_bond_rule_matches_its_examples(self):
        self.assertIn("age minus 20 as a percent in bonds", reference_text())

    def test_covers_every_fund_on_the_home_page(self):
        """The home page and the advisor keep separate copies of the principles; catch drift."""
        react_json = Path(__file__).resolve().parents[2] / "react-app/src/data/principles.json"
        if not react_json.exists():
            self.skipTest("react-app not checked out")
        principles = json.loads(react_json.read_text())["principles"]
        tickers = {
            value
            for principle in principles
            for row in principle.get("table", {}).get("data", [])
            for key, value in row.items()
            if key != "Brokerage Company" and value != "N/A" and value.isupper() and " " not in value
        }
        self.assertTrue(tickers)
        advisor_text = (CONTEXT_DIR / "10_principles.md").read_text()
        self.assertEqual({t for t in tickers if t not in advisor_text}, set())


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


class ConversationCreateTest(BedrockPatchMixin, BaseAPITestCase):
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
        self.assertEqual(self.sent_messages(), [('user', 'Fresh topic')])

    def test_sends_system_prompt_and_configured_model(self):
        self.authenticate_client()
        with patch.dict("os.environ", {"ADVISOR_MODEL_ID": "qwen.test-model"}):
            self.client.post(self.url, {"message": "Hello"}, format='json')
        kwargs = self.mock_client.converse.call_args.kwargs
        self.assertEqual(kwargs['modelId'], "qwen.test-model")
        self.assertEqual(kwargs['system'][0]['text'], build_system_prompt())

    def test_throttled_model_call_returns_503_and_creates_nothing(self):
        self.mock_client.converse.side_effect = ClientError(
            {"Error": {"Code": "ThrottlingException", "Message": "Too many requests"}}, "Converse"
        )
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertEqual(response.data['detail'].code, 'advisor_unavailable')
        self.assertFalse(Conversation.objects.exists())

    def test_unreachable_model_returns_503_and_creates_nothing(self):
        self.mock_client.converse.side_effect = EndpointConnectionError(endpoint_url="https://bedrock")
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertFalse(Conversation.objects.exists())

    def test_empty_model_reply_returns_503_and_creates_nothing(self):
        self.mock_client.converse.return_value = converse_reply("  ")
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertFalse(Conversation.objects.exists())

    def test_malformed_model_response_returns_503_and_creates_nothing(self):
        self.mock_client.converse.return_value = {"output": {}, "stopReason": "end_turn"}
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertFalse(Conversation.objects.exists())

    def test_truncated_reply_is_kept_and_logged(self):
        self.mock_client.converse.return_value = {
            **converse_reply("Index funds are great because"),
            "stopReason": "max_tokens",
        }
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="WARNING") as logs:
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(Interaction.objects.get().output_text, "Index funds are great because")
        self.assertIn("truncated", logs.output[0])

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


class ConversationMessageTest(BedrockPatchMixin, BaseAPITestCase):
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
        self.assertEqual(
            self.sent_messages(),
            [('user', 'Earlier'), ('assistant', 'Earlier answer'), ('user', 'Follow-up')],
        )

    def test_failed_model_call_adds_no_message(self):
        self.mock_client.converse.side_effect = ClientError(
            {"Error": {"Code": "ModelErrorException", "Message": "boom"}}, "Converse"
        )
        self.authenticate_client()
        with self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Follow-up"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertEqual(self.conversation.interactions.count(), 1)
        self.conversation.refresh_from_db()
        self.assertEqual(self.conversation.last_message_at, datetime(2026, 1, 1, tzinfo=timezone.utc))

    def test_post_to_other_users_conversation_is_not_found(self):
        other_user = User.objects.create_user(username='otheruser', password='pass')
        other = Conversation.objects.create(user=other_user, title='Secret')
        self.authenticate_client()
        url = reverse('conversation-messages', args=[other.pk])
        response = self.client.post(url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.mock_client.converse.assert_not_called()

    def test_post_empty_message_rejected(self):
        self.authenticate_client()
        response = self.client.post(self.url, {}, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_post_message_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class BedrockClientConfigTest(BaseAPITestCase):
    def test_retry_budget_fits_under_nginx_proxy_timeout(self):
        """nginx's default proxy_read_timeout is 60s; a slower worst case surfaces as a 504, not our 503."""
        bedrock_client.cache_clear()
        self.addCleanup(bedrock_client.cache_clear)
        with patch.dict("os.environ", {"AWS_ACCESS_KEY_ID": "test", "AWS_SECRET_ACCESS_KEY": "test"}):  # pragma: allowlist secret
            config = bedrock_client().meta.config
        attempts = config.retries["total_max_attempts"]
        self.assertLess(attempts * (config.read_timeout + config.connect_timeout), 60)


class AdvisorSpendCapTest(BedrockPatchMixin, BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('conversation-list')
        self.authenticate_client()

    def test_successful_call_records_tokens_for_the_month(self):
        self.client.post(self.url, {"message": "Hello"}, format='json')
        self.client.post(self.url, {"message": "Again"}, format='json')
        usage = AdvisorUsage.objects.get(month=current_month())
        self.assertEqual((usage.input_tokens, usage.output_tokens), (200, 100))

    def test_discarded_reply_still_counts_tokens(self):
        """Bedrock bills an empty reply, so the ledger must too."""
        self.mock_client.converse.return_value = converse_reply("  ", input_tokens=300, output_tokens=0)
        with self.assertLogs("chatbot.views", level="ERROR"):
            self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(AdvisorUsage.objects.get().input_tokens, 300)

    def test_cap_reached_refuses_without_calling_bedrock(self):
        AdvisorUsage.objects.create(month=current_month(), input_tokens=900, output_tokens=100)
        with patch.dict("os.environ", {"ADVISOR_MONTHLY_TOKEN_CAP": "1000"}), \
                self.assertLogs("chatbot.views", level="WARNING"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.assertEqual(response.data['detail'].code, 'advisor_budget_exhausted')
        self.mock_client.converse.assert_not_called()
        self.assertFalse(Conversation.objects.exists())

    def test_cap_blocks_follow_up_messages_too(self):
        conversation = Conversation.objects.create(user=self.user, title='Chat')
        with patch.dict("os.environ", {"ADVISOR_MONTHLY_TOKEN_CAP": "0"}), \
                self.assertLogs("chatbot.views", level="WARNING"):
            response = self.client.post(
                reverse('conversation-messages', kwargs={'pk': conversation.pk}), {"message": "More"}, format='json'
            )
        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        self.mock_client.converse.assert_not_called()

    def test_last_months_usage_does_not_count(self):
        AdvisorUsage.objects.create(month=datetime(2020, 1, 1).date(), input_tokens=10**9)
        response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_failed_usage_write_still_returns_the_reply(self):
        with patch("chatbot.views.AdvisorUsage.objects.get_or_create", side_effect=DatabaseError("db down")), \
                self.assertLogs("chatbot.views", level="ERROR"):
            response = self.client.post(self.url, {"message": "Hello"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_deleting_conversations_does_not_reset_usage(self):
        self.client.post(self.url, {"message": "Hello"}, format='json')
        Conversation.objects.all().delete()
        self.assertEqual(AdvisorUsage.objects.get().total_tokens, 150)


@override_settings(CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}})
class AdvisorThrottleTest(BedrockPatchMixin, BaseAPITestCase):
    def setUp(self):
        super().setUp()
        cache.clear()
        self.addCleanup(cache.clear)
        patch.object(AdvisorThrottle, "rate", "2/day").start()
        self.url = reverse('conversation-list')
        self.authenticate_client()

    def test_questions_past_the_rate_are_throttled(self):
        for _ in range(2):
            self.assertEqual(self.client.post(self.url, {"message": "Hi"}, format='json').status_code, 201)
        response = self.client.post(self.url, {"message": "Hi"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)
        self.assertEqual(self.mock_client.converse.call_count, 2)
        self.assertEqual(
            response.data['detail'], "You've reached the advisor's question limit for now. Please try again later."
        )
        self.assertGreater(int(response['Retry-After']), 0)

    def test_follow_ups_share_the_same_limit(self):
        conversation = Conversation.objects.create(user=self.user, title='Chat')
        message_url = reverse('conversation-messages', kwargs={'pk': conversation.pk})
        self.client.post(self.url, {"message": "Hi"}, format='json')
        self.client.post(message_url, {"message": "More"}, format='json')
        response = self.client.post(message_url, {"message": "Even more"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)

    def test_reading_conversations_is_not_throttled(self):
        for _ in range(5):
            self.assertEqual(self.client.get(self.url).status_code, status.HTTP_200_OK)

    def test_limit_is_per_user(self):
        for _ in range(2):
            self.client.post(self.url, {"message": "Hi"}, format='json')
        other = User.objects.create_user(username='otheruser', password='pass')
        self.client.force_authenticate(user=other)
        response = self.client.post(self.url, {"message": "Hi"}, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
