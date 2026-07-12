from rest_framework import permissions, generics
from .serializers import RestaurantSerializer, ReviewSerializer
from .models import Restaurant, Review
from .permissions import IsOwner

# API endpoint for 'get' Restaurants and 'post' Restaurant
class RestaurantListCreateView(generics.ListCreateAPIView):
    queryset = Restaurant.objects.all()
    serializer_class = RestaurantSerializer
    permission_classes = [permissions.AllowAny]
    def get_queryset(self):
        return Restaurant.objects.filter(state=self.request.GET.get("state"), city=self.request.GET.get("city"))

class ReviewRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Review.objects.all().select_related("restaurant")
    serializer_class = ReviewSerializer
    permission_classes = [IsOwner]

# API endpoint for 'get' assets and 'post' asset
class ReviewListView(generics.ListAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [IsOwner]
    def get_queryset(self):
        return Review.objects.filter(user=self.request.user).select_related("restaurant") 

class ReviewCreateView(generics.CreateAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [permissions.IsAuthenticated]
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

class ReviewUpdateView(generics.UpdateAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [IsOwner]

#class RestaurantRecommenderView(generics.ListCreateAPIView):
#    queryset = Restaurant.objects.all()
#    serializer_class = RestaurantSerializer
#    def get_queryset(self):
#        recommendedRestaurants = getRestaurantRecommendations(self.request.user)
#        return Restaurant.objects.filter(yelp_id__in=recommendedRestaurants)