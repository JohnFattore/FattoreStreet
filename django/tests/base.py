from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework.test import APIRequestFactory

class BaseAPITestCase(APITestCase):
    def setUp(self):
        # Common setup for all tests
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.factory = APIRequestFactory()
        # Explicitly set headers if needed, or use force_authenticate in individual tests
        
    def authenticate_client(self):
        self.client.force_authenticate(user=self.user)
        
    def unauthenticate_client(self):
        self.client.force_authenticate(user=None)
