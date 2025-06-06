from django.shortcuts import render
import environ
import google.generativeai as genai
from rest_framework.views import APIView
from .serializer import ChatMessageSerializer
from rest_framework.response import Response
from rest_framework import status
import json
import os

env = environ.Env()
environ.Env.read_env()

current_dir = os.path.dirname(os.path.abspath(__file__))

# Build the path to the JSON file in the same folder
json_path = os.path.join(current_dir, 'principles.json')

with open(json_path, 'r') as f:
    data = json.load(f)

class ChatbotView(APIView):
    def post(self, request):
        api_key = env("GOOGLE_API_KEY")
        genai.configure(api_key=api_key)

        generation_config = {
        "temperature": 0.5,
        "top_p": 0.95,
        "top_k": 40,
        "max_output_tokens": 1024,
        "response_mime_type": "text/plain",
        }

        model = genai.GenerativeModel(
        model_name="gemini-2.0-flash",
        generation_config=generation_config,
        system_instruction=f"Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds. {data}"
        )

        chat_session = model.start_chat()
        # i should use a serializer, handle errors up front please instead of kicking down the road
        user_message = self.request.data["message"]

        if (user_message == ''):
            return Response({"message": f"Input can't be blank"}, status=status.HTTP_400_BAD_REQUEST)

        response = chat_session.send_message(user_message)
        return Response({"message": f"{response.text}"}, status=status.HTTP_200_OK)