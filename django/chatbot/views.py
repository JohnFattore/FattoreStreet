from collections.abc import Iterable
from functools import cache
import json
import logging
import os

import boto3
import environ
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError
from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, status
from rest_framework.exceptions import APIException
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Conversation, Interaction
from .serializer import ChatMessageSerializer, ConversationSerializer, ConversationDetailSerializer

logger = logging.getLogger(__name__)

env = environ.Env()
environ.Env.read_env()

current_dir = os.path.dirname(os.path.abspath(__file__))

# Build the path to the JSON file in the same folder
json_path = os.path.join(current_dir, 'principles.json')

with open(json_path, 'r') as f:
    data = json.load(f)

SYSTEM_PROMPT = (
    "Imagine you are a financial advisor obsessed with index funds. You are a Boglehead who wants "
    f"everyone to invest in low-cost, highly diverse index funds. {data}"
)

# Any Bedrock Converse model works; switching models is an env change only. The default is a
# cross-region inference profile, so the IAM grant in deploy/iam/ must cover it and its regions.
DEFAULT_MODEL_ID = "us.meta.llama3-3-70b-instruct-v1:0"

# How many earlier exchanges of the same conversation are sent as context
HISTORY_TURNS = 10
TITLE_LENGTH = 60


class AdvisorUnavailable(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_detail = "The advisor is unavailable right now. Please try again in a moment."
    default_code = "advisor_unavailable"


def title_from(message: str) -> str:
    """Title a conversation from the start of its first question, cut at a word boundary."""
    text = " ".join(message.split())
    if len(text) <= TITLE_LENGTH:
        return text
    return text[:TITLE_LENGTH].rsplit(" ", 1)[0] + "…"


@cache
def bedrock_client():
    """One shared client per process; boto3 clients are thread-safe, so gunicorn threads share it.

    Credentials come from the default chain: the EC2 instance role in production,
    AWS_PROFILE (or env keys) in development. No API key is stored anywhere.
    """
    return boto3.client(
        "bedrock-runtime",
        region_name=env("AWS_REGION", default="us-east-1"),
        # Fail fast rather than hold a worker thread; adaptive retries absorb brief throttling
        config=Config(read_timeout=60, connect_timeout=5, retries={"max_attempts": 3, "mode": "adaptive"}),
    )


def ask_advisor(previous_interactions: Iterable[Interaction], message: str) -> str:
    """Ask the advisor through the Bedrock Converse API. Raises AdvisorUnavailable on any model error."""
    messages = []
    for interaction in previous_interactions:
        messages.append({"role": "user", "content": [{"text": interaction.input_text}]})
        messages.append({"role": "assistant", "content": [{"text": interaction.output_text}]})
    messages.append({"role": "user", "content": [{"text": message}]})

    try:
        response = bedrock_client().converse(
            modelId=env("ADVISOR_MODEL_ID", default=DEFAULT_MODEL_ID),
            system=[{"text": SYSTEM_PROMPT}],
            messages=messages,
            inferenceConfig={"temperature": 0.5, "topP": 0.95, "maxTokens": 2048},
        )
    except (ClientError, BotoCoreError) as exc:
        logger.exception("Bedrock advisor call failed")
        raise AdvisorUnavailable() from exc

    reply = "".join(block["text"] for block in response["output"]["message"]["content"] if "text" in block)
    if not reply.strip():
        logger.error("Bedrock advisor returned no text (stopReason=%s)", response.get("stopReason"))
        raise AdvisorUnavailable()
    return reply


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
