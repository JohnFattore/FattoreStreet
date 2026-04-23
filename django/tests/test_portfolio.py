from unittest.mock import patch, MagicMock
from decimal import Decimal
from datetime import date, datetime
from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework import status
from django.urls import reverse
import pandas as pd
from portfolio.models import Asset, Account
from portfolio.tasks import load_iex_hist
from portfolio.helper import get_historical_dividends, get_historical_prices, get_historical_splits
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


class AssetDividendsTest(BaseAPITestCase):
    """Tests for the asset-dividends endpoint (public, calls yfinance)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('asset-dividends')
        patch("portfolio.views.get_historical_dividends", return_value={
            "AAPL": {
                "2025-02-10": Decimal("0.25"),
                "2025-05-12": Decimal("0.26"),
            }
        }).start()
        self.addCleanup(patch.stopall)

    def test_asset_dividends(self):
        response = self.client.get(self.url, {'ticker': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertIsInstance(response.data, list)
        self.assertEqual(response.data[0]["date"], "2025-02-10")
        self.assertEqual(Decimal(str(response.data[0]["value"])), Decimal("0.25"))

    def test_asset_dividends_missing_ticker(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


class AssetSplitsTest(BaseAPITestCase):
    """Tests for the asset-splits endpoint (public, calls yfinance)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('asset-splits')
        patch("portfolio.views.get_historical_splits", return_value={
            "AAPL": {
                "2020-08-31": Decimal("4"),
                "2014-06-09": Decimal("7"),
            }
        }).start()
        self.addCleanup(patch.stopall)

    def test_asset_splits(self):
        response = self.client.get(self.url, {'ticker': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertIsInstance(response.data, list)
        self.assertEqual(response.data[0]["date"], "2020-08-31")
        self.assertEqual(Decimal(str(response.data[0]["value"])), Decimal("4"))

    def test_asset_splits_missing_ticker(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 400)


class HistoricalPricesHelperTest(TestCase):
    """Tests for yfinance historical price extraction."""

    @patch("portfolio.helper.cache.set")
    @patch("portfolio.helper.cache.get", return_value=None)
    @patch("portfolio.helper.yf.Tickers")
    @patch("portfolio.helper.datetime")
    def test_uses_adjusted_close_when_available(
        self,
        mock_datetime,
        mock_tickers,
        _mock_cache_get,
        _mock_cache_set,
    ):
        mock_datetime.now.return_value = datetime(2025, 1, 15, 10, 0, 0)

        history_df = pd.DataFrame(
            {
                "Close": [Decimal("100.00"), Decimal("101.00")],
                "Adj Close": [Decimal("95.00"), Decimal("96.00")],
            },
            index=pd.to_datetime(["2025-01-13", "2025-01-14"]),
        )

        mock_ticker = MagicMock()
        mock_ticker.history.return_value = history_df
        mock_tickers.return_value = MagicMock(tickers={"AAPL": mock_ticker})

        result = get_historical_prices(["AAPL"])

        self.assertEqual(result["AAPL"]["2025-01-13"], Decimal("95.00"))
        self.assertEqual(result["AAPL"]["2025-01-14"], Decimal("96.00"))


class HistoricalDividendsHelperTest(TestCase):
    """Tests for yfinance historical dividend extraction."""

    @patch("portfolio.helper.cache.set")
    @patch("portfolio.helper.cache.get", return_value=None)
    @patch("portfolio.helper.yf.Tickers")
    def test_reads_dividend_series(self, mock_tickers, _mock_cache_get, _mock_cache_set):
        series = pd.Series(
            [Decimal("0.25"), Decimal("0.26")],
            index=pd.to_datetime(["2025-02-10", "2025-05-12"]),
        )
        mock_ticker = MagicMock()
        mock_ticker.dividends = series
        mock_tickers.return_value = MagicMock(tickers={"AAPL": mock_ticker})

        result = get_historical_dividends(["AAPL"])

        self.assertEqual(result["AAPL"]["2025-02-10"], Decimal("0.25"))
        self.assertEqual(result["AAPL"]["2025-05-12"], Decimal("0.26"))


class HistoricalSplitsHelperTest(TestCase):
    """Tests for yfinance historical split extraction."""

    @patch("portfolio.helper.cache.set")
    @patch("portfolio.helper.cache.get", return_value=None)
    @patch("portfolio.helper.yf.Tickers")
    def test_reads_split_series(self, mock_tickers, _mock_cache_get, _mock_cache_set):
        series = pd.Series(
            [Decimal("4"), Decimal("7")],
            index=pd.to_datetime(["2020-08-31", "2014-06-09"]),
        )
        mock_ticker = MagicMock()
        mock_ticker.splits = series
        mock_tickers.return_value = MagicMock(tickers={"AAPL": mock_ticker})

        result = get_historical_splits(["AAPL"])

        self.assertEqual(result["AAPL"]["2020-08-31"], Decimal("4"))
        self.assertEqual(result["AAPL"]["2014-06-09"], Decimal("7"))


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


class LoadIexHistTaskTests(TestCase):
    """Tests for portfolio.tasks.load_iex_hist (Spring Boot /admin/load-hist)."""

    def setUp(self):
        User = get_user_model()
        User.objects.filter(pk=1).delete()
        User.objects.create_user(
            username="sb_iex_scheduler",
            password="unused",  # pragma: allowlist secret
            id=1,
        )

    @override_settings(SPRINGBOOT_BASE_URL="")
    def test_missing_springboot_base_url(self):
        with patch("portfolio.tasks.requests.get") as mock_get:
            with self.assertRaises(ValueError) as ctx:
                load_iex_hist(days=10)
        mock_get.assert_not_called()
        self.assertIn("SPRINGBOOT_BASE_URL", str(ctx.exception))

    @override_settings(SPRINGBOOT_BASE_URL="http://springboot:8080")
    def test_missing_user_one(self):
        User = get_user_model()
        User.objects.filter(pk=1).delete()
        with patch("portfolio.tasks.requests.get") as mock_get:
            with self.assertRaises(User.DoesNotExist):
                load_iex_hist()
        mock_get.assert_not_called()

    @override_settings(SPRINGBOOT_BASE_URL="http://springboot:8080")
    @patch("portfolio.tasks.requests.get")
    def test_calls_spring_boot_with_bearer_and_days(self, mock_get):
        mock_response = MagicMock()
        mock_response.ok = True
        mock_response.status_code = 200
        mock_response.json.return_value = {"message": "ok", "processed": 1}
        mock_get.return_value = mock_response

        result = load_iex_hist(days=15)

        self.assertEqual(result["status_code"], 200)
        self.assertEqual(result["days"], 15)
        mock_get.assert_called_once()
        args, kwargs = mock_get.call_args
        self.assertTrue(args[0].endswith("/admin/load-hist"))
        self.assertEqual(kwargs["params"], {"days": 15})
        auth = kwargs["headers"]["Authorization"]
        self.assertTrue(auth.startswith("Bearer "))
        self.assertIn("timeout", kwargs)
