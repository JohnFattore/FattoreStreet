from rest_framework import generics, permissions
from .serializers import OptionSerializer, SelectionSerializer, ResultSerializer
from .models import Option, Selection, Result
from .permissions import IsOwner
from django_filters import rest_framework as filters

# API endpoint for 'get' options and 'post' option
# this route uses django-filter, which may or may not be useful
class OptionListAPI(generics.ListAPIView):
    serializer_class = OptionSerializer
    queryset = Option.objects.all()
    filter_backends = (filters.DjangoFilterBackend,)
    filterset_fields = ('sunday', 'benchmark')

# API endpoint for 'get' options and 'post' option
class SelectionListCreateAPI(generics.ListCreateAPIView):
    serializer_class = SelectionSerializer
    permission_classes = [permissions.IsAuthenticated]
    # return only the selections the user owns and that are current
    def get_queryset(self):
        options = Option.objects.filter(sunday=self.request.query_params.get('sunday'))
        return Selection.objects.filter(user=self.request.user, option__in=options)
    
    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
    
# API endpoint for 'get' or 'delete' selection, only the owner should be able to do this
class SelectionRetrieveDestroyAPI(generics.RetrieveDestroyAPIView):
    queryset = Selection.objects.all()
    serializer_class = SelectionSerializer
    permission_classes = [IsOwner]

class ResultListAPI(generics.ListAPIView):
    serializer_class = ResultSerializer
    permission_classes = [permissions.IsAuthenticated]
    def get_queryset(self):
        return Result.objects.filter(user=self.request.user, sunday=self.request.query_params.get('sunday'))

class ResultRetrieveAPI(generics.RetrieveAPIView):
    queryset = Result.objects.all()
    serializer_class = ResultSerializer
    permission_classes = [IsOwner]