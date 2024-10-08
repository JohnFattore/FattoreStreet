# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
# API modules using drf
from rest_framework import permissions, generics
from .serializers import AssetSerializer, UserSerializer
from .permissions import IsOwner
from .models import Asset, SnP500Price

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

# API endpoint for 'post' user, allow anyone to make an account
class UserCreateView(generics.CreateAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [permissions.AllowAny]