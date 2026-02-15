from rest_framework import viewsets, permissions
from .models import ChangeRequest
from .serializers import ChangeRequestSerializer

class ChangeRequestViewSet(viewsets.ModelViewSet):
    queryset = ChangeRequest.objects.all()
    serializer_class = ChangeRequestSerializer
    permission_classes = [permissions.IsAuthenticated]
