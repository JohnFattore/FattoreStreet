from django.urls import include, path
from rest_framework import routers
from . import views
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

# router = routers.SimpleRouter()

# app_name = 'portfolio'
urlpatterns = [
    # API routes
    # path('api/', include(router.urls)),
    # the paths from views are more direct than the router (subject to change)
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/asset/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/users/', views.UserCreateView.as_view(), name="users"),
    path('api-auth/', include('rest_framework.urls', namespace='rest_framework')),
    # routes for djangorestframework-simplejwt
    path('api/token/', TokenObtainPairView.as_view(), name='token_obtain_pair'),
    path('api/token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
]