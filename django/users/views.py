# only API views, see retiredViews for old django frontend views
from django.contrib.auth.models import User
# API modules using drf
from rest_framework import permissions, generics, response
from .serializers import UserSerializer
from django.core.mail import send_mail
from rest_framework.views import APIView
from rest_framework.response import Response

# API endpoint for 'post' user, allow anyone to make an account
class UserCreateView(generics.CreateAPIView):
    queryset = User.objects.all()
    serializer_class = UserSerializer
    permission_classes = [permissions.AllowAny]

class SendEmailAPIView(APIView):
    def post(self, request):
        send_mail(
            subject="Hello from Django",
            message="This is a test email.",
            from_email="your-email@gmail.com",
            recipient_list=["recipient@example.com"],
            fail_silently=False,
        )
        return response.Response({"message": "Email sent successfully!"})