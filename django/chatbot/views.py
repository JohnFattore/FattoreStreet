from collections.abc import Iterable
from datetime import date
from functools import cache
import json
import logging
import math
import os

import boto3
import environ
from botocore.client import BaseClient
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError
from django.db import transaction
from django.db.models import F
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, status
from rest_framework.exceptions import APIException, Throttled
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.throttling import UserRateThrottle
from rest_framework.views import APIView

from .models import AdvisorUsage, Conversation, Interaction
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

# Any Bedrock Converse model works, but ADVISOR_MODEL_ID and AWS_REGION are both pinned by the IAM
# grant in deploy/iam/ec2-fattorestreet-role-bedrock.json (profile ARN, its region, and the regions
# it routes to). Changing either without editing that policy turns every call into a 503.
DEFAULT_MODEL_ID = "us.meta.llama3-3-70b-instruct-v1:0"

# How many earlier exchanges of the same conversation are sent as context
HISTORY_TURNS = 10
TITLE_LENGTH = 60
MAX_REPLY_TOKENS = 2048

# Spend cap: Bedrock tokens (input + output, all users) allowed per UTC calendar month. Expected use
# is about 5M tokens a month; 20M is roughly $15 at Llama 3.3 70B prices. 0 turns the advisor off.
DEFAULT_MONTHLY_TOKEN_CAP = 20_000_000


class AdvisorUnavailable(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_detail = "The advisor is unavailable right now. Please try again in a moment."
    default_code = "advisor_unavailable"


class AdvisorBudgetExhausted(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_detail = "The advisor has reached its monthly usage limit and will be back next month."
    default_code = "advisor_budget_exhausted"


class AdvisorRateLimited(Throttled):
    """A 429 whose message reads well in the chat box; DRF's default counts down in seconds."""

    def __init__(self, wait: float | None):
        super().__init__(detail="You've reached the advisor's question limit for now. Please try again later.")
        # Set after super(), which would otherwise append "Expected available in N seconds" to the
        # message; the Retry-After header still carries the wait
        self.wait = math.ceil(wait) if wait is not None else None


class AdvisorThrottle(UserRateThrottle):
    """Per-user limit on questions, so one account cannot spend the whole monthly budget.

    Only POST asks the model; reading conversations is never throttled. Counts live in the
    Django cache, which is Redis in production and a no-op DummyCache in development, so
    the limit does not apply locally. The monthly token cap still does.
    """
    scope = "advisor"
    rate = env("ADVISOR_THROTTLE_RATE", default="50/day")

    def allow_request(self, request, view) -> bool:
        if request.method != "POST":
            return True
        return super().allow_request(request, view)


class AdvisorThrottledMixin:
    throttle_classes = [AdvisorThrottle]

    def throttled(self, request, wait):
        raise AdvisorRateLimited(wait)


def current_month() -> date:
    return timezone.now().date().replace(day=1)


def check_budget() -> None:
    """Refuse before calling Bedrock once this month's tokens reach ADVISOR_MONTHLY_TOKEN_CAP.

    Concurrent requests can each pass this check, so a month can overshoot the cap by a
    few replies. That is a few thousand tokens, which is fine for a cost guard.
    """
    cap = env.int("ADVISOR_MONTHLY_TOKEN_CAP", default=DEFAULT_MONTHLY_TOKEN_CAP)
    usage = AdvisorUsage.objects.filter(month=current_month()).first()
    spent = usage.total_tokens if usage else 0
    if spent >= cap:
        logger.warning("Advisor monthly token cap reached (%d of %d); refusing to call Bedrock", spent, cap)
        raise AdvisorBudgetExhausted()


def record_usage(response: dict) -> None:
    """Add a Converse response's token usage to this month's ledger, with an atomic increment."""
    usage = response.get("usage") or {}
    input_tokens = int(usage.get("inputTokens", 0))
    output_tokens = int(usage.get("outputTokens", 0))
    if not input_tokens and not output_tokens:
        # Converse always reports usage, so this means the cap is blind to this call
        logger.warning("Bedrock advisor response reported no token usage")
        return
    month = current_month()
    AdvisorUsage.objects.get_or_create(month=month)
    AdvisorUsage.objects.filter(month=month).update(
        input_tokens=F("input_tokens") + input_tokens,
        output_tokens=F("output_tokens") + output_tokens,
    )


def title_from(message: str) -> str:
    """Title a conversation from the start of its first question, cut at a word boundary."""
    text = " ".join(message.split())
    if len(text) <= TITLE_LENGTH:
        return text
    return text[:TITLE_LENGTH].rsplit(" ", 1)[0] + "…"


@cache
def bedrock_client() -> BaseClient:
    """One shared client per process; boto3 clients are thread-safe, so gunicorn threads share it.

    Credentials come from the default chain: the EC2 instance role in production,
    AWS_PROFILE (or env keys) in development. No API key is stored anywhere.
    """
    return boto3.client(
        "bedrock-runtime",
        region_name=env("AWS_REGION", default="us-east-1"),
        # nginx gives up after its default 60s proxy_read_timeout, so the whole retry budget must fit
        # under it: 2 attempts x (20s read + 5s connect) = 50s worst case, leaving room for backoff.
        # Throttling errors return in milliseconds, so the retry only costs real time when Bedrock is
        # slow. An answer that needs more than 20s to generate fails; streaming is the fix for that.
        config=Config(read_timeout=20, connect_timeout=5, retries={"total_max_attempts": 2, "mode": "adaptive"}),
    )


def ask_advisor(previous_interactions: Iterable[Interaction], message: str) -> str:
    """Ask the advisor through the Bedrock Converse API.

    Raises AdvisorBudgetExhausted past the monthly token cap, and AdvisorUnavailable on any model error.
    """
    check_budget()

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
            inferenceConfig={"temperature": 0.5, "topP": 0.95, "maxTokens": MAX_REPLY_TOKENS},
        )
    except (ClientError, BotoCoreError) as exc:
        logger.exception("Bedrock advisor call failed")
        raise AdvisorUnavailable() from exc

    # Count the tokens before anything can discard the reply: Bedrock bills them either way
    record_usage(response)

    try:
        reply ="".join(block["text"] for block in response["output"]["message"]["content"] if "text" in block)
    except (KeyError, TypeError) as exc:
        logger.exception("Bedrock advisor returned an unexpected response shape")
        raise AdvisorUnavailable() from exc

    if response.get("stopReason") == "max_tokens":
        # A cut-off answer still beats none; the log shows whether MAX_REPLY_TOKENS needs raising
        logger.warning("Bedrock advisor reply hit maxTokens=%d and was truncated", MAX_REPLY_TOKENS)
    if not reply.strip():
        logger.error("Bedrock advisor returned no text (stopReason=%s)", response.get("stopReason"))
        raise AdvisorUnavailable()
    return reply


class ConversationListCreateView(AdvisorThrottledMixin, generics.ListAPIView):
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


class ConversationMessageView(AdvisorThrottledMixin, APIView):
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
