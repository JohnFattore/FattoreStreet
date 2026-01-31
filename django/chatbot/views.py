from django.shortcuts import render
import environ
import google.generativeai as genai
from rest_framework.views import APIView
from .serializer import ChatMessageSerializer, InteractionSerializer
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
import json
import os
from .models import Interaction

env = environ.Env()
environ.Env.read_env()

current_dir = os.path.dirname(os.path.abspath(__file__))

# Build the path to the JSON file in the same folder
json_path = os.path.join(current_dir, 'principles.json')

with open(json_path, 'r') as f:
    data = json.load(f)

# gemini chatbot
class ChatbotView(APIView):
    permission_classes = [IsAuthenticated]
    def get(self, request):
        interactions = Interaction.objects.filter(user=request.user).order_by('timestamp')
        serializer = InteractionSerializer(interactions, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request):
        api_key = env("GOOGLE_API_KEY")
        genai.configure(api_key=api_key)

        generation_config = {
        "temperature": 0.5,
        "top_p": 0.95,
        "top_k": 40,
        "max_output_tokens": 8192,
        "response_mime_type": "text/plain",
        }

        model = genai.GenerativeModel(
        model_name="gemini-flash-latest",
        generation_config=generation_config,
        system_instruction=f"Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds. {data}"
        )

        # Retrieve previous interactions
        previous_interactions = Interaction.objects.filter(user=request.user).order_by('-timestamp')[:10]
        history = []
        for interaction in reversed(list(previous_interactions)):
            history.append({"role": "user", "parts": [interaction.input_text]})
            history.append({"role": "model", "parts": [interaction.output_text]})

        chat_session = model.start_chat(history=history)
        # i should use a serializer, handle errors up front please instead of kicking down the road
        user_message = self.request.data["message"]

        if (user_message == ''):
            return Response({"message": f"Input can't be blank"}, status=status.HTTP_400_BAD_REQUEST)

        response = chat_session.send_message(user_message)
        
        # Save the interaction
        Interaction.objects.create(
            user=request.user,
            input_text=user_message,
            output_text=response.text
        )

        return Response({"message": f"{response.text}"}, status=status.HTTP_200_OK)