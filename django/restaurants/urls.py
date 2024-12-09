from django.urls import path
from . import views

urlpatterns = [
    path('api/restaurants/', views.RestaurantListCreateView.as_view(), name="restaurants"),
]