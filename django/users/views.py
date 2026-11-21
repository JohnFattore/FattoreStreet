from django.contrib.auth.models import User
from rest_framework import permissions, generics
from .serializers import CurrentUserSerializer, UserSerializer

# API endpoint for 'post' user, allow anyone to make an account
class UserCreateView(generics.CreateAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    authentication_classes = []
    permission_classes = [permissions.AllowAny]


class CurrentUserView(generics.RetrieveAPIView):
    serializer_class = CurrentUserSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user
