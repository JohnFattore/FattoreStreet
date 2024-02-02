from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from portfolio.models import Asset  # Import your Asset model
from portfolio.views import AssetListCreateView, UserCreateView
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse
import json


class EndToEndTest1(APITestCase):
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
        self.assertEqual()