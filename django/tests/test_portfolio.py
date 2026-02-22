from unittest.mock import patch, MagicMock
from decimal import Decimal
from datetime import date
from django.test import TestCase, override_settings
from rest_framework import status
from django.urls import reverse
from portfolio.models import Asset, Account
from portfolio.tasks import load_iex_hist, refresh_corporate_actions
from tests.base import BaseAPITestCase

MOCK_PRICES = {
    "SPY": {
        "2023-01-02": Decimal("380.00"),
        "2023-10-13": Decimal("430.00"),
        "2023-11-14": Decimal("440.00"),
    },
    "MSFT": {"2023-01-02": Decimal("240.00")},
    "GOOG": {"2023-01-02": Decimal("90.00")},
    "AAPL": {
        "2023-10-12": Decimal("178.00"),
        "2023-10-06": Decimal("175.00"),
        "2023-09-13": Decimal("170.00"),
        "2023-01-03": Decimal("130.00"),
        "2022-10-13": Decimal("140.00"),
        "2020-10-13": Decimal("120.00"),
        "2018-10-12": Decimal("100.00"),
    },
}

MOCK_REFERENCE_DATES = {
    "yesterday": date(2023, 10, 12),
    "1_week_ago": date(2023, 10, 6),
    "1_month_ago": date(2023, 9, 13),
    "year_to_date": date(2023, 1, 3),
    "1_year_ago": date(2022, 10, 13),
    "3_years_ago": date(2020, 10, 13),
    "5_years_ago": date(2018, 10, 12),
}

MOCK_YFINANCE_DATA = {
    "AAPL": {
        "ticker": "AAPL",
        "short_name": "Apple Inc.",
        "long_name": "Apple Inc.",
        "type": "EQUITY",
        "market": "us_market",
        "exchange": "NASDAQ",
    }
}

MOCK_QUOTE = {"price": Decimal("178.72"), "percent_change_daily": Decimal("0.012")}


def _mock_historical_prices(tickers):
    return {t: MOCK_PRICES.get(t, {}) for t in tickers}


class AssetsTests(BaseAPITestCase):
    """Tests for asset list/create endpoints."""

    def setUp(self):
        super().setUp()
        self.url = reverse('assets')
        patch("portfolio.serializers.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.serializers.is_market_open", return_value=True).start()
        self.addCleanup(patch.stopall)

    def post_asset(self):
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13'}
        return self.client.post(reverse('assets'), data, format='json')

    def test_get_assets_filtered_by_account(self):
        """Test retrieving assets filtered by account."""
        self.authenticate_client()
        account_data = {'name': 'Test Account', 'account_type': 'ROTH_IRA'}
        self.client.post(reverse('accounts'), account_data, format='json')
        account = Account.objects.get(name='Test Account')

        Asset.objects.create(ticker='MSFT', shares=5, buy_date='2023-01-02', user=self.user, account=account)
        Asset.objects.create(ticker='GOOG', shares=5, buy_date='2023-01-02', user=self.user)

        response = self.client.get(reverse('assets'), {'account_id': account.id})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['ticker'], 'MSFT')

        response = self.client.get(reverse('assets'))
        self.assertEqual(len(response.data), 2)

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
    """Tests for asset deletion."""

    def setUp(self):
        super().setUp()
        patch("portfolio.serializers.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.serializers.is_market_open", return_value=True).start()
        self.addCleanup(patch.stopall)
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
    """Tests for selling (patching) an asset."""

    def setUp(self):
        super().setUp()
        patch("portfolio.serializers.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.serializers.is_market_open", return_value=True).start()
        self.addCleanup(patch.stopall)
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


class AssetInfoTest(BaseAPITestCase):
    """Tests for the asset-info endpoint (public, calls yfinance + Finnhub)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('asset-info')
        patch("portfolio.views.get_yfinance_data", return_value=MOCK_YFINANCE_DATA).start()
        patch("portfolio.views.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.views.get_market_reference_dates", return_value=MOCK_REFERENCE_DATES).start()
        patch("portfolio.views.get_realtime_price", return_value=MOCK_QUOTE).start()
        self.addCleanup(patch.stopall)

    def test_asset_info(self):
        response = self.client.get(self.url, {'tickers': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertIn("data", response.data)
        self.assertEqual(len(response.data["data"]), 1)
        self.assertEqual(response.data["data"][0]["ticker"], "AAPL")

    def test_asset_info_missing_tickers(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


class AssetPricesTest(BaseAPITestCase):
    """Tests for the asset-prices endpoint (public, calls yfinance)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('asset-prices')
        patch("portfolio.views.get_historical_prices", side_effect=_mock_historical_prices).start()
        self.addCleanup(patch.stopall)

    def test_asset_prices(self):
        response = self.client.get(self.url, {'ticker': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertIsInstance(response.data, list)

    def test_asset_prices_missing_ticker(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


class QuoteTest(BaseAPITestCase):
    """Tests for the quote endpoint (public, calls Finnhub)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('quote')
        patch("portfolio.views.get_realtime_price", return_value=MOCK_QUOTE).start()
        self.addCleanup(patch.stopall)

    def test_quote(self):
        response = self.client.get(self.url, {'symbol': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertIn("price", response.data)

    def test_quote_missing_symbol(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


class FredDataTest(BaseAPITestCase):
    """Tests for the FRED data endpoint (public, calls FRED API)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('fred-data')
        patch("portfolio.views.get_fred_data", return_value=[
            {"date": "2023-01-01", "value": 3.5},
            {"date": "2023-02-01", "value": 3.4},
        ]).start()
        self.addCleanup(patch.stopall)

    def test_fred_data(self):
        data = [{"series_id": "UNRATE", "compute_yoy": False}]
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, 200)
        self.assertIn("UNRATE", response.data)

    def test_fred_data_empty_body(self):
        response = self.client.post(self.url, [], format='json')
        self.assertEqual(response.status_code, 200)


class QuarterlyDataTest(BaseAPITestCase):
    """Tests for the quarterly-data endpoint (public, calls yfinance)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('quarterly-data')
        patch("portfolio.views.get_quarterly_data", return_value={
            "income": [{"date": "2023-Q3", "revenue": 100000}],
            "balance_sheet": [],
            "cashflow": [],
        }).start()
        self.addCleanup(patch.stopall)

    def test_quarterly_data(self):
        response = self.client.get(self.url, {'ticker': 'AAPL'})
        self.assertEqual(response.status_code, 200)

    def test_quarterly_data_missing_ticker(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


@override_settings(
    SPRINGBOOT_INTERNAL_URL="http://springboot:8080",
    ADMIN_API_KEY="test-key",
)
class LoadIexHistTaskTest(TestCase):

    @patch("portfolio.tasks.requests.get")
    def test_calls_springboot_with_correct_params(self, mock_get):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {"processed": 3, "skipped": 2, "errors": 0}
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        result = load_iex_hist(days=10)

        mock_get.assert_called_once_with(
            "http://springboot:8080/admin/load-hist",
            params={"days": 10},
            headers={"X-Admin-Key": "test-key"},
            timeout=7200,
        )
        self.assertEqual(result["processed"], 3)

    @patch("portfolio.tasks.requests.get")
    def test_uses_default_days(self, mock_get):
        mock_response = MagicMock()
        mock_response.json.return_value = {"processed": 1}
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        load_iex_hist()

        call_kwargs = mock_get.call_args
        self.assertEqual(call_kwargs.kwargs["params"]["days"], 5)

    @patch("portfolio.tasks.requests.get")
    def test_handles_request_failure(self, mock_get):
        from requests.exceptions import ConnectionError
        mock_get.side_effect = ConnectionError("Connection refused")

        result = load_iex_hist(days=5)

        self.assertIn("error", result)


@override_settings(
    SPRINGBOOT_INTERNAL_URL="http://springboot:8080",
    ADMIN_API_KEY="test-key",
)
class RefreshCorporateActionsTaskTest(TestCase):

    @patch("portfolio.tasks.requests.get")
    def test_calls_springboot_with_force_false(self, mock_get):
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "tickersProcessed": 10, "skippedNoAsset": 5,
            "totalSplits": 2, "totalDividends": 8, "totalPricesUpdated": 500,
        }
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        result = refresh_corporate_actions()

        mock_get.assert_called_once_with(
            "http://springboot:8080/admin/adjust-prices",
            params={"force": "false"},
            headers={"X-Admin-Key": "test-key"},
            timeout=7200,
        )
        self.assertEqual(result["tickersProcessed"], 10)

    @patch("portfolio.tasks.requests.get")
    def test_calls_springboot_with_force_true(self, mock_get):
        mock_response = MagicMock()
        mock_response.json.return_value = {"tickersProcessed": 50}
        mock_response.raise_for_status = MagicMock()
        mock_get.return_value = mock_response

        result = refresh_corporate_actions(force=True)

        call_kwargs = mock_get.call_args
        self.assertEqual(call_kwargs.kwargs["params"]["force"], "true")
        self.assertEqual(result["tickersProcessed"], 50)

    @patch("portfolio.tasks.requests.get")
    def test_handles_request_failure(self, mock_get):
        from requests.exceptions import ConnectionError
        mock_get.side_effect = ConnectionError("Connection refused")

        result = refresh_corporate_actions()

        self.assertIn("error", result)
