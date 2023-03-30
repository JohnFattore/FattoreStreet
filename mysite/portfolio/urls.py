from django.urls import include, path
from rest_framework import routers, viewsets, permissions
from . import views

# set up URLS for apis
router = routers.DefaultRouter()
router.register(r'users', views.UserViewSet, 'users')
router.register(r'assets', views.AssetViewSet, 'assets')

app_name = 'portfolio'
urlpatterns = [
    path('', views.index, name='index'),
    path('register', views.register_view, name='register'),
    path('login', views.login_view, name='login'),
    path('<int:user_id>/portfolio/', views.portfolio_view, name='portfolio'),
    path('<int:user_id>/buy/', views.buy_view, name='buy'),
    path('<int:user_id>/buy_CSV/', views.buy_CSV_view, name='buy_CSV'),
    path('<int:user_id>/sell/', views.sell_view, name='sell'),
    path('<int:user_id>/allocation/', views.allocation_view, name='allocation'),
    path('<int:user_id>/schedule/', views.schedule_view, name='schedule'),
    path('<int:user_id>/logout/', views.logout_view, name='logout'),
    path('api/', include(router.urls)),
    path('api-auth/', include('rest_framework.urls', namespace='rest_framework'))
]