# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
# API modules using drf
from rest_framework import permissions, generics
from .serializers import AssetSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price
from .tasks import SnP500PriceUpdate
from rest_framework.response import Response
from rest_framework import status
from rest_framework.views import APIView

# API endpoint for 'get' assets and 'post' asset
class AssetListCreateView(generics.ListCreateAPIView):
    queryset = Asset.objects.all()
    serializer_class = AssetSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the assets the user owns
    def get_queryset(self):
        return Asset.objects.filter(user=self.request.user).select_related("SnP500Price")

    # user comes from different part of response as other data
    def perform_create(self, serializer):
        SnP = SnP500Price.objects.get(date=self.request.data["buy"])
        serializer.save(user=self.request.user, SnP500Price=SnP)


# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class AssetRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Asset.objects.all().select_related("SnP500Price")
    serializer_class = AssetSerializer
    permission_classes = [IsOwner]


class UpdateSnP500PriceView(APIView):
    def post(self, request):
        # Trigger the Celery task
        task = SnP500PriceUpdate.delay()
        return Response({"task_id": task.id}, status=status.HTTP_202_ACCEPTED)