from rest_framework.permissions import IsAdminUser
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from .serializers import HoursQuerySerializer, LogsQuerySerializer
from .services import (
    fetch_error_summary,
    fetch_host_metrics,
    fetch_job_health,
    fetch_log_events,
    fetch_nginx_traffic,
    get_allowed_sources,
)


class MonitoringSourcesView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        sources = list(get_allowed_sources().values())
        return Response({"sources": sources})


class MonitoringLogsView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        serializer = LogsQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        params = serializer.validated_data
        result = fetch_log_events(
            source_key=params["source"],
            start=params["start"],
            end=params["end"],
            query=params["q"],
            level=params["level"],
            next_token=params["next_token"],
            limit=params["limit"],
        )
        return Response(result)


class MonitoringSummaryView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        serializer = HoursQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        hours = serializer.validated_data["hours"]
        return Response(fetch_error_summary(hours=hours))


class MonitoringTrafficView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        serializer = HoursQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        hours = serializer.validated_data["hours"]
        return Response(fetch_nginx_traffic(hours=hours))


class MonitoringJobsView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        return Response(fetch_job_health())


class MonitoringMetricsView(APIView):
    permission_classes = [IsAdminUser]

    def get(self, request: Request) -> Response:
        serializer = HoursQuerySerializer(data=request.query_params)
        serializer.is_valid(raise_exception=True)
        hours = serializer.validated_data["hours"]
        return Response(fetch_host_metrics(hours=hours))
