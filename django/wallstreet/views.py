# only API views, see retiredViews for old django frontend views
# from django.contrib.auth.models import User
# API modules using drf
from rest_framework import generics, permissions
from .serializers import OptionSerializer, SelectionSerializer
from .models import Option, Selection
from datetime import datetime, timedelta
from .permissions import IsOwner

# API endpoint for 'get' options and 'post' option
class OptionListCreateView(generics.ListCreateAPIView):
    serializer_class = OptionSerializer
    def get_queryset(self):
        return Option.objects.filter(sunday = self.request.GET.get('sunday', '1999-01-01'))

# API endpoint for 'get' options and 'post' option
class SelectionListCreateView(generics.ListCreateAPIView):
    serializer_class = SelectionSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the selections the user owns and that are current
    def get_queryset(self):
        options = Option.objects.filter(sunday = self.request.GET.get('sunday', '1999-01-01'))
        # selections = Selection.objects.filter(option__in=options)
        return Selection.objects.filter(user=self.request.user, option__in=options)
    
    # user comes from different part of response as other data
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
    
# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class SelectionRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Selection.objects.all()
    serializer_class = SelectionSerializer
    permission_classes = [IsOwner]