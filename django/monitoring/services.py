from datetime import datetime, timedelta, timezone as dt_timezone
from functools import cache
import json
import logging
import re
import time

import boto3
from botocore.client import BaseClient
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError
from django.contrib.auth.models import User
from django.core.cache import cache as django_cache
from django.utils import timezone
import environ
from rest_framework import status
from rest_framework.exceptions import APIException

logger = logging.getLogger(__name__)

env = environ.Env()

CACHE_TTL_SECONDS = 60
INSIGHTS_TIMEOUT_SECONDS = 10.0
INSIGHTS_POLL_INTERVAL_SECONDS = 0.35
MAX_FILTER_PAGES = 3

ALLOWED_LEVELS = ("ERROR", "WARN", "INFO", "DEBUG")

DEFAULT_LOG_SOURCES: dict[str, dict[str, str]] = {
    "nginx": {
        "key": "nginx",
        "label": "Nginx Reverse Proxy",
        "log_group": "nginx-logs",
        "category": "service",
    },
    "django": {
        "key": "django",
        "label": "Django API",
        "log_group": "/fattorestreet/django",
        "category": "service",
    },
    "springboot": {
        "key": "springboot",
        "label": "Spring Boot SEC Service",
        "log_group": "/fattorestreet/springboot",
        "category": "service",
    },
    "hist-load": {
        "key": "hist-load",
        "label": "IEX HIST Daily Load",
        "log_group": "/ecs/fattorestreet-hist-load",
        "category": "job",
    },
    "index-load": {
        "key": "index-load",
        "label": "Index Metrics & Rebuild",
        "log_group": "/ecs/fattorestreet-index-load",
        "category": "job",
    },
    "fundamentals-load": {
        "key": "fundamentals-load",
        "label": "EDGAR Fundamentals Sync",
        "log_group": "/ecs/fattorestreet-fundamentals-load",
        "category": "job",
    },
    "validate-prices": {
        "key": "validate-prices",
        "label": "Weekly Price Validation",
        "log_group": "/ecs/fattorestreet-validate-prices",
        "category": "job",
    },
    "asset-load": {
        "key": "asset-load",
        "label": "Monthly Asset Universe Load",
        "log_group": "/ecs/fattorestreet-asset-load",
        "category": "job",
    },
    "job-runs": {
        "key": "job-runs",
        "label": "Scheduled Job Run Events",
        "log_group": "/fattorestreet/job-runs",
        "category": "system",
    },
}

SCHEDULED_JOBS: list[dict[str, object]] = [
    {
        "key": "hist-load",
        "label": "IEX HIST Daily Load",
        "family": "fattorestreet-hist-load",
        "schedule": "Daily 02:00 ET",
        "expected_interval_hours": 24,
        "stale_after_hours": 36,
    },
    {
        "key": "index-load",
        "label": "Index Metrics & Rebuild",
        "family": "fattorestreet-index-load",
        "schedule": "Daily 09:30 ET",
        "expected_interval_hours": 24,
        "stale_after_hours": 36,
    },
    {
        "key": "fundamentals-load",
        "label": "EDGAR Fundamentals Sync",
        "family": "fattorestreet-fundamentals-load",
        "schedule": "Daily 13:30 ET",
        "expected_interval_hours": 24,
        "stale_after_hours": 36,
    },
    {
        "key": "validate-prices",
        "label": "Weekly Price Validation",
        "family": "fattorestreet-validate-prices",
        "schedule": "Weekly Sun 20:00 ET",
        "expected_interval_hours": 168,
        "stale_after_hours": 192,
    },
    {
        "key": "asset-load",
        "label": "Monthly Asset Universe Load",
        "family": "fattorestreet-asset-load",
        "schedule": "Monthly 1st 22:00 ET",
        "expected_interval_hours": 744,
        "stale_after_hours": 840,
    },
]

_ERROR_PATTERN = re.compile(
    r"\b(?:ERROR|CRITICAL|FATAL|Exception|Traceback)\b|\[error\]|\[crit\]|"
    r'"[A-Z]+ [^"]+ HTTP/[0-9.]+" 5[0-9]{2}\b'
)
_WARN_PATTERN = re.compile(
    r"\b(?:WARN|WARNING)\b|\[warn\]|"
    r'"[A-Z]+ [^"]+ HTTP/[0-9.]+" 4[0-9]{2}\b'
)
_DEBUG_PATTERN = re.compile(r"\bDEBUG\b|\[debug\]")


class MonitoringUnavailable(APIException):
    status_code = status.HTTP_503_SERVICE_UNAVAILABLE
    default_detail = "CloudWatch monitoring is unavailable right now. Please try again in a moment."
    default_code = "monitoring_unavailable"


class MonitoringQueryTimeout(APIException):
    status_code = status.HTTP_504_GATEWAY_TIMEOUT
    default_detail = "The CloudWatch Logs Insights query timed out. Try a narrower time window."
    default_code = "monitoring_query_timeout"


@cache
def logs_client() -> BaseClient:
    return boto3.client(
        "logs",
        region_name=env("AWS_REGION", default="us-east-1"),
        config=Config(
            read_timeout=10,
            connect_timeout=5,
            retries={"total_max_attempts": 2, "mode": "adaptive"},
        ),
    )


@cache
def cloudwatch_client() -> BaseClient:
    return boto3.client(
        "cloudwatch",
        region_name=env("AWS_REGION", default="us-east-1"),
        config=Config(
            read_timeout=10,
            connect_timeout=5,
            retries={"total_max_attempts": 2, "mode": "adaptive"},
        ),
    )


def get_allowed_sources() -> dict[str, dict[str, str]]:
    """Return the allowlisted log sources, with optional log-group name overrides from env."""
    sources = {key: dict(meta) for key, meta in DEFAULT_LOG_SOURCES.items()}
    raw_override = env("MONITORING_LOG_GROUPS", default="").strip()
    if raw_override:
        try:
            overrides = json.loads(raw_override)
            if isinstance(overrides, dict):
                for key, group_name in overrides.items():
                    if key in sources and isinstance(group_name, str) and group_name.strip():
                        sources[key]["log_group"] = group_name.strip()
        except ValueError:
            logger.warning("Ignoring invalid JSON in MONITORING_LOG_GROUPS")
    return sources


def _is_not_found(exc: ClientError) -> bool:
    code = exc.response.get("Error", {}).get("Code", "")
    return code == "ResourceNotFoundException"


def detect_log_level(message: str) -> str:
    if _ERROR_PATTERN.search(message):
        return "ERROR"
    if _WARN_PATTERN.search(message):
        return "WARN"
    if _DEBUG_PATTERN.search(message):
        return "DEBUG"
    return "INFO"


def _quote_filter_term(term: str) -> str:
    escaped = term.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def build_filter_pattern(query: str, level: str, source_key: str) -> str:
    cleaned_query = query.strip()
    # CloudWatch filterPattern cannot combine '?' OR terms with AND terms, and
    # Nginx access logs encode status codes rather than level keywords. When a
    # search query is present with ERROR/WARN (or on nginx), filter by the query
    # term in CloudWatch and let detect_log_level post-filter in Python.
    if not cleaned_query and source_key != "nginx":
        if level == "ERROR":
            return "?ERROR ?Exception ?Traceback ?CRITICAL ?FATAL"
        if level == "WARN":
            return "?WARN ?WARNING"
        if level in ("INFO", "DEBUG"):
            return _quote_filter_term(level)

    terms: list[str] = []
    if cleaned_query:
        terms.append(_quote_filter_term(cleaned_query))
    if level in ("INFO", "DEBUG") and source_key != "nginx":
        terms.append(_quote_filter_term(level))
    return " ".join(terms)


def fetch_log_events(
    source_key: str,
    start: datetime,
    end: datetime,
    query: str = "",
    level: str = "",
    next_token: str = "",
    limit: int = 200,
) -> dict:
    sources = get_allowed_sources()
    source_meta = sources[source_key]
    log_group = source_meta["log_group"]
    filter_pattern = build_filter_pattern(query, level, source_key)
    start_ms = int(start.timestamp() * 1000)
    end_ms = int(end.timestamp() * 1000)

    events: list[dict] = []
    current_token: str | None = next_token or None
    max_pages = MAX_FILTER_PAGES if level else 1

    for page_index in range(max_pages):
        params: dict[str, object] = {
            "logGroupName": log_group,
            "startTime": start_ms,
            "endTime": end_ms,
            "limit": limit,
        }
        if filter_pattern:
            params["filterPattern"] = filter_pattern
        if current_token:
            params["nextToken"] = current_token

        try:
            response = logs_client().filter_log_events(**params)
        except ClientError as exc:
            if _is_not_found(exc) and page_index == 0:
                return {
                    "source": source_key,
                    "log_group": log_group,
                    "events": [],
                    "next_token": None,
                }
            logger.exception("CloudWatch FilterLogEvents failed for %s", log_group)
            raise MonitoringUnavailable() from exc
        except BotoCoreError as exc:
            logger.exception("CloudWatch FilterLogEvents failed for %s", log_group)
            raise MonitoringUnavailable() from exc

        for item in response.get("events", []):
            message = (item.get("message") or "").rstrip("\r\n")
            detected_level = detect_log_level(message)
            if level and detected_level != level:
                continue
            ts_ms = int(item.get("timestamp", 0))
            ts_iso = datetime.fromtimestamp(ts_ms / 1000.0, tz=dt_timezone.utc).isoformat()
            events.append(
                {
                    "event_id": item.get("eventId", ""),
                    "timestamp": ts_iso,
                    "timestamp_ms": ts_ms,
                    "log_stream": item.get("logStreamName", ""),
                    "level": detected_level,
                    "message": message,
                }
            )

        current_token = response.get("nextToken")
        if not current_token or len(events) >= limit:
            break

    events.sort(key=lambda row: row["timestamp_ms"], reverse=True)
    return {
        "source": source_key,
        "log_group": log_group,
        "events": events,
        "next_token": current_token,
    }


def run_insights_query(
    log_group_names: list[str],
    query_string: str,
    start: datetime,
    end: datetime,
    timeout_seconds: float | None = None,
    poll_interval_seconds: float | None = None,
) -> list[dict[str, str]]:
    timeout = INSIGHTS_TIMEOUT_SECONDS if timeout_seconds is None else timeout_seconds
    poll_interval = INSIGHTS_POLL_INTERVAL_SECONDS if poll_interval_seconds is None else poll_interval_seconds
    client = logs_client()
    try:
        started = client.start_query(
            logGroupNames=log_group_names,
            startTime=int(start.timestamp()),
            endTime=int(end.timestamp()),
            queryString=query_string,
        )
    except ClientError as exc:
        if _is_not_found(exc):
            return []
        logger.exception("CloudWatch Logs Insights StartQuery failed")
        raise MonitoringUnavailable() from exc
    except BotoCoreError as exc:
        logger.exception("CloudWatch Logs Insights StartQuery failed")
        raise MonitoringUnavailable() from exc

    query_id = started["queryId"]
    deadline = time.monotonic() + timeout

    while True:
        try:
            result = client.get_query_results(queryId=query_id)
        except (ClientError, BotoCoreError) as exc:
            logger.exception("CloudWatch Logs Insights GetQueryResults failed for %s", query_id)
            raise MonitoringUnavailable() from exc

        query_status = result.get("status", "Unknown")
        if query_status == "Complete":
            rows: list[dict[str, str]] = []
            for raw_row in result.get("results", []):
                parsed = {
                    cell["field"]: cell["value"]
                    for cell in raw_row
                    if "field" in cell and "value" in cell and cell["field"] != "@ptr"
                }
                rows.append(parsed)
            return rows

        if query_status in ("Failed", "Cancelled", "Timeout"):
            logger.error("CloudWatch Logs Insights query %s ended with status %s", query_id, query_status)
            raise MonitoringUnavailable()

        if time.monotonic() >= deadline:
            try:
                client.stop_query(queryId=query_id)
            except (ClientError, BotoCoreError):
                logger.warning("Could not stop timed-out CloudWatch Logs Insights query %s", query_id)
            raise MonitoringQueryTimeout()

        time.sleep(poll_interval)


def _match_source_by_log_field(log_field: str, sources: dict[str, dict[str, str]]) -> str | None:
    # CloudWatch Logs Insights populates @log as "<account_id>:<log_group_name>".
    group_name = log_field.split(":", 1)[1] if ":" in log_field else log_field
    for key, meta in sources.items():
        if meta["log_group"] == group_name:
            return key
    return None


def fetch_error_summary(hours: int = 24) -> dict:
    cache_key = f"monitoring:summary:{hours}"
    cached = django_cache.get(cache_key)
    if cached is not None:
        return cached

    sources = get_allowed_sources()
    monitored_sources = [meta for meta in sources.values() if meta["category"] in ("service", "job")]
    log_group_names = [meta["log_group"] for meta in monitored_sources]

    end = timezone.now()
    start = end - timedelta(hours=hours)

    query_string = (
        "fields @log, @timestamp, @message "
        "| parse @message /(?<err_tok>(?i)\\b(?:ERROR|Exception|Traceback|CRITICAL|FATAL)\\b"
        '|\\[error\\]|\\[crit\\]|"[A-Z]+ [^"]+ HTTP\\/[0-9.]+" 5[0-9]{2}\\b)/ '
        "| parse @message /(?<warn_tok>(?i)\\b(?:WARN|WARNING)\\b"
        '|\\[warn\\]|"[A-Z]+ [^"]+ HTTP\\/[0-9.]+" 4[0-9]{2}\\b)/ '
        "| filter ispresent(err_tok) or ispresent(warn_tok) "
        "| stats count(err_tok) as error_count, count(warn_tok) as warn_count by @log, bin(1h) as bucket "
        "| sort bucket asc"
    )

    rows = run_insights_query(log_group_names, query_string, start, end)

    totals_by_key: dict[str, dict] = {
        meta["key"]: {
            "source": meta["key"],
            "label": meta["label"],
            "category": meta["category"],
            "error_count": 0,
            "warn_count": 0,
        }
        for meta in monitored_sources
    }
    buckets_by_time: dict[str, dict[str, object]] = {}

    for row in rows:
        source_key = _match_source_by_log_field(row.get("@log", ""), sources)
        err = int(float(row.get("error_count", "0") or 0))
        warn = int(float(row.get("warn_count", "0") or 0))
        if source_key and source_key in totals_by_key:
            totals_by_key[source_key]["error_count"] += err
            totals_by_key[source_key]["warn_count"] += warn
        bucket = row.get("bucket", "")
        if bucket:
            entry = buckets_by_time.setdefault(
                bucket,
                {"bucket": bucket, "error_count": 0, "warn_count": 0},
            )
            entry["error_count"] = int(entry["error_count"]) + err
            entry["warn_count"] = int(entry["warn_count"]) + warn

    payload = {
        "hours": hours,
        "totals": list(totals_by_key.values()),
        "buckets": [buckets_by_time[k] for k in sorted(buckets_by_time.keys())],
    }
    django_cache.set(cache_key, payload, CACHE_TTL_SECONDS)
    return payload


# Client errors on the platform's own API (nginx's /django/ and /springboot/ prefixes)
# are the ones worth reading; everything else is mostly bots probing paths the app
# never served. Grouping by status, rather than summing boolean expressions, keeps
# these queries to Logs Insights syntax already proven against the live service.
_NGINX_REQUEST_PARSE = (
    '| parse @message /"(?<method>[A-Z]+) (?<path>[^ "?]+)(?:\\?[^ "]*)? HTTP\\/[0-9.]+" (?<status>[0-9]{3}) / '
)
_API_4XX_FILTER = "| filter status >= 400 and status < 500 | filter path like /^\\/(django|springboot)\\// "
API_4XX_BY_STATUS_QUERY = (
    "fields @message " + _NGINX_REQUEST_PARSE + _API_4XX_FILTER + "| stats count(*) as request_count by status"
)
TOP_API_4XX_PATHS_QUERY = (
    "fields @message "
    + _NGINX_REQUEST_PARSE
    + _API_4XX_FILTER
    + "| stats count(*) as error_count by method, path, status "
    "| sort error_count desc "
    "| limit 10"
)
API_4XX_CATEGORIES = {"401": "unauthorized", "403": "forbidden", "404": "not_found"}


def fetch_nginx_traffic(hours: int = 24) -> dict:
    cache_key = f"monitoring:traffic:{hours}"
    cached = django_cache.get(cache_key)
    if cached is not None:
        return cached

    sources = get_allowed_sources()
    nginx_group = sources["nginx"]["log_group"]
    end = timezone.now()
    start = end - timedelta(hours=hours)

    series_query = (
        "fields @timestamp, @message "
        '| parse @message /"(?<method>[A-Z]+) (?<path>[^ "]+) HTTP\\/[0-9.]+" (?<status>[0-9]{3}) / '
        "| filter ispresent(status) "
        "| stats count(*) as total_requests, "
        "sum(status >= 400 and status < 500) as status_4xx, "
        "sum(status >= 500) as status_5xx "
        "by bin(1h) as bucket "
        "| sort bucket asc"
    )
    top_5xx_query = (
        "fields @message "
        '| parse @message /"(?<method>[A-Z]+) (?<path>[^ "?]+)(?:\\?[^ "]*)? HTTP\\/[0-9.]+" (?<status>[0-9]{3}) / '
        "| filter status >= 500 "
        "| stats count(*) as error_count by method, path, status "
        "| sort error_count desc "
        "| limit 10"
    )

    series_rows = run_insights_query([nginx_group], series_query, start, end)
    top_5xx_rows = run_insights_query([nginx_group], top_5xx_query, start, end)
    api_4xx_status_rows = run_insights_query([nginx_group], API_4XX_BY_STATUS_QUERY, start, end)
    top_api_4xx_rows = run_insights_query([nginx_group], TOP_API_4XX_PATHS_QUERY, start, end)

    buckets = []
    total_requests = 0
    total_4xx = 0
    total_5xx = 0
    for row in series_rows:
        reqs = int(float(row.get("total_requests", "0") or 0))
        c4xx = int(float(row.get("status_4xx", "0") or 0))
        c5xx = int(float(row.get("status_5xx", "0") or 0))
        total_requests += reqs
        total_4xx += c4xx
        total_5xx += c5xx
        buckets.append(
            {
                "bucket": row.get("bucket", ""),
                "total_requests": reqs,
                "status_4xx": c4xx,
                "status_5xx": c5xx,
            }
        )

    top_5xx_paths = [
        {
            "method": row.get("method", "GET"),
            "path": row.get("path", "/"),
            "status": int(float(row.get("status", "500") or 500)),
            "error_count": int(float(row.get("error_count", "0") or 0)),
        }
        for row in top_5xx_rows
    ]

    api_4xx = {"total": 0, "unauthorized": 0, "forbidden": 0, "not_found": 0, "other": 0}
    for row in api_4xx_status_rows:
        count = int(float(row.get("request_count", "0") or 0))
        category = API_4XX_CATEGORIES.get(str(row.get("status", "")), "other")
        api_4xx[category] += count
        api_4xx["total"] += count

    top_api_4xx_paths = [
        {
            "method": row.get("method", "GET"),
            "path": row.get("path", "/"),
            "status": int(float(row.get("status", "400") or 400)),
            "error_count": int(float(row.get("error_count", "0") or 0)),
        }
        for row in top_api_4xx_rows
    ]

    payload = {
        "hours": hours,
        "total_requests": total_requests,
        "total_4xx": total_4xx,
        "total_5xx": total_5xx,
        "api_4xx": api_4xx,
        # The two figures come from separate queries over a moving window, so clamp
        # rather than report a negative count when they disagree by a request or two.
        "non_api_4xx": max(total_4xx - api_4xx["total"], 0),
        "buckets": buckets,
        "top_5xx_paths": top_5xx_paths,
        "top_api_4xx_paths": top_api_4xx_paths,
    }
    django_cache.set(cache_key, payload, CACHE_TTL_SECONDS)
    return payload


def _parse_iso_datetime(value: str | None) -> datetime | None:
    if not value or not isinstance(value, str):
        return None
    normalized = value.replace("Z", "+00:00")
    try:
        dt = datetime.fromisoformat(normalized)
    except ValueError:
        return None
    if dt.tzinfo is None:
        return dt.replace(tzinfo=dt_timezone.utc)
    return dt


def _parse_insights_timestamp(value: str | None) -> datetime | None:
    """Parse CloudWatch Logs Insights timestamp values (epoch ms/s strings or ISO datetimes)."""
    if not value or not isinstance(value, str):
        return None
    stripped = value.strip()
    if not stripped:
        return None
    try:
        numeric = float(stripped)
        seconds = numeric / 1000.0 if numeric >= 1e11 else numeric
        return datetime.fromtimestamp(seconds, tz=dt_timezone.utc)
    except (ValueError, OSError, OverflowError):
        pass
    return _parse_iso_datetime(stripped.replace(" ", "T", 1))


USER_ACTIVITY_QUERY = (
    "fields @timestamp, @message "
    "| filter @message like /api_request / "
    "| parse @message /api_request user=(?<user>\\S+) user_id=(?<user_id>\\S+) "
    "method=(?<method>[A-Z]+) app=(?<app>\\S+) path=(?<path>\\S+) "
    "status=(?<status>[0-9]{3}) duration_ms=(?<duration_ms>[0-9]+)/ "
    "| parse @message /status=(?<err_4xx>4[0-9]{2})\\b/ "
    "| parse @message /status=(?<err_5xx>5[0-9]{2})\\b/ "
    "| filter ispresent(user) "
    "| stats count(*) as request_count, count(err_4xx) as status_4xx, "
    "count(err_5xx) as status_5xx, max(@timestamp) as last_seen by user, app "
    "| sort request_count desc "
    "| limit 500"
)


def fetch_user_activity(hours: int = 24) -> dict:
    cache_key = f"monitoring:user_activity:{hours}"
    cached = django_cache.get(cache_key)
    if cached is not None:
        return cached

    sources = get_allowed_sources()
    django_group = sources["django"]["log_group"]
    end = timezone.now()
    start = end - timedelta(hours=hours)

    rows = run_insights_query([django_group], USER_ACTIVITY_QUERY, start, end)

    per_user: dict[str, dict] = {}
    anonymous_requests = 0
    authenticated_requests = 0

    for row in rows:
        username = (row.get("user") or "").strip()
        if not username:
            continue
        req_count = int(float(row.get("request_count", "0") or 0))
        if username == "anonymous":
            anonymous_requests += req_count
            continue

        c4xx = int(float(row.get("status_4xx", "0") or 0))
        c5xx = int(float(row.get("status_5xx", "0") or 0))
        app = (row.get("app") or "other").strip() or "other"
        seen_dt = _parse_insights_timestamp(row.get("last_seen"))

        authenticated_requests += req_count
        entry = per_user.setdefault(
            username,
            {
                "request_count": 0,
                "status_4xx": 0,
                "status_5xx": 0,
                "last_seen": None,
                "apps": {},
            },
        )
        entry["request_count"] += req_count
        entry["status_4xx"] += c4xx
        entry["status_5xx"] += c5xx
        entry["apps"][app] = entry["apps"].get(app, 0) + req_count
        if seen_dt is not None and (entry["last_seen"] is None or seen_dt > entry["last_seen"]):
            entry["last_seen"] = seen_dt

    staff_by_username: dict[str, bool] = {}
    if per_user:
        staff_by_username = dict(
            User.objects.filter(username__in=list(per_user.keys())).values_list("username", "is_staff")
        )

    users_out = []
    for username, entry in sorted(
        per_user.items(),
        key=lambda item: (-item[1]["request_count"], item[0]),
    ):
        apps_sorted = [
            {"app": app_name, "request_count": count}
            for app_name, count in sorted(
                entry["apps"].items(),
                key=lambda item: (-item[1], item[0]),
            )
        ]
        last_seen_dt: datetime | None = entry["last_seen"]
        users_out.append(
            {
                "username": username,
                "is_staff": bool(staff_by_username.get(username, False)),
                "request_count": entry["request_count"],
                "status_4xx": entry["status_4xx"],
                "status_5xx": entry["status_5xx"],
                "last_seen": last_seen_dt.isoformat() if last_seen_dt else None,
                "last_seen_ms": int(last_seen_dt.timestamp() * 1000) if last_seen_dt else 0,
                "top_app": apps_sorted[0]["app"] if apps_sorted else "",
                "apps": apps_sorted,
            }
        )

    payload = {
        "hours": hours,
        "active_users": len(users_out),
        "total_requests": authenticated_requests + anonymous_requests,
        "authenticated_requests": authenticated_requests,
        "anonymous_requests": anonymous_requests,
        "users": users_out,
    }
    django_cache.set(cache_key, payload, CACHE_TTL_SECONDS)
    return payload


def _fallback_last_stream_time(log_group: str) -> datetime | None:
    try:
        resp = logs_client().describe_log_streams(
            logGroupName=log_group,
            orderBy="LastEventTime",
            descending=True,
            limit=1,
        )
    except ClientError as exc:
        if _is_not_found(exc):
            return None
        logger.exception("CloudWatch DescribeLogStreams failed for %s", log_group)
        raise MonitoringUnavailable() from exc
    except BotoCoreError as exc:
        logger.exception("CloudWatch DescribeLogStreams failed for %s", log_group)
        raise MonitoringUnavailable() from exc

    streams = resp.get("logStreams") or []
    if not streams:
        return None
    ts_ms = streams[0].get("lastEventTimestamp") or streams[0].get("creationTime")
    if not ts_ms:
        return None
    return datetime.fromtimestamp(int(ts_ms) / 1000.0, tz=dt_timezone.utc)


def fetch_job_health() -> dict:
    cache_key = "monitoring:jobs"
    cached = django_cache.get(cache_key)
    if cached is not None:
        return cached

    sources = get_allowed_sources()
    job_runs_group = sources["job-runs"]["log_group"]
    now = timezone.now()
    start_ms = int((now - timedelta(days=40)).timestamp() * 1000)
    end_ms = int(now.timestamp() * 1000)

    raw_events = []
    try:
        resp = logs_client().filter_log_events(
            logGroupName=job_runs_group,
            startTime=start_ms,
            endTime=end_ms,
            limit=200,
        )
        raw_events = resp.get("events", [])
    except ClientError as exc:
        if not _is_not_found(exc):
            logger.exception("CloudWatch FilterLogEvents failed for %s", job_runs_group)
            raise MonitoringUnavailable() from exc
    except BotoCoreError as exc:
        logger.exception("CloudWatch FilterLogEvents failed for %s", job_runs_group)
        raise MonitoringUnavailable() from exc

    runs_by_family: dict[str, list[dict]] = {}
    for event in raw_events:
        try:
            payload = json.loads(event.get("message", "{}"))
        except ValueError:
            continue
        detail = payload.get("detail") if isinstance(payload, dict) else None
        if not isinstance(detail, dict):
            continue
        group = str(detail.get("group", ""))
        family = group.split("family:", 1)[1] if group.startswith("family:") else group
        if not family:
            continue

        containers = detail.get("containers") or []
        first_container = containers[0] if containers and isinstance(containers[0], dict) else {}
        exit_code = first_container.get("exitCode")
        stop_code = detail.get("stopCode", "")
        stopped_reason = detail.get("stoppedReason", "")
        started_at = _parse_iso_datetime(detail.get("startedAt") or detail.get("createdAt"))
        stopped_at = _parse_iso_datetime(detail.get("stoppedAt")) or datetime.fromtimestamp(
            int(event.get("timestamp", 0)) / 1000.0, tz=dt_timezone.utc
        )
        duration_seconds = (
            max(0, int((stopped_at - started_at).total_seconds()))
            if started_at and stopped_at
            else None
        )
        succeeded = exit_code == 0 and stop_code != "TaskFailedToStart"
        runs_by_family.setdefault(family, []).append(
            {
                "stopped_at": stopped_at,
                "duration_seconds": duration_seconds,
                "exit_code": exit_code,
                "stop_code": stop_code,
                "stopped_reason": stopped_reason,
                "task_arn": detail.get("taskArn", ""),
                "succeeded": succeeded,
            }
        )

    jobs_out = []
    for job in SCHEDULED_JOBS:
        key = str(job["key"])
        family = str(job["family"])
        stale_after_hours = int(job["stale_after_hours"])
        family_runs = sorted(
            runs_by_family.get(family, []),
            key=lambda r: r["stopped_at"],
            reverse=True,
        )

        if family_runs:
            latest = family_runs[0]
            last_success = next((r for r in family_runs if r["succeeded"]), None)
            last_run_at = latest["stopped_at"]
            last_success_at = last_success["stopped_at"] if last_success else None
            is_stale = (
                last_success_at is None
                or (now - last_success_at) > timedelta(hours=stale_after_hours)
            )
            status_label = "SUCCESS" if latest["succeeded"] else "FAILED"
            jobs_out.append(
                {
                    "key": key,
                    "label": job["label"],
                    "schedule": job["schedule"],
                    "expected_interval_hours": job["expected_interval_hours"],
                    "stale_after_hours": stale_after_hours,
                    "last_run_at": last_run_at.isoformat(),
                    "last_success_at": last_success_at.isoformat() if last_success_at else None,
                    "duration_seconds": latest["duration_seconds"],
                    "exit_code": latest["exit_code"],
                    "status": status_label,
                    "is_stale": is_stale,
                    "stopped_reason": latest["stopped_reason"],
                    "task_arn": latest["task_arn"],
                }
            )
        else:
            log_group = sources[key]["log_group"]
            stream_last_time = _fallback_last_stream_time(log_group)
            is_stale = (
                stream_last_time is None
                or (now - stream_last_time) > timedelta(hours=stale_after_hours)
            )
            jobs_out.append(
                {
                    "key": key,
                    "label": job["label"],
                    "schedule": job["schedule"],
                    "expected_interval_hours": job["expected_interval_hours"],
                    "stale_after_hours": stale_after_hours,
                    "last_run_at": stream_last_time.isoformat() if stream_last_time else None,
                    "last_success_at": stream_last_time.isoformat() if stream_last_time else None,
                    "duration_seconds": None,
                    "exit_code": 0 if stream_last_time else None,
                    "status": "SUCCESS" if stream_last_time else "UNKNOWN",
                    "is_stale": is_stale,
                    "stopped_reason": "Inferred from latest task log stream" if stream_last_time else "",
                    "task_arn": "",
                }
            )

    payload = {"jobs": jobs_out}
    django_cache.set(cache_key, payload, CACHE_TTL_SECONDS)
    return payload


def fetch_host_metrics(hours: int = 24) -> dict:
    instance_id = env("MONITORING_INSTANCE_ID", default="").strip()
    if not instance_id:
        return {
            "hours": hours,
            "instance_id": "",
            "configured": False,
            "latest": {
                "cpu_percent": None,
                "mem_used_percent": None,
                "disk_root_percent": None,
                "disk_ebs_percent": None,
            },
            "series": [],
        }

    cache_key = f"monitoring:metrics:{instance_id}:{hours}"
    cached = django_cache.get(cache_key)
    if cached is not None:
        return cached

    end = timezone.now()
    start = end - timedelta(hours=hours)
    period = 300 if hours <= 24 else 3600

    queries = [
        {
            "Id": "cpu",
            "MetricStat": {
                "Metric": {
                    "Namespace": "AWS/EC2",
                    "MetricName": "CPUUtilization",
                    "Dimensions": [{"Name": "InstanceId", "Value": instance_id}],
                },
                "Period": period,
                "Stat": "Average",
            },
            "ReturnData": True,
        },
        {
            "Id": "mem",
            "MetricStat": {
                "Metric": {
                    "Namespace": "CWAgent",
                    "MetricName": "mem_used_percent",
                    "Dimensions": [{"Name": "InstanceId", "Value": instance_id}],
                },
                "Period": period,
                "Stat": "Average",
            },
            "ReturnData": True,
        },
        {
            "Id": "disk_root",
            "MetricStat": {
                "Metric": {
                    "Namespace": "CWAgent",
                    "MetricName": "disk_used_percent",
                    "Dimensions": [
                        {"Name": "InstanceId", "Value": instance_id},
                        {"Name": "path", "Value": "/"},
                    ],
                },
                "Period": period,
                "Stat": "Average",
            },
            "ReturnData": True,
        },
        {
            "Id": "disk_ebs",
            "MetricStat": {
                "Metric": {
                    "Namespace": "CWAgent",
                    "MetricName": "disk_used_percent",
                    "Dimensions": [
                        {"Name": "InstanceId", "Value": instance_id},
                        {"Name": "path", "Value": "/mnt/ebs"},
                    ],
                },
                "Period": period,
                "Stat": "Average",
            },
            "ReturnData": True,
        },
    ]

    try:
        response = cloudwatch_client().get_metric_data(
            MetricDataQueries=queries,
            StartTime=start,
            EndTime=end,
            ScanBy="TimestampAscending",
        )
    except (ClientError, BotoCoreError) as exc:
        logger.exception("CloudWatch GetMetricData failed for instance %s", instance_id)
        raise MonitoringUnavailable() from exc

    field_map = {
        "cpu": "cpu_percent",
        "mem": "mem_used_percent",
        "disk_root": "disk_root_percent",
        "disk_ebs": "disk_ebs_percent",
    }
    points_by_ts: dict[str, dict[str, object]] = {}
    latest: dict[str, float | None] = {
        "cpu_percent": None,
        "mem_used_percent": None,
        "disk_root_percent": None,
        "disk_ebs_percent": None,
    }

    for result in response.get("MetricDataResults", []):
        field_name = field_map.get(result.get("Id", ""))
        if not field_name:
            continue
        timestamps = result.get("Timestamps") or []
        values = result.get("Values") or []
        for ts, val in zip(timestamps, values):
            if isinstance(ts, datetime):
                ts_utc = ts.astimezone(dt_timezone.utc) if ts.tzinfo else ts.replace(tzinfo=dt_timezone.utc)
                ts_iso = ts_utc.isoformat()
            else:
                ts_iso = str(ts)
            rounded = round(float(val), 2)
            point = points_by_ts.setdefault(
                ts_iso,
                {
                    "timestamp": ts_iso,
                    "cpu_percent": None,
                    "mem_used_percent": None,
                    "disk_root_percent": None,
                    "disk_ebs_percent": None,
                },
            )
            point[field_name] = rounded
            latest[field_name] = rounded

    payload = {
        "hours": hours,
        "instance_id": instance_id,
        "configured": True,
        "latest": latest,
        "series": [points_by_ts[k] for k in sorted(points_by_ts.keys())],
    }
    django_cache.set(cache_key, payload, CACHE_TTL_SECONDS)
    return payload
