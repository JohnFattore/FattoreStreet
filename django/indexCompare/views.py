from rest_framework import generics, permissions
from .models import Outlier
from .serializers import OutlierSerializer

class OutlierListAPI(generics.ListAPIView):
    serializer_class = OutlierSerializer
    queryset = Outlier.objects.all()

class OutlierUpdateAPI(generics.UpdateAPIView):
    serializer_class = OutlierSerializer
    queryset = Outlier.objects.all()
    permission_classes = [permissions.IsAuthenticated]