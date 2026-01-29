from django.shortcuts import render
from rest_framework import viewsets
from .models import ChangeRequest
from .serializers import ChangeRequestSerializer

class ChangeRequestViewSet(viewsets.ModelViewSet):
    queryset = ChangeRequest.objects.all()
    serializer_class = ChangeRequestSerializer
