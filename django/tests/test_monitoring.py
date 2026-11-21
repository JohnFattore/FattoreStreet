from datetime import datetime, timedelta, timezone as dt_timezone
import json
from unittest.mock import patch

from botocore.stub import ANY, Stubber
from django.contrib.auth.models import User
from django.urls import reverse
from django.utils import timezone
from rest_framework import status

from monitoring import services
from tests.base import BaseAPITestCase


class MonitoringAPITests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.staff_user = User.objects.create_user(
            username="staffuser",
            password="testpassword",  # pragma: allowlist secret
            is_staff=True,
        )
        self.logs_stubber = Stubber(services.logs_client())
        self.cw_stubber = Stubber(services.cloudwatch_client())
        self.logs_stubber.activate()
        self.cw_stubber.activate()
        self.addCleanup(self.logs_stubber.deactivate)
        self.addCleanup(self.cw_stubber.deactivate)

    def authenticate_staff(self):
        self.client.force_authenticate(user=self.staff_user)

    def test_permissions_enforced_across_all_monitoring_endpoints(self):
        endpoints = [
            (reverse("monitoring_sources"), {}),
            (reverse("monitoring_logs"), {"source": "django"}),
            (reverse("monitoring_summary"), {}),
            (reverse("monitoring_traffic"), {}),
            (reverse("monitoring_jobs"), {}),
            (reverse("monitoring_metrics"), {}),
        ]
        # Anonymous -> 401
        for url, params in endpoints:
            res = self.client.get(url, params)
            self.assertEqual(res.status_code, status.HTTP_401_UNAUTHORIZED, url)

        # Authenticated non-staff -> 403
        self.authenticate_client()
        for url, params in endpoints:
            res = self.client.get(url, params)
            self.assertEqual(res.status_code, status.HTTP_403_FORBIDDEN, url)

    def test_sources_returns_allowlisted_sources(self):
        self.authenticate_staff()
        res = self.client.get(reverse("monitoring_sources"))
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        keys = [s["key"] for s in res.data["sources"]]
        self.assertIn("nginx", keys)
        self.assertIn("django", keys)
        self.assertIn("springboot", keys)
        self.assertIn("hist-load", keys)
        self.assertIn("job-runs", keys)

    def test_logs_rejects_unknown_source_and_invalid_level(self):
        self.authenticate_staff()
        res = self.client.get(reverse("monitoring_logs"), {"source": "/ arbitrary/group"})
        self.assertEqual(res.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("source", res.data)

        res = self.client.get(reverse("monitoring_logs"), {"source": "django", "level": "TRACE"})
        self.assertEqual(res.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("level", res.data)

    def test_logs_fetches_and_filters_events_via_stubber(self):
        self.authenticate_staff()
        self.logs_stubber.add_response(
            "filter_log_events",
            {
                "events": [
                    {
                        "eventId": "evt-1",
                        "timestamp": 1763720000000,
                        "logStreamName": "django-123",
                        "message": "2026-11-21 10:13:20 INFO django.request GET /portfolio/\n",
                    },
                    {
                        "eventId": "evt-2",
                        "timestamp": 1763720060000,
                        "logStreamName": "django-123",
                        "message": "2026-11-21 10:14:20 ERROR chatbot.views Bedrock call failed\n",
                    },
                ],
                "nextToken": "token-page-2",
            },
            {
                "logGroupName": "/fattorestreet/django",
                "startTime": ANY,
                "endTime": ANY,
                "limit": 50,
                "filterPattern": '"Bedrock" "ERROR"',
            },
        )

        res = self.client.get(
            reverse("monitoring_logs"),
            {"source": "django", "q": "Bedrock", "level": "ERROR", "limit": 50},
        )
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data["source"], "django")
        self.assertEqual(res.data["next_token"], "token-page-2")
        self.assertEqual(len(res.data["events"]), 1)
        self.assertEqual(res.data["events"][0]["event_id"], "evt-2")
        self.assertEqual(res.data["events"][0]["level"], "ERROR")
        self.logs_stubber.assert_no_pending_responses()

    def test_logs_returns_empty_list_when_log_group_does_not_exist_yet(self):
        self.authenticate_staff()
        self.logs_stubber.add_client_error(
            "filter_log_events",
            service_error_code="ResourceNotFoundException",
            service_message="The specified log group does not exist.",
        )
        res = self.client.get(reverse("monitoring_logs"), {"source": "springboot"})
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data["events"], [])
        self.assertIsNone(res.data["next_token"])

    def test_summary_aggregates_error_and_warn_counts_per_service(self):
        self.authenticate_staff()
        self.logs_stubber.add_response(
            "start_query",
            {"queryId": "q-summary-1"},
            {
                "logGroupNames": ANY,
                "startTime": ANY,
                "endTime": ANY,
                "queryString": ANY,
            },
        )
        self.logs_stubber.add_response(
            "get_query_results",
            {
                "status": "Complete",
                "results": [
                    [
                        {"field": "@log", "value": "123456789012:/fattorestreet/django"},
                        {"field": "bucket", "value": "2026-11-21 12:00:00.000"},
                        {"field": "error_count", "value": "3"},
                        {"field": "warn_count", "value": "5"},
                    ],
                    [
                        {"field": "@log", "value": "123456789012:/ecs/fattorestreet-hist-load"},
                        {"field": "bucket", "value": "2026-11-21 12:00:00.000"},
                        {"field": "error_count", "value": "1"},
                        {"field": "warn_count", "value": "0"},
                    ],
                ],
            },
            {"queryId": "q-summary-1"},
        )

        res = self.client.get(reverse("monitoring_summary"), {"hours": 24})
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        by_source = {item["source"]: item for item in res.data["totals"]}
        self.assertEqual(by_source["django"]["error_count"], 3)
        self.assertEqual(by_source["django"]["warn_count"], 5)
        self.assertEqual(by_source["hist-load"]["error_count"], 1)
        self.assertEqual(by_source["springboot"]["error_count"], 0)
        self.assertEqual(len(res.data["buckets"]), 1)
        self.assertEqual(res.data["buckets"][0]["error_count"], 4)
        self.assertEqual(res.data["buckets"][0]["warn_count"], 5)
        self.logs_stubber.assert_no_pending_responses()

    def test_insights_query_timeout_stops_query_and_returns_504(self):
        self.authenticate_staff()
        self.logs_stubber.add_response(
            "start_query",
            {"queryId": "q-slow"},
            {
                "logGroupNames": ANY,
                "startTime": ANY,
                "endTime": ANY,
                "queryString": ANY,
            },
        )
        self.logs_stubber.add_response(
            "get_query_results",
            {"status": "Running", "results": []},
            {"queryId": "q-slow"},
        )
        self.logs_stubber.add_response(
            "stop_query",
            {"success": True},
            {"queryId": "q-slow"},
        )

        with patch.object(services, "INSIGHTS_TIMEOUT_SECONDS", 0.0):
            res = self.client.get(reverse("monitoring_summary"), {"hours": 6})

        self.assertEqual(res.status_code, status.HTTP_504_GATEWAY_TIMEOUT)
        self.logs_stubber.assert_no_pending_responses()

    def test_traffic_returns_hourly_counts_and_top_5xx_paths(self):
        self.authenticate_staff()
        self.logs_stubber.add_response(
            "start_query",
            {"queryId": "q-traffic-series"},
            {"logGroupNames": ["nginx-logs"], "startTime": ANY, "endTime": ANY, "queryString": ANY},
        )
        self.logs_stubber.add_response(
            "get_query_results",
            {
                "status": "Complete",
                "results": [
                    [
                        {"field": "bucket", "value": "2026-11-21 12:00:00.000"},
                        {"field": "total_requests", "value": "120"},
                        {"field": "status_4xx", "value": "8"},
                        {"field": "status_5xx", "value": "2"},
                    ]
                ],
            },
            {"queryId": "q-traffic-series"},
        )
        self.logs_stubber.add_response(
            "start_query",
            {"queryId": "q-traffic-5xx"},
            {"logGroupNames": ["nginx-logs"], "startTime": ANY, "endTime": ANY, "queryString": ANY},
        )
        self.logs_stubber.add_response(
            "get_query_results",
            {
                "status": "Complete",
                "results": [
                    [
                        {"field": "method", "value": "POST"},
                        {"field": "path", "value": "/django/chatbot/api/conversations/"},
                        {"field": "status", "value": "503"},
                        {"field": "error_count", "value": "2"},
                    ]
                ],
            },
            {"queryId": "q-traffic-5xx"},
        )

        res = self.client.get(reverse("monitoring_traffic"), {"hours": 24})
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data["total_requests"], 120)
        self.assertEqual(res.data["total_4xx"], 8)
        self.assertEqual(res.data["total_5xx"], 2)
        self.assertEqual(len(res.data["top_5xx_paths"]), 1)
        self.assertEqual(res.data["top_5xx_paths"][0]["path"], "/django/chatbot/api/conversations/")
        self.logs_stubber.assert_no_pending_responses()

    def test_jobs_computes_last_run_status_and_staleness(self):
        self.authenticate_staff()
        now = timezone.now()
        fresh_stop = (now - timedelta(hours=4)).isoformat()
        fresh_start = (now - timedelta(hours=4, minutes=12)).isoformat()
        stale_stop = (now - timedelta(hours=50)).isoformat()
        stale_start = (now - timedelta(hours=50, minutes=5)).isoformat()

        self.logs_stubber.add_response(
            "filter_log_events",
            {
                "events": [
                    {
                        "eventId": "run-1",
                        "timestamp": int((now - timedelta(hours=4)).timestamp() * 1000),
                        "message": json.dumps(
                            {
                                "detail": {
                                    "group": "family:fattorestreet-hist-load",
                                    "taskArn": "arn:aws:ecs:us-east-1:123456789012:task/hist/1",
                                    "startedAt": fresh_start,
                                    "stoppedAt": fresh_stop,
                                    "stopCode": "EssentialContainerExited",
                                    "stoppedReason": "Essential container in task exited",
                                    "containers": [{"exitCode": 0}],
                                }
                            }
                        ),
                    },
                    {
                        "eventId": "run-2",
                        "timestamp": int((now - timedelta(hours=50)).timestamp() * 1000),
                        "message": json.dumps(
                            {
                                "detail": {
                                    "group": "family:fattorestreet-index-load",
                                    "taskArn": "arn:aws:ecs:us-east-1:123456789012:task/index/1",
                                    "startedAt": stale_start,
                                    "stoppedAt": stale_stop,
                                    "stopCode": "EssentialContainerExited",
                                    "stoppedReason": "Container exited with non-zero status",
                                    "containers": [{"exitCode": 1}],
                                }
                            }
                        ),
                    },
                ]
            },
            {
                "logGroupName": "/fattorestreet/job-runs",
                "startTime": ANY,
                "endTime": ANY,
                "limit": 200,
            },
        )
        # Fallback DescribeLogStreams for the 3 jobs without EventBridge events
        for group_name in (
            "/ecs/fattorestreet-fundamentals-load",
            "/ecs/fattorestreet-validate-prices",
            "/ecs/fattorestreet-asset-load",
        ):
            self.logs_stubber.add_response(
                "describe_log_streams",
                {"logStreams": []},
                {
                    "logGroupName": group_name,
                    "orderBy": "LastEventTime",
                    "descending": True,
                    "limit": 1,
                },
            )

        res = self.client.get(reverse("monitoring_jobs"))
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        by_key = {j["key"]: j for j in res.data["jobs"]}

        self.assertEqual(by_key["hist-load"]["status"], "SUCCESS")
        self.assertFalse(by_key["hist-load"]["is_stale"])
        self.assertEqual(by_key["hist-load"]["duration_seconds"], 720)

        self.assertEqual(by_key["index-load"]["status"], "FAILED")
        self.assertTrue(by_key["index-load"]["is_stale"])
        self.assertEqual(by_key["index-load"]["exit_code"], 1)

        self.assertEqual(by_key["asset-load"]["status"], "UNKNOWN")
        self.assertTrue(by_key["asset-load"]["is_stale"])
        self.logs_stubber.assert_no_pending_responses()

    def test_metrics_returns_unconfigured_when_instance_id_unset(self):
        self.authenticate_staff()
        with patch.dict("os.environ", {"MONITORING_INSTANCE_ID": ""}):
            res = self.client.get(reverse("monitoring_metrics"), {"hours": 24})
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertFalse(res.data["configured"])
        self.assertEqual(res.data["series"], [])

    def test_metrics_fetches_ec2_and_cwagent_series_when_instance_id_set(self):
        self.authenticate_staff()
        ts = datetime(2026, 11, 21, 12, 0, tzinfo=dt_timezone.utc)
        self.cw_stubber.add_response(
            "get_metric_data",
            {
                "MetricDataResults": [
                    {"Id": "cpu", "Timestamps": [ts], "Values": [14.236], "StatusCode": "Complete"},
                    {"Id": "mem", "Timestamps": [ts], "Values": [62.5], "StatusCode": "Complete"},
                    {"Id": "disk_root", "Timestamps": [ts], "Values": [41.1], "StatusCode": "Complete"},
                    {"Id": "disk_ebs", "Timestamps": [ts], "Values": [55.8], "StatusCode": "Complete"},
                ]
            },
            {
                "MetricDataQueries": ANY,
                "StartTime": ANY,
                "EndTime": ANY,
                "ScanBy": "TimestampAscending",
            },
        )

        with patch.dict("os.environ", {"MONITORING_INSTANCE_ID": "i-0123456789abcdef0"}):
            res = self.client.get(reverse("monitoring_metrics"), {"hours": 24})

        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertTrue(res.data["configured"])
        self.assertEqual(res.data["instance_id"], "i-0123456789abcdef0")
        self.assertEqual(res.data["latest"]["cpu_percent"], 14.24)
        self.assertEqual(res.data["latest"]["mem_used_percent"], 62.5)
        self.assertEqual(res.data["latest"]["disk_root_percent"], 41.1)
        self.assertEqual(res.data["latest"]["disk_ebs_percent"], 55.8)
        self.assertEqual(len(res.data["series"]), 1)
        self.cw_stubber.assert_no_pending_responses()
