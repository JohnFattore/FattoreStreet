from unittest.mock import patch, MagicMock
from django.urls import reverse
from rest_framework import status
from chatbot.models import Interaction
from tests.base import BaseAPITestCase


class ChatbotGetTest(BaseAPITestCase):
    """Tests for retrieving chatbot interaction history."""

    def setUp(self):
        super().setUp()
        self.url = reverse('chatbot')

    def test_get_history_authenticated(self):
        Interaction.objects.create(user=self.user, input_text='Hello', output_text='Hi there!')
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['input_text'], 'Hello')

    def test_get_history_empty(self):
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 0)

    def test_get_history_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_history_scoped_to_user(self):
        """User should only see their own interactions."""
        from django.contrib.auth.models import User
        other_user = User.objects.create_user(username='otheruser', password='pass')
        Interaction.objects.create(user=self.user, input_text='Mine', output_text='Yours')
        Interaction.objects.create(user=other_user, input_text='Other', output_text='Secret')
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['input_text'], 'Mine')


class ChatbotPostTest(BaseAPITestCase):
    """Tests for sending messages to the chatbot (Gemini mocked)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('chatbot')

        self.mock_genai = patch("chatbot.views.genai").start()
        patch("chatbot.views.env", return_value="fake-api-key").start()
        self.addCleanup(patch.stopall)

        mock_client = MagicMock()
        self.mock_genai.Client.return_value = mock_client
        mock_chat = MagicMock()
        mock_client.chats.create.return_value = mock_chat
        mock_response = MagicMock()
        mock_response.text = "You should invest in low-cost index funds!"
        mock_chat.send_message.return_value = mock_response

    def test_post_message(self):
        self.authenticate_client()
        data = {"message": "What should I invest in?"}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("index funds", response.data["message"].lower())
        self.assertEqual(Interaction.objects.count(), 1)
        interaction = Interaction.objects.get()
        self.assertEqual(interaction.user, self.user)
        self.assertEqual(interaction.input_text, "What should I invest in?")

    def test_post_message_unauthenticated(self):
        self.unauthenticate_client()
        data = {"message": "Hello"}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_post_empty_message_rejected(self):
        self.authenticate_client()
        data = {}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
