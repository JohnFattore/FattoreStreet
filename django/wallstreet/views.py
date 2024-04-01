# only API views, see retiredViews for old django frontend views
# from django.contrib.auth.models import User
# API modules using drf
from rest_framework import generics, permissions
from .serializers import OptionSerializer, SelectionSerializer
from .models import Option, Selection, Result
from datetime import datetime, timedelta
from .permissions import IsOwner

# API endpoint for 'get' options and 'post' option
class OptionsAPI(generics.ListCreateAPIView):
    serializer_class = OptionSerializer
    def get_queryset(self):
        sunday = self.request.GET.get('sunday', '1999-01-01')
        benchmark = self.request.GET.get('benchmark', False)
        return Option.objects.filter(sunday=sunday, benchmark=benchmark)

# API endpoint for 'get' options and 'post' option
class SelectionsAPI(generics.ListCreateAPIView):
    serializer_class = SelectionSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the selections the user owns and that are current
    def get_queryset(self):
        # return error if no sunday
        options = Option.objects.filter(sunday = self.request.GET.get('sunday', '1999-01-01'))
        return Selection.objects.filter(user=self.request.user, option__in=options)
    
    # user comes from different part of response as other data
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
    
# API endpoint for 'get' or 'delete' selection, only the owner should be able to do this
class SelectionAPI(generics.RetrieveDestroyAPIView):
    queryset = Selection.objects.all()
    serializer_class = SelectionSerializer
    permission_classes = [IsOwner]

class UserWeeklyResultListView(generics.ListAPIView):
    def get_queryset(self):
        # return error if no sunday
        return Result.objects.filter(user=self.request.user, sunday= self.request.GET.get('sunday', '1999-01-01'))