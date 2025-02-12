from django.urls import include, path
from . import views
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

urlpatterns = [
    path('api/users/', views.UserCreateView.as_view(), name="users"),
    path('api-auth/', include('rest_framework.urls', namespace='rest_framework')),
    # routes for djangorestframework-simplejwt
    path('api/token/', TokenObtainPairView.as_view(), name='token_obtain_pair'),
    path('api/token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
    path('api/send-email/', views.SendEmailAPIView.as_view(), name='send_email'),
]