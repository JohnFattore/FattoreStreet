from django.urls import path
from . import views
# router = routers.SimpleRouter()

# app_name = 'portfolio'
urlpatterns = [
    # API routes
    # path('api/', include(router.urls)),
    # the paths from views are more direct than the router (subject to change)
    path('api/index_members/', views.IndexMemberListAPI.as_view(), name="indexMemberList"),
    path('api/index_members_update/<int:pk>/', views.IndexMemberUpdateAPI.as_view(), name="indexMemberUpdate"),
]