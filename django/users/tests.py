from rest_framework.test import APITestCase
from django.urls import reverse
from rest_framework.test import APIRequestFactory, force_authenticate
from .views import UserCreateView
from rest_framework import status

# Unit Tests
class UserCreateTest(APITestCase):
    def setUp(self):
        self.factory = APIRequestFactory()
        self.url = reverse('users')
        self.view = UserCreateView.as_view()

    def test_create_user(self):
        data = {'username': 'UnitTest', 'password': 'password', 'email': 'test@test.com'}
        request = self.factory.post(self.url, data, format='json')
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_user_client(self):
        data = {'username': 'UnitTest', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

# Integration Tests
class integration(APITestCase):
    def setUp(self):
        # self.url = reverse('asset')
        self.view = UserCreateView.as_view()

    def test_register_login(self):
        registerData = {'username': 'IntegrationTestUser', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(reverse('users'), registerData, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        loginData = {'username': 'IntegrationTestUser', 'password': 'password'}
        response = self.client.post(reverse('token_obtain_pair'), loginData, format='json')
        token = response.data['access']
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token}')
        # self.assertEqual()