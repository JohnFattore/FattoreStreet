from django.urls import path

from . import views

app_name = 'portfolio'
urlpatterns = [
    path('', views.index, name='index'),
    path('portfolio', views.portfolio, name='portfolio'),
    path('register', views.register, name='register'),
    path('login', views.login, name='login')
]