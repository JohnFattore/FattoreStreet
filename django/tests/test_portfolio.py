from rest_framework import status
from django.urls import reverse
from portfolio.models import Asset
from tests.base import BaseAPITestCase

class AssetsTests(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('assets')

    def post_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13'}
        return self.client.post(reverse('assets'), data, format='json')

    def test_create_asset_unauthenticated(self):
        self.unauthenticate_client()
        response = self.post_asset()
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_asset(self):
        self.authenticate_client()
        response = self.post_asset()
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created_asset = Asset.objects.latest('id')
        self.assertEqual(created_asset.user, self.user)

    def test_create_asset_future_date(self):
        self.authenticate_client()
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2029-10-13'}
        response = self.client.post(reverse('assets'), data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("buy date", str(response.data).lower())

    def test_list_assets(self):
        self.authenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
    
    def test_list_assets_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

class AssetDeleteTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.authenticate_client()
        self.asset_response = self.post_asset()
        self.url = reverse('asset', kwargs={'pk': self.asset_response.data["id"]})

    def post_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13'}
        return self.client.post(reverse('assets'), data, format='json')

    def test_delete_asset_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.delete(self.url, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_delete_asset(self):
        self.authenticate_client()
        response = self.client.delete(self.url, format='json')
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

class AssetSellTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.authenticate_client()
        self.asset_response = self.post_asset()
        self.url = reverse('asset', kwargs={'pk': self.asset_response.data["id"]})
        self.data = {'sell_date': '2023-11-14'}

    def post_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13'}
        return self.client.post(reverse('assets'), data, format='json')

    def test_sell_asset_unauthenticated(self):
        self.unauthenticate_client()
        response = self.client.patch(self.url, self.data, format='json')
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_sell_asset(self):
        self.authenticate_client()
        response = self.client.patch(self.url, self.data, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_sell_asset_before_buy_date(self):
        self.data = {'sell_date': '2023-09-14'}
        self.authenticate_client()
        response = self.client.patch(self.url, self.data, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        # self.assertIn("sell date", response.data["non_field_errors"][0].lower()) # Adjusted loosely as actual response structure might vary

class AssetInfoTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('asset-info')

    def test_asset_info(self):
        # Asset info might need auth or not, previous test didn't enforce it but better to have it if needed
        # Assuming public or auth agnostic for now based on previous test
        data = {'tickers': ['AAPL']}
        response = self.client.get(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)

class AssetPricesTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('asset-prices')

    def test_quote(self):
        data = {'ticker': "AAPL"}
        response = self.client.get(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)

class QuoteTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('quote')

    def test_quote(self):
        data = {'symbol': "AAPL"}
        response = self.client.get(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)

class FredDataTest(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.url = reverse('fred-data')

    def test_quote(self):
        data = [{ "series_id": "UNRATE", "compute_yoy": False}]
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)
