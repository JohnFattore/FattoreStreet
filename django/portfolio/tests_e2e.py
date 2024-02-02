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
        self.view = AssetListCreateView.as_view()

    def test_e2e(self):
        registerData = {'username': 'E2EUser', 'password': 'password', 'email': 'test@test.com'}
        response = self.client.post(reverse('users'), registerData, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        loginData = {'username': 'E2EUser', 'password': 'password'}
        response = self.client.post(reverse('token_obtain_pair'), loginData, format='json')
        token = response.data['access']
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token}')
        assetData = {'ticker': 'SPY', 'shares': 10, 'costbasis': 200, 'buy': '2023-10-13', 'user': 1}
        response = self.client.post(reverse('assets'), assetData, format='json')
        response_data = json.loads(response.content)
        self.asset_id = response_data.get('id')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        response = self.client.get(reverse('assets'), format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("SPY", str(response.content).upper())
        response = self.client.get(reverse('asset', kwargs={'pk': self.asset_id}), format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        response = self.client.delete(reverse('asset', kwargs={'pk': self.asset_id}), format='json')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)