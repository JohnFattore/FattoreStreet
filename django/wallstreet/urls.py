from django.urls import path

from . import views

urlpatterns = [
    path('api/options/', views.OptionListCreateView.as_view(), name="options"),
]