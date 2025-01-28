from django.urls import path
from . import views 

urlpatterns = [
    path('api/restaurant-list-create/', views.RestaurantListCreateView.as_view(), name="restaurants"),
    path('api/review-list/', views.ReviewListView.as_view(), name="reviewList"),
    path('api/review-create/', views.ReviewCreateView.as_view(), name="reviewCreate"),
    path('api/review-update/<int:pk>/', views.ReviewUpdateView.as_view(), name="reviewUpdate"),
    path('api/review/<int:pk>/', views.ReviewRetrieveDestroyView.as_view(), name="review"),
    path('api/yelp-load/', views.YelpLoadView.as_view(), name="yelpLoad"),
    path('api/restaurant-recommend/', views.RestaurantRecommenderView.as_view(), name="restaurantRecommend"),
]