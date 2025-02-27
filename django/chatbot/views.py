from django.shortcuts import render
import os
import environ
import google.generativeai as genai
from rest_framework.views import APIView
from .serializer import ChatMessageSerializer
from rest_framework.response import Response
from rest_framework import status

env = environ.Env()
environ.Env.read_env()

class ChatbotView(APIView):
    def post(self, request):
        api_key = os.getenv("GOOGLE_API_KEY")
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
        )

        chat_session = model.start_chat(
        history=[
                {"role": "user", "parts": ["Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds."]}
            ]
        )
        # i should use a serializer, handle errors up front please instead of kicking down the road
        user_message = self.request.data["message"]

        if (user_message == ''):
            return Response({"message": f"Input can't be blank"}, status=status.HTTP_400_BAD_REQUEST)

        response = chat_session.send_message(user_message)
        print(response.text)
        return Response({"message": f"{response.text}"}, status=status.HTTP_200_OK)