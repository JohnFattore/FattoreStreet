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
    path('', views.index, name="index"),
]