from django.urls import path

from . import views

app_name = 'portfolio'
urlpatterns = [
    path('', views.index, name='index'),
    path('register', views.register_view, name='register'),
    path('login_user', views.login_user_view, name='login_user'),
    path('<int:user_id>/portfolio/', views.portfolio_view, name='portfolio'),
    path('<int:user_id>/buy/', views.buy_view, name='buy'),
    path('<int:user_id>/sell/', views.sell_view, name='sell')
]