from django.urls import path
from . import views

urlpatterns = [
    path('api/conversations/', views.ConversationListCreateView.as_view(), name="conversation-list"),
    path('api/conversations/<int:pk>/', views.ConversationDetailView.as_view(), name="conversation-detail"),
    path('api/conversations/<int:pk>/messages/', views.ConversationMessageView.as_view(), name="conversation-messages"),
]
