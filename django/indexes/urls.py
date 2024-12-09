from django.urls import path
from . import views

urlpatterns = [
    path('api/index_members/', views.IndexMemberListAPI.as_view(), name="indexMemberList"),
    path('api/index_members_update/<int:pk>/', views.IndexMemberUpdateAPI.as_view(), name="indexMemberUpdate"),
]