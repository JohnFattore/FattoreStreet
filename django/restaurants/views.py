# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
# API modules using drf
from rest_framework import permissions, generics
from .serializers import RestaurantSerializer, ReviewSerializer
from .models import Restaurant, Review
from .permissions import IsOwner

# API endpoint for 'get' Restaurants and 'post' Restaurant
class RestaurantListCreateView(generics.ListCreateAPIView):
    queryset = Restaurant.objects.all()
    serializer_class = RestaurantSerializer


# API endpoint for 'get' assets and 'post' asset
class ReviewListView(generics.ListAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [permissions.IsAuthenticated]
    # def get_queryset(self):
    #     return Review.objects.filter(user=self.request.user).select_related("SnP500Price")


class ReviewCreateView(generics.CreateAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [IsOwner]
    # user comes from different part of response as other data
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)