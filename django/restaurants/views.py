from rest_framework import permissions, generics, status, response, views
from .serializers import RestaurantSerializer, ReviewSerializer
from .models import Restaurant, Review
from .permissions import IsOwner
from .tasks import YelpLoad

# API endpoint for 'get' Restaurants and 'post' Restaurant
class RestaurantListCreateView(generics.ListCreateAPIView):
    queryset = Restaurant.objects.all()
    serializer_class = RestaurantSerializer
    def get_queryset(self):
        return Restaurant.objects.filter(state=self.request.GET.get("state"), city=self.request.GET.get("city"))

# API endpoint for 'get' assets and 'post' asset
class ReviewListView(generics.ListAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [permissions.IsAuthenticated]


class ReviewCreateView(generics.CreateAPIView):
    queryset = Review.objects.all()
    serializer_class = ReviewSerializer
    permission_classes = [IsOwner]
    # user comes from different part of response as other data
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)

class YelpLoadView(views.APIView):
    """
    API endpoint to trigger the YelpLoad Celery task.
    """
    def post(self, request):
        # Trigger the Celery task
        task = YelpLoad.delay()  # Asynchronously starts the task
        return response.Response(
            {
                "message": "YelpLoad task has been initiated.",
                "task_id": task.id,  # Return the Celery task ID
            },
            status=status.HTTP_202_ACCEPTED
        )