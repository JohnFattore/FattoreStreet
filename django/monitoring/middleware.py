import logging
import re
import time
from typing import Callable

from django.http import HttpRequest, HttpResponse

logger = logging.getLogger("monitoring.requests")

_UNSAFE_TOKEN_RE = re.compile(r"[\s\x00-\x1f\x7f]+")
_MAX_PATH_LENGTH = 200
_SKIPPED_PREFIXES = ("/monitoring/", "/static/")


def _sanitize_token(value: str, fallback: str = "-") -> str:
    cleaned = _UNSAFE_TOKEN_RE.sub("_", value.strip())
    return cleaned or fallback


def _extract_app(path_info: str) -> str:
    stripped = path_info.lstrip("/")
    if not stripped:
        return "root"
    segment = stripped.split("/", 1)[0]
    return _sanitize_token(segment, fallback="other")


def _resolve_user(request: HttpRequest):
    for attr in ("_monitoring_user", "_force_auth_user", "user"):
        candidate = getattr(request, attr, None)
        if candidate is not None and getattr(candidate, "is_authenticated", False):
            return candidate
    return None


class RequestActivityLoggingMiddleware:
    """Emit one structured INFO log line per handled Django request for per-user monitoring.

    Monitoring routes themselves are skipped so dashboard polling does not inflate
    staff activity. Only the normalized path (never query strings or request bodies)
    is logged.
    """

    def __init__(self, get_response: Callable[[HttpRequest], HttpResponse]) -> None:
        self.get_response = get_response

    def __call__(self, request: HttpRequest) -> HttpResponse:
        path_info = request.path_info or "/"
        if any(path_info.startswith(prefix) for prefix in _SKIPPED_PREFIXES):
            return self.get_response(request)

        started = time.monotonic()
        response = self.get_response(request)
        duration_ms = max(0, int((time.monotonic() - started) * 1000))

        user = _resolve_user(request)
        if user is not None:
            username = _sanitize_token(str(getattr(user, "username", "")), fallback="unknown")
            user_id = str(getattr(user, "pk", "") or "-")
        else:
            username = "anonymous"
            user_id = "-"

        method = _sanitize_token(request.method or "GET", fallback="GET").upper()
        app = _extract_app(path_info)
        safe_path = _sanitize_token(path_info[:_MAX_PATH_LENGTH], fallback="/")

        logger.info(
            "api_request user=%s user_id=%s method=%s app=%s path=%s status=%d duration_ms=%d",
            username,
            user_id,
            method,
            app,
            safe_path,
            response.status_code,
            duration_ms,
        )
        return response
