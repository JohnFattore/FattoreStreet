from collections.abc import Iterable
import environ
from google import genai
from google.genai import types
from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, status
from rest_framework.views import APIView
from .serializer import ChatMessageSerializer, ConversationSerializer, ConversationDetailSerializer
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
import json
import os
from .models import Conversation, Interaction

env = environ.Env()
environ.Env.read_env()

current_dir = os.path.dirname(os.path.abspath(__file__))

# Build the path to the JSON file in the same folder
json_path = os.path.join(current_dir, 'principles.json')

with open(json_path, 'r') as f:
    data = json.load(f)

# How many earlier exchanges of the same conversation are sent as context
HISTORY_TURNS = 10
TITLE_LENGTH = 60


def title_from(message: str) -> str:
    """Title a conversation from the start of its first question, cut at a word boundary."""
    text = " ".join(message.split())
    if len(text) <= TITLE_LENGTH:
        return text
    return text[:TITLE_LENGTH].rsplit(" ", 1)[0] + "…"


# gemini chatbot
def ask_advisor(previous_interactions: Iterable[Interaction], message: str) -> str:
    api_key = env("GOOGLE_API_KEY")
    client = genai.Client(api_key=api_key)

    config = types.GenerateContentConfig(
        temperature=0.5,
        top_p=0.95,
        top_k=40,
        max_output_tokens=8192,
        response_mime_type="text/plain",
        system_instruction=f"Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants everyone to invest in low-cost, highly diverse index funds. {data}",
    )

    history = []
    for interaction in previous_interactions:
        history.append(types.Content(role="user", parts=[types.Part(text=interaction.input_text)]))
        history.append(types.Content(role="model", parts=[types.Part(text=interaction.output_text)]))

    chat_session = client.chats.create(
        model="gemini-flash-latest",
        config=config,
        history=history,
    )
    return chat_session.send_message(message).text


class ConversationListCreateView(generics.ListAPIView):
    serializer_class = ConversationSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return Conversation.objects.filter(user=self.request.user)

    # Starting a conversation means asking its first question, so no empty conversations exist
    def post(self, request):
        msg_serializer = ChatMessageSerializer(data=request.data)
        msg_serializer.is_valid(raise_exception=True)
        user_message = msg_serializer.validated_data["message"]

        # Ask before writing anything, so a failed model call leaves no orphan conversation
        reply = ask_advisor([], user_message)

        with transaction.atomic():
            conversation = Conversation.objects.create(user=request.user, title=title_from(user_message))
            Interaction.objects.create(conversation=conversation, input_text=user_message, output_text=reply)

        return Response(ConversationDetailSerializer(conversation).data, status=status.HTTP_201_CREATED)


class ConversationDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = ConversationDetailSerializer
    permission_classes = [IsAuthenticated]
    # Only the title is editable, so PUT has nothing to offer over PATCH
    http_method_names = ['get', 'patch', 'delete', 'head', 'options']

    def get_queryset(self):
        return Conversation.objects.filter(user=self.request.user).prefetch_related('interactions')


class ConversationMessageView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, pk):
        conversation = get_object_or_404(Conversation, pk=pk, user=request.user)
        msg_serializer = ChatMessageSerializer(data=request.data)
        msg_serializer.is_valid(raise_exception=True)
        user_message = msg_serializer.validated_data["message"]

        # Only this conversation's exchanges are context; a new conversation starts fresh
        previous_interactions = conversation.interactions.order_by('-timestamp', '-id')[:HISTORY_TURNS]
        reply = ask_advisor(reversed(list(previous_interactions)), user_message)

        with transaction.atomic():
            Interaction.objects.create(conversation=conversation, input_text=user_message, output_text=reply)
            conversation.last_message_at = timezone.now()
            conversation.save(update_fields=['last_message_at'])

        return Response({"message": reply}, status=status.HTTP_200_OK)
