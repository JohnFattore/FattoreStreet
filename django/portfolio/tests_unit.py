from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from portfolio.models import Asset  # Import your Asset model
from portfolio.views import AssetListCreateView, AssetRetrieveDestroyView, UserCreateView
from rest_framework.test import APIRequestFactory, force_authenticate
from rest_framework.test import APIClient
from django.urls import reverse

class AssetListCreateViewTests(APITestCase):
    def setUp(self):
        # Create a user for authentication
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.factory = APIRequestFactory()
        self.view = AssetListCreateView.as_view()
        self.url = reverse('assets')

    def test_create_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'costbasis': 200, 'buy': '2023-10-13', 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

    def test_create_asset_negative_shares(self):
        data = {'ticker': 'SPY', 'shares': -10, 'costbasis': 200, 'buy': '2023-10-13', 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("shares", str(response.content).lower())

    def test_create_asset_negative_costbasis(self):
        data = {'ticker': 'SPY', 'shares': 10, 'costbasis': -200, 'buy': '2023-10-13', 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("costbasis", str(response.content).lower())

    def test_create_asset_future_buy(self):
        data = {'ticker': 'SPY', 'shares': -10, 'costbasis': 200, 'buy': '3000-10-13', 'user': self.user.id}
        request = self.factory.post(self.url, data, format='json')
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        response.render()
        self.assertIn("buy", str(response.content).lower())

    def test_create_asset_client(self):
        self.client.force_authenticate(user=self.user)
        data = {'ticker': 'SPY', 'shares': 10, 'costbasis': 200, 'buy': '2023-10-13', 'user': self.user.id}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

    def test_create_asset_client_unauthenticated(self):
        data = {'ticker': 'SPY', 'shares': 10, 'costbasis': 200, 'buy': '2023-10-13', 'user': self.user.id}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

## list
    def test_list_assets(self):
        request = self.factory.get(self.url)
        force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_list_assets_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
    
    def test_list_assets_client_unauthenticated(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

class AssetDeleteTest(APITestCase):
    def setUp(self):
        # Create a user for authentication
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        self.asset = Asset.objects.create(ticker='SPY', shares=10, costbasis=200, buy='2023-10-13', user=self.user)
        self.factory = APIRequestFactory()
        self.url = reverse('asset', kwargs={'pk': self.asset.id})
        # self.url = f'/portfolio/api/asset/{self.asset.id}/'
        self.view = AssetRetrieveDestroyView.as_view()

    # idk
    def test_delete_asset_unauthenticated(self):
        request = self.factory.delete(self.url, format='json')
        # force_authenticate(request, user=self.user)
        response = self.view(request)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_asset_client(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.delete(self.url, format='json')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

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