from django.urls import path

from . import views

app_name = "entertainment"

urlpatterns = [
    path("", views.recommendation_list, name="recommendation_list"),
]
