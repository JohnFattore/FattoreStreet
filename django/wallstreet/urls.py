from django.urls import path
from . import views

urlpatterns = [
    path('api/options/', views.OptionsAPI.as_view(), name="options"),
    path('api/selections/', views.SelectionsAPI.as_view(), name="selections"),
    path('api/selection/<int:pk>/', views.SelectionAPI.as_view(), name="selection"),
]