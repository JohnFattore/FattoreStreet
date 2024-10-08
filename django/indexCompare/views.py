from rest_framework import generics, permissions
from .models import Outlier, IndexMember
from .serializers import OutlierSerializer, IndexMemberSerializer

# outlier list outdated
class OutlierListAPI(generics.ListAPIView):
    serializer_class = OutlierSerializer
    queryset = Outlier.objects.all()

class OutlierUpdateAPI(generics.UpdateAPIView):
    serializer_class = OutlierSerializer
    queryset = Outlier.objects.all()
    permission_classes = [permissions.IsAuthenticated]

# new 'outliers'
class IndexMemberListAPI(generics.ListAPIView):
    serializer_class = IndexMemberSerializer
    queryset = IndexMember.objects.all().select_related("stock")

class IndexMemberUpdateAPI(generics.UpdateAPIView):
    serializer_class = IndexMemberSerializer
    queryset = IndexMember.objects.all()
    permission_classes = [permissions.IsAuthenticated]