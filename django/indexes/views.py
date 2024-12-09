from rest_framework import generics, permissions
from .models import IndexMember
from .serializers import IndexMemberSerializer

# new 'outliers'
class IndexMemberListAPI(generics.ListAPIView):
    serializer_class = IndexMemberSerializer
    queryset = IndexMember.objects.all().select_related("stock")

class IndexMemberUpdateAPI(generics.UpdateAPIView):
    serializer_class = IndexMemberSerializer
    queryset = IndexMember.objects.all()
    permission_classes = [permissions.IsAuthenticated]