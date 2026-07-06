from unittest.mock import patch

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


class MarketDataPatchMixin:
    """Patches serializer-level market data lookups for asset endpoint tests.

    Set `mock_prices` on the subclass; list the mixin before BaseAPITestCase.
    """
    mock_prices: dict = {}

    def setUp(self):
        super().setUp()
        patch(
            "portfolio.serializers.get_historical_prices",
            side_effect=lambda tickers: {t: self.mock_prices.get(t, {}) for t in tickers},
        ).start()
        patch("portfolio.serializers.is_market_open", return_value=True).start()
        self.addCleanup(patch.stopall)
