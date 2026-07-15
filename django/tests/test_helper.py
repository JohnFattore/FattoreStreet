from unittest.mock import patch, MagicMock
from decimal import Decimal
from datetime import date, datetime

from django.core.cache import cache
from django.test import TestCase, override_settings
import pandas as pd

from portfolio.helper import (
    QuoteFetchError,
    _partition_cached,
    get_historical_dividends,
    get_historical_prices,
    get_historical_splits,
    get_market_reference_dates,
    get_quarterly_data,
    get_realtime_price,
    get_yfinance_data,
    is_market_open,
    percent_change,
)

# Tests run with DummyCache (DEBUG=True); override with a real backend when
# asserting cache behavior.
LOCMEM_CACHES = {"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}}


def _mock_tickers(ticker_mocks: dict):
    """Build a fake yf.Tickers return value from {ticker: MagicMock}."""
    return MagicMock(tickers=ticker_mocks)


@override_settings(CACHES=LOCMEM_CACHES)
class LocMemCacheTestCase(TestCase):
    def setUp(self):
        super().setUp()
        cache.clear()
        self.addCleanup(cache.clear)


class PartitionCachedTests(LocMemCacheTestCase):
    """Tests for the _partition_cached cache-splitting helper."""

    def test_splits_cached_and_uncached(self):
        cache.set("k_AAPL", {"2025-01-13": Decimal("95.00")})
        cached, uncached = _partition_cached(["AAPL", "MSFT"], lambda t: f"k_{t}")
        self.assertEqual(cached, {"AAPL": {"2025-01-13": Decimal("95.00")}})
        self.assertEqual(uncached, ["MSFT"])

    def test_all_uncached_when_cache_empty(self):
        cached, uncached = _partition_cached(["AAPL", "MSFT"], lambda t: f"k_{t}")
        self.assertEqual(cached, {})
        self.assertEqual(uncached, ["AAPL", "MSFT"])


class HistoricalPricesHelperTest(LocMemCacheTestCase):
    """Tests for yfinance historical price extraction."""

    def _history_df(self, columns: dict) -> pd.DataFrame:
        return pd.DataFrame(columns, index=pd.to_datetime(["2025-01-13", "2025-01-14"]))

    @patch("portfolio.helper.yf.Tickers")
    def test_uses_adjusted_close_when_available(self, mock_tickers):
        mock_ticker = MagicMock()
        mock_ticker.history.return_value = self._history_df({
            "Close": [Decimal("100.00"), Decimal("101.00")],
            "Adj Close": [Decimal("95.00"), Decimal("96.00")],
        })
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_prices(["AAPL"])

        self.assertEqual(result["AAPL"]["2025-01-13"], Decimal("95.00"))
        self.assertEqual(result["AAPL"]["2025-01-14"], Decimal("96.00"))

    @patch("portfolio.helper.yf.Tickers")
    def test_falls_back_to_close_column(self, mock_tickers):
        mock_ticker = MagicMock()
        mock_ticker.history.return_value = self._history_df({
            "Close": [Decimal("100.00"), Decimal("101.00")],
        })
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_prices(["AAPL"])

        self.assertEqual(result["AAPL"]["2025-01-13"], Decimal("100.00"))

    @patch("portfolio.helper.yf.Tickers")
    def test_second_call_served_from_cache(self, mock_tickers):
        mock_ticker = MagicMock()
        mock_ticker.history.return_value = self._history_df({
            "Close": [Decimal("100.00"), Decimal("101.00")],
        })
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        first = get_historical_prices(["AAPL"])
        second = get_historical_prices(["AAPL"])

        # The function still constructs yf.Tickers("") on a full cache hit,
        # so assert on history() calls rather than the constructor.
        self.assertEqual(mock_ticker.history.call_count, 1)
        self.assertEqual(first, second)


class HistoricalDividendsHelperTest(LocMemCacheTestCase):
    """Tests for yfinance historical dividend extraction."""

    @patch("portfolio.helper.yf.Tickers")
    def test_reads_dividend_series(self, mock_tickers):
        series = pd.Series(
            [Decimal("0.25"), Decimal("0.26")],
            index=pd.to_datetime(["2025-02-10", "2025-05-12"]),
        )
        mock_ticker = MagicMock()
        mock_ticker.dividends = series
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_dividends(["AAPL"])

        self.assertEqual(result["AAPL"]["2025-02-10"], Decimal("0.25"))
        self.assertEqual(result["AAPL"]["2025-05-12"], Decimal("0.26"))

    @patch("portfolio.helper.yf.Tickers")
    def test_empty_series_returns_empty_dict(self, mock_tickers):
        mock_ticker = MagicMock()
        mock_ticker.dividends = pd.Series(dtype="float64")
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_dividends(["AAPL"])

        self.assertEqual(result["AAPL"], {})

    @patch("portfolio.helper.yf.Tickers")
    def test_zero_values_filtered_out(self, mock_tickers):
        series = pd.Series(
            [Decimal("0"), Decimal("0.25")],
            index=pd.to_datetime(["2025-02-10", "2025-05-12"]),
        )
        mock_ticker = MagicMock()
        mock_ticker.dividends = series
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_dividends(["AAPL"])

        self.assertEqual(result["AAPL"], {"2025-05-12": Decimal("0.25")})


class HistoricalSplitsHelperTest(LocMemCacheTestCase):
    """Tests for yfinance historical split extraction."""

    @patch("portfolio.helper.yf.Tickers")
    def test_reads_split_series(self, mock_tickers):
        series = pd.Series(
            [Decimal("4"), Decimal("7")],
            index=pd.to_datetime(["2020-08-31", "2014-06-09"]),
        )
        mock_ticker = MagicMock()
        mock_ticker.splits = series
        mock_tickers.return_value = _mock_tickers({"AAPL": mock_ticker})

        result = get_historical_splits(["AAPL"])

        self.assertEqual(result["AAPL"]["2020-08-31"], Decimal("4"))
        self.assertEqual(result["AAPL"]["2014-06-09"], Decimal("7"))


@patch("portfolio.helper.env", MagicMock(return_value="test-key"))
class RealtimePriceTests(LocMemCacheTestCase):
    """Tests for the Finnhub realtime quote helper."""

    @patch("portfolio.helper.requests.get")
    def test_returns_price_and_percent_change(self, mock_get):
        mock_get.return_value.json.return_value = {"c": 178.72, "dp": 1.2}

        result = get_realtime_price("AAPL")

        self.assertEqual(result["price"], Decimal("178.72"))
        self.assertEqual(result["percent_change_daily"], Decimal("1.2") / 100)

    @patch("portfolio.helper.requests.get")
    def test_raises_quote_fetch_error_when_dp_missing(self, mock_get):
        mock_get.return_value.json.return_value = {"c": 0, "dp": None}

        with self.assertRaises(QuoteFetchError):
            get_realtime_price("FAKE")
        self.assertIsNone(cache.get("current_quote_FAKE"))

    @patch("portfolio.helper.requests.get")
    def test_second_call_served_from_cache(self, mock_get):
        mock_get.return_value.json.return_value = {"c": 178.72, "dp": 1.2}

        get_realtime_price("AAPL")
        get_realtime_price("AAPL")

        mock_get.assert_called_once()


class YfinanceDataTests(LocMemCacheTestCase):
    """Tests for the yfinance company-info helper."""

    def _get(self, info: dict, quarterly_financials=None) -> dict:
        mock_ticker = MagicMock()
        mock_ticker.info = info
        if quarterly_financials is not None:
            mock_ticker.quarterly_financials = quarterly_financials
        with patch("portfolio.helper.yf.Tickers", return_value=_mock_tickers({"AAPL": mock_ticker})):
            return get_yfinance_data(["AAPL"])["AAPL"]

    def test_equity_sums_last_four_quarters_only(self):
        financials = pd.DataFrame(
            [[10.0, 20.0, 30.0, 40.0, 999.0], [100.0, 200.0, 300.0, 400.0, 999.0]],
            index=["Net Income", "Total Revenue"],
            columns=pd.to_datetime(["2025-03-31", "2024-12-31", "2024-09-30", "2024-06-30", "2024-03-31"]),
        )
        result = self._get(
            {"quoteType": "EQUITY", "shortName": "Apple", "marketCap": 3_000_000},
            financials,
        )

        self.assertEqual(result["market_cap"], 3_000_000)
        self.assertEqual(result["net_income"], 100.0)
        self.assertEqual(result["total_revenue"], 1000.0)

    def test_equity_missing_rows_default_to_zero(self):
        result = self._get({"quoteType": "EQUITY"}, pd.DataFrame())

        self.assertEqual(result["net_income"], 0)
        self.assertEqual(result["total_revenue"], 0)

    def test_etf_computes_expense_ratio(self):
        result = self._get({"quoteType": "ETF", "netExpenseRatio": 3.0})

        self.assertEqual(result["expenseRatio"], 0.03)
        self.assertNotIn("net_income", result)

    def test_dividend_yield_defaults_to_zero(self):
        result = self._get({"quoteType": "ETF", "netExpenseRatio": 3.0})

        self.assertEqual(result["dividend_yield"], 0)

    def test_missing_info_fields_use_fallbacks(self):
        result = self._get({})

        self.assertEqual(result["short_name"], "AAPL")
        self.assertEqual(result["type"], "N/A")


class MarketCalendarTests(TestCase):
    """Tests for NYSE calendar helpers (deterministic, no network)."""

    def test_reference_dates_align_to_prior_trading_days(self):
        # 2024-07-04 and 2024-01-01 are NYSE holidays
        result = get_market_reference_dates(datetime(2024, 7, 5))

        self.assertEqual(result, {
            "yesterday": date(2024, 7, 3),
            "1_week_ago": date(2024, 6, 28),
            "1_month_ago": date(2024, 6, 5),
            "year_to_date": date(2023, 12, 29),
            "1_year_ago": date(2023, 7, 6),
            "3_years_ago": date(2021, 7, 6),
            "5_years_ago": date(2019, 7, 5),
        })

    def test_is_market_open(self):
        self.assertTrue(is_market_open(date(2024, 7, 3)))
        self.assertFalse(is_market_open(date(2024, 7, 4)))  # Independence Day
        self.assertFalse(is_market_open(date(2024, 7, 6)))  # Saturday


class PercentChangeTests(TestCase):
    def test_computes_fractional_change(self):
        self.assertEqual(percent_change(Decimal("110"), Decimal("100")), Decimal("0.1"))

    def test_returns_na_when_no_historical_price(self):
        self.assertEqual(percent_change(Decimal("110"), None), "N/A")


class QuarterlyDataTests(LocMemCacheTestCase):
    """Tests for quarterly financial statement extraction."""

    def _get(self, income_stmt, balance_sheet=None, cashflow=None):
        mock_ticker = MagicMock()
        mock_ticker.quarterly_income_stmt = income_stmt
        mock_ticker.quarterly_balance_sheet = balance_sheet if balance_sheet is not None else pd.DataFrame()
        mock_ticker.quarterly_cashflow = cashflow if cashflow is not None else pd.DataFrame()
        with patch("portfolio.helper.yf.Ticker", return_value=mock_ticker):
            return get_quarterly_data("AAPL")

    def test_builds_records_with_field_mapping(self):
        income = pd.DataFrame(
            {pd.Timestamp("2024-03-31"): [1000.0, 100.0]},
            index=["Total Revenue", "Net Income"],
        )
        balance = pd.DataFrame(
            {pd.Timestamp("2024-03-31"): [5000.0]},
            index=["Total Assets"],
        )

        result = self._get(income, balance)

        self.assertEqual(len(result), 1)
        record = result[0]
        self.assertEqual(record["year"], 2024)
        self.assertEqual(record["quarter"], 1)
        self.assertEqual(record["periodEnd"], "2024-03-31")
        self.assertEqual(record["revenues"], 1000.0)
        self.assertIsInstance(record["revenues"], float)  # numpy converted via .item()
        self.assertEqual(record["assets"], 5000.0)
        # Empty cashflow statement leaves cashflow fields as None
        self.assertIsNone(record["netCashProvidedByUsedInOperatingActivities"])

    def test_falls_back_to_alternate_row_labels(self):
        income = pd.DataFrame(
            {pd.Timestamp("2024-03-31"): [1000.0]},
            index=["Revenue"],  # second label in _INCOME_STMT_FIELDS["revenues"]
        )

        result = self._get(income)

        self.assertEqual(result[0]["revenues"], 1000.0)

    def test_excludes_pre_2008_quarters(self):
        income = pd.DataFrame(
            {
                pd.Timestamp("2007-12-31"): [900.0],
                pd.Timestamp("2024-03-31"): [1000.0],
            },
            index=["Total Revenue"],
        )

        result = self._get(income)

        self.assertEqual([r["periodEnd"] for r in result], ["2024-03-31"])

    def test_cached_result_short_circuits(self):
        cache.set("quarterly_data_AAPL", [])

        with patch("portfolio.helper.yf.Ticker") as mock_ticker_cls:
            result = get_quarterly_data("AAPL")

        self.assertEqual(result, [])
        mock_ticker_cls.assert_not_called()
