from django.test import TestCase
from django.contrib.auth.models import User
from .models import Interaction
from rest_framework.test import APIClient
from rest_framework import status
from unittest.mock import patch

from django.urls import reverse

class ChatbotTest(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)
        self.url = reverse('chatbot')

    def test_interaction_model(self):
        interaction = Interaction.objects.create(
            user=self.user,
            input_text="Hello",
            output_text="Hi there"
        )
        self.assertEqual(interaction.input_text, "Hello")
        self.assertEqual(interaction.output_text, "Hi there")
        self.assertEqual(interaction.user, self.user)

    @patch('chatbot.views.env', return_value='fake-api-key')
    @patch('chatbot.views.genai')
    def test_post_chatbot_saves_interaction(self, mock_genai, mock_env):
        # Mock the chat session and response
        mock_model = mock_genai.GenerativeModel.return_value
        mock_chat_session = mock_model.start_chat.return_value
        mock_response = mock_chat_session.send_message.return_value
        mock_response.text = "I am a bot"

        response = self.client.post(self.url, {'message': 'Hello bot'})
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['message'], 'I am a bot')
        
        # Verify interaction is saved
        self.assertEqual(Interaction.objects.count(), 1)
        interaction = Interaction.objects.first()
        self.assertEqual(interaction.input_text, 'Hello bot')
        self.assertEqual(interaction.output_text, 'I am a bot')

    def test_get_chatbot_history(self):
        Interaction.objects.create(user=self.user, input_text="Q1", output_text="A1")
        Interaction.objects.create(user=self.user, input_text="Q2", output_text="A2")

        response = self.client.get(self.url)
        
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
        self.assertEqual(response.data[0]['input_text'], "Q1")
        self.assertEqual(response.data[1]['input_text'], "Q2")
