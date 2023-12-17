from django.urls import include, path
from rest_framework import routers, viewsets, permissions
from . import views
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

# set up URLS for apis
# router = routers.DefaultRouter()
router = routers.SimpleRouter()
# router.register(r'users', views.UserViewSet, 'users')
# router.register(r'assets', views.AssetViewSet, 'assets')
# router.register(r'assets', views.AssetListView)

app_name = 'portfolio'
urlpatterns = [
    # Old django frontend views, abandon for react frontend
#    path('', views.index, name='index'),
#    path('register', views.register_view, name='register'),
#    path('login', views.login_view, name='login'),
#    path('<int:user_id>/portfolio/', views.portfolio_view, name='portfolio'),
#    path('<int:user_id>/buy/', views.buy_view, name='buy'),
#    path('<int:user_id>/buy_CSV/', views.buy_CSV_view, name='buy_CSV'),
#    path('<int:user_id>/sell/', views.sell_view, name='sell'),
#    path('<int:user_id>/allocation/', views.allocation_view, name='allocation'),
#    path('<int:user_id>/schedule/', views.schedule_view, name='schedule'),
#    path('<int:user_id>/logout/', views.logout_view, name='logout'),

    # API routes
    path('api/', include(router.urls)),
    # the paths from views are more direct than the router (subject to change)
    path('api/assets/', views.AssetListCreateView.as_view(), name="assets"),
    path('api/asset/<int:pk>/', views.AssetRetrieveDestroyView.as_view(), name="asset"),
    path('api/users/', views.UserCreateView.as_view(), name="users"),
    path('api-auth/', include('rest_framework.urls', namespace='rest_framework')),
    # routes for djangorestframework-simplejwt
    path('api/token/', TokenObtainPairView.as_view(), name='token_obtain_pair'),
    path('api/token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
]