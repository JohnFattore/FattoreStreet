from django.test import TestCase
from django.contrib.auth.models import User
from rest_framework.test import APIClient
from rest_framework import status
from .models import ChangeRequest

class ChangeRequestAPITests(TestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)

    def test_create_change_request(self):
        data = {
            "title": "Test Request",
            "description": "This is a test",
            "priority": "HIGH",
            "data": {"key": "value"}
        }
        response = self.client.post('/changeflow/api/requests/', data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(ChangeRequest.objects.count(), 1)
        self.assertEqual(ChangeRequest.objects.get().title, "Test Request")

    def test_get_change_requests(self):
        ChangeRequest.objects.create(title="Request 1")
        ChangeRequest.objects.create(title="Request 2")
        response = self.client.get('/changeflow/api/requests/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)
