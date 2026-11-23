from django.urls import path

from . import views

urlpatterns = [
    path("api/sources/", views.MonitoringSourcesView.as_view(), name="monitoring_sources"),
    path("api/logs/", views.MonitoringLogsView.as_view(), name="monitoring_logs"),
    path("api/summary/", views.MonitoringSummaryView.as_view(), name="monitoring_summary"),
    path("api/traffic/", views.MonitoringTrafficView.as_view(), name="monitoring_traffic"),
    path("api/jobs/", views.MonitoringJobsView.as_view(), name="monitoring_jobs"),
    path("api/metrics/", views.MonitoringMetricsView.as_view(), name="monitoring_metrics"),
    path("api/user-activity/", views.MonitoringUserActivityView.as_view(), name="monitoring_user_activity"),
]
