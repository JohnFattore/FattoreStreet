from django.urls import reverse
from rest_framework import status
from tests.base import BaseAPITestCase
from users.views import UserCreateView


class UserTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
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

    def test_create_duplicate_username(self):
        """BaseAPITestCase already creates 'testuser'; duplicating should fail."""
        data = {'username': 'testuser', 'password': 'password', 'email': 'dup@test.com'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_user_missing_password(self):
        data = {'username': 'NoPassword'}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_register_login_integration(self):
        register_data = {'username': 'IntegrationTestUser', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(reverse('users'), register_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        login_data = {'username': 'IntegrationTestUser', 'password': 'password'}
        response = self.client.post(reverse('token_obtain_pair'), login_data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('access', response.data)
        self.assertIn('refresh', response.data)
        token = response.data['access']

        # Use the token to access a protected endpoint
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token}')
        response = self.client.get(reverse('accounts'))
        self.assertEqual(response.status_code, status.HTTP_200_OK)
