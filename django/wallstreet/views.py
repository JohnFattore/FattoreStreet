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
    today = datetime.now()
    nextSunday = (today + timedelta((6-today.weekday()) % 7 ))
    queryset = Option.objects.filter(sunday=nextSunday or nextSunday - 7)
    serializer_class = OptionSerializer

# API endpoint for 'get' options and 'post' option
class SelectionListCreateView(generics.ListCreateAPIView):
    today = datetime.now()
    nextSunday = (today + timedelta((6-today.weekday()) % 7 ))
    # option = select option.id from options where option.sunday IN (thisSunday, nextSunday)
    options = Option.objects.filter(sunday=nextSunday or nextSunday - 7)
    queryset = Selection.objects.filter(option__in=options)
    serializer_class = SelectionSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the assets the user owns
    def get_queryset(self):
        return Selection.objects.filter(user=self.request.user)
    
    # user comes from different part of response as other data
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
    
# API endpoint for 'get' or 'delete' asset, only the owner should be able to do this
class SelectionRetrieveDestroyView(generics.RetrieveDestroyAPIView):
    queryset = Selection.objects.all()
    serializer_class = SelectionSerializer
    permission_classes = [IsOwner]