from django.urls import path
from . import views

urlpatterns = [
    path('api/chatbot/', views.ChatbotView.as_view(), name="chatbot"),
]