from datetime import timedelta

from django.utils import timezone
from rest_framework import serializers

from .services import ALLOWED_LEVELS, get_allowed_sources

MAX_LOG_WINDOW_DAYS = 30
DEFAULT_LOG_WINDOW_HOURS = 1
MAX_LIMIT = 500
DEFAULT_LIMIT = 200


class LogsQuerySerializer(serializers.Serializer):
    source = serializers.CharField(required=True)
    start = serializers.DateTimeField(required=False)
    end = serializers.DateTimeField(required=False)
    minutes = serializers.IntegerField(
        required=False,
        min_value=1,
        max_value=MAX_LOG_WINDOW_DAYS * 24 * 60,
        default=DEFAULT_LOG_WINDOW_HOURS * 60,
    )
    q = serializers.CharField(required=False, allow_blank=True, default="", max_length=256)
    level = serializers.CharField(required=False, allow_blank=True, default="")
    next_token = serializers.CharField(required=False, allow_blank=True, default="")
    limit = serializers.IntegerField(required=False, min_value=1, max_value=MAX_LIMIT, default=DEFAULT_LIMIT)

    def validate_source(self, value: str) -> str:
        sources = get_allowed_sources()
        if value not in sources:
            raise serializers.ValidationError(f"Unknown log source '{value}'.")
        return value

    def validate_level(self, value: str) -> str:
        normalized = value.strip().upper()
        if normalized and normalized not in ALLOWED_LEVELS:
            raise serializers.ValidationError(
                f"Invalid level '{value}'. Allowed levels: {', '.join(ALLOWED_LEVELS)}."
            )
        return normalized

    def validate(self, attrs: dict) -> dict:
        now = timezone.now()
        end = attrs.get("end") or now
        minutes = attrs.get("minutes", DEFAULT_LOG_WINDOW_HOURS * 60)
        start = attrs.get("start") or (end - timedelta(minutes=minutes))
        if start >= end:
            raise serializers.ValidationError({"start": "start must be earlier than end."})
        if (end - start) > timedelta(days=MAX_LOG_WINDOW_DAYS):
            raise serializers.ValidationError(
                {"start": f"Time window cannot exceed {MAX_LOG_WINDOW_DAYS} days."}
            )
        attrs["start"] = start
        attrs["end"] = end
        return attrs


class HoursQuerySerializer(serializers.Serializer):
    hours = serializers.IntegerField(required=False, min_value=1, max_value=168, default=24)
