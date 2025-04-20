from rest_framework.test import APITestCase
from django.contrib.auth.models import User
from rest_framework import status
from portfolio.models import Asset
from django.urls import reverse
from datetime import date

class BaseAssetTest(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='testuser', password='testpassword')
        
    def post_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13'}
        return self.client.post(reverse('assets'), data, format='json')

class AssetsTests(BaseAssetTest):
    def setUp(self):
        super().setUp()
        self.url = reverse('assets')

    def test_create_asset_unauthenticated(self):
        self.client.force_authenticate(user=None)
        response = self.post_asset()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_asset(self):
        self.client.force_authenticate(user=self.user)
        response = self.post_asset()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

## list
    def test_list_assets(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
    
    def test_list_assets_unauthenticated(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

class AssetDeleteTest(BaseAssetTest):
    def setUp(self):
        super().setUp()
        self.client.force_authenticate(user=self.user)
        response = self.post_asset()
        self.url = reverse('asset-get-delete', kwargs={'pk': response.data["id"]})

    def test_delete_asset_unauthenticated(self):
        self.client.force_authenticate(user=None)
        response = self.client.delete(self.url, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_asset(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.delete(self.url, format='json')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

class AssetSellTest(BaseAssetTest):
    def setUp(self):
        super().setUp()
        self.client.force_authenticate(user=self.user)
        response = self.post_asset()
        self.url = reverse('update-sell-date', kwargs={'pk': response.data["id"]})
        self.data = {'sell_date': '2023-11-14'}

    def test_sell_asset_unauthenticated(self):
        self.client.force_authenticate(user=None)
        response = self.client.patch(self.url, self.data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_sell_asset(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.patch(self.url, self.data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

# Other model tests
class SnP500GetTests(BaseAssetTest):
    def setUp(self):
        super().setUp()
        self.url = reverse('snp500-price')
        self.post_asset()

    def test_retrieve_existing_date(self):
        data = {'date': '2023-10-13'}
        response = self.client.get(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)

class QuoteTest(BaseAssetTest):
    def setUp(self):
        self.url = reverse('quote')

    def test_quote(self):
        data = {'ticker': 'SPY'}
        response = self.client.get(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)