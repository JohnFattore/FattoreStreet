from django.urls import path

from . import views

app_name = 'portfolio'
urlpatterns = [
    path('', views.index, name='index'),
    path('register', views.register_view, name='register'),
    path('login_user', views.login_user_view, name='login_user'),
    path('<int:user_id>/portfolio/', views.portfolio_view, name='portfolio'),
    path('<int:user_id>/buy/', views.buy_view, name='buy'),
    path('<int:user_id>/buy_CSV/', views.buy_CSV_view, name='buy_CSV'),
    path('<int:user_id>/sell/', views.sell_view, name='sell'),
    path('<int:user_id>/schedule/', views.schedule_view, name='schedule'),
    path('<int:user_id>/logout/', views.logout_view, name='logout')
]