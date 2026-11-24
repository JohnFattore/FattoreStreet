from unittest.mock import patch
from decimal import Decimal
from datetime import date
import requests
from rest_framework import status
from django.core.cache import cache
from django.test import override_settings
from django.urls import reverse
from django.contrib.auth.models import User
from portfolio.helper import QuoteFetchError
from portfolio.models import Asset, Account
from portfolio.views import MAX_ASSET_INFO_TICKERS, QuoteThrottle
from tests.base import BaseAPITestCase, MarketDataPatchMixin

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


class AssetsTests(MarketDataPatchMixin, BaseAPITestCase):
    """Tests for asset list/create endpoints."""

    mock_prices = MOCK_PRICES

    def setUp(self):
        super().setUp()
        self.url = reverse('assets')

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

    def test_create_asset_in_unknown_account_returns_404(self):
        self.authenticate_client()
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13', 'account_id': 999999}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(response.data['detail'], 'Account does not exist.')

    def test_create_asset_in_another_users_account_returns_404(self):
        other = User.objects.create_user(username='otheruser', password='other-pass-91')  # pragma: allowlist secret
        account = Account.objects.create(name='Not Mine', user=other, account_type='ROTH_IRA')
        self.authenticate_client()
        data = {'ticker': 'SPY', 'shares': 10, 'buy_date': '2023-10-13', 'account_id': account.id}
        response = self.client.post(self.url, data, format='json')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(response.data['detail'], 'Account does not exist.')
        self.assertFalse(Asset.objects.filter(account=account).exists())

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


class AssetDeleteTest(MarketDataPatchMixin, BaseAPITestCase):
    """Tests for asset deletion."""

    mock_prices = MOCK_PRICES

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


class AssetSellTest(MarketDataPatchMixin, BaseAPITestCase):
    """Tests for selling (patching) an asset."""

    mock_prices = MOCK_PRICES

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


class AssetInfoTest(BaseAPITestCase):
    """Tests for the asset-info endpoint (public, calls yfinance + Finnhub)."""

    def setUp(self):
        super().setUp()
        self.url = reverse('asset-info')
        self.mock_yfinance = patch("portfolio.views.get_yfinance_data", return_value=MOCK_YFINANCE_DATA).start()
        patch("portfolio.views.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.views.get_market_reference_dates", return_value=MOCK_REFERENCE_DATES).start()
        self.mock_quote = patch("portfolio.views.get_realtime_price", return_value=MOCK_QUOTE).start()
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

    def test_asset_info_deduplicates_tickers(self):
        response = self.client.get(self.url, {'tickers': 'AAPL,AAPL,AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(len(response.data["data"]), 1)
        self.mock_quote.assert_called_once_with("AAPL")

    def test_asset_info_rejects_too_many_tickers(self):
        too_many = ",".join(f"T{i}" for i in range(MAX_ASSET_INFO_TICKERS + 1))
        response = self.client.get(self.url, {'tickers': too_many})
        self.assertEqual(response.status_code, 400)
        self.assertIn("tickers", response.data)
        self.mock_yfinance.assert_not_called()
        self.mock_quote.assert_not_called()

    def test_asset_info_hides_upstream_error_detail(self):
        upstream_error = requests.HTTPError(
            "429 Client Error: Too Many Requests for url: https://finnhub.io/api/v1/quote?symbol=AAPL"
        )
        with patch("portfolio.views.get_realtime_price", side_effect=upstream_error):
            with self.assertLogs("portfolio.views", level="ERROR"):
                response = self.client.get(self.url, {'tickers': 'AAPL'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["data"], [])
        self.assertEqual(response.data["errors"], [{"ticker": "AAPL", "error": "Quote unavailable"}])
        self.assertNotIn("finnhub.io", response.content.decode())
        self.assertNotIn("429", response.content.decode())


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

    def test_quote_fetch_error_returns_controlled_message(self):
        with patch("portfolio.views.get_realtime_price", side_effect=QuoteFetchError("No real time quote for FAKE")):
            response = self.client.get(self.url, {'symbol': 'FAKE'})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data, {"symbol": "No real time quote for FAKE"})

    def test_quote_hides_upstream_error_detail(self):
        upstream_error = requests.HTTPError(
            "429 Client Error: Too Many Requests for url: https://finnhub.io/api/v1/quote?symbol=AAPL"
        )
        with patch("portfolio.views.get_realtime_price", side_effect=upstream_error):
            with self.assertLogs("portfolio.views", level="ERROR"):
                response = self.client.get(self.url, {'symbol': 'AAPL'})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.data, {"symbol": "Quote unavailable"})
        self.assertNotIn("finnhub.io", response.content.decode())
        self.assertNotIn("429", response.content.decode())


@override_settings(CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}})
class QuoteThrottleTest(BaseAPITestCase):
    """Tests for IP-based throttling on public quote endpoints."""

    def setUp(self):
        super().setUp()
        cache.clear()
        self.addCleanup(cache.clear)
        patch.dict(QuoteThrottle.THROTTLE_RATES, {"quote": "2/min"}).start()
        self.mock_quote = patch("portfolio.views.get_realtime_price", return_value=MOCK_QUOTE).start()
        patch("portfolio.views.get_yfinance_data", return_value=MOCK_YFINANCE_DATA).start()
        patch("portfolio.views.get_historical_prices", side_effect=_mock_historical_prices).start()
        patch("portfolio.views.get_market_reference_dates", return_value=MOCK_REFERENCE_DATES).start()
        self.addCleanup(patch.stopall)
        self.quote_url = reverse('quote')
        self.asset_info_url = reverse('asset-info')

    def test_quote_requests_past_rate_are_throttled(self):
        for symbol in ("AAPL", "MSFT"):
            response = self.client.get(self.quote_url, {'symbol': symbol})
            self.assertEqual(response.status_code, status.HTTP_200_OK)

        response = self.client.get(self.quote_url, {'symbol': 'GOOG'})
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)
        self.assertGreater(int(response['Retry-After']), 0)
        self.assertEqual(self.mock_quote.call_count, 2)

    def test_asset_info_shares_quote_throttle(self):
        self.assertEqual(self.client.get(self.quote_url, {'symbol': 'AAPL'}).status_code, status.HTTP_200_OK)
        self.assertEqual(self.client.get(self.asset_info_url, {'tickers': 'AAPL'}).status_code, status.HTTP_200_OK)

        response = self.client.get(self.asset_info_url, {'tickers': 'AAPL'})
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)

    def test_spoofed_xff_prefix_cannot_bypass_throttle(self):
        for i, symbol in enumerate(("AAPL", "MSFT")):
            response = self.client.get(
                self.quote_url,
                {'symbol': symbol},
                HTTP_X_FORWARDED_FOR=f"10.0.0.{i}, 203.0.113.5",
            )
            self.assertEqual(response.status_code, status.HTTP_200_OK)

        response = self.client.get(
            self.quote_url,
            {'symbol': 'GOOG'},
            HTTP_X_FORWARDED_FOR="10.0.0.99, 203.0.113.5",
        )
        self.assertEqual(response.status_code, status.HTTP_429_TOO_MANY_REQUESTS)

    def test_throttle_is_per_client_ip(self):
        for symbol in ("AAPL", "MSFT"):
            self.assertEqual(
                self.client.get(
                    self.quote_url, {'symbol': symbol}, HTTP_X_FORWARDED_FOR="203.0.113.5"
                ).status_code,
                status.HTTP_200_OK,
            )

        response = self.client.get(
            self.quote_url, {'symbol': 'GOOG'}, HTTP_X_FORWARDED_FOR="203.0.113.6"
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)


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
