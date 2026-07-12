import yfinance as yf
from datetime import date, timedelta, datetime
from zoneinfo import ZoneInfo
from decimal import Decimal
from collections.abc import Callable
from django.core.cache import cache
import requests
import environ
import pandas_market_calendars as mcal
import pandas as pd


class QuoteFetchError(Exception):
    """Raised when a real-time quote cannot be retrieved."""
    pass


# Initialise environment variables
env = environ.Env()
environ.Env.read_env()

HISTORY_YEARS = 25
# Extra days per year account for leap years
HISTORY_DAYS = 365 * HISTORY_YEARS + HISTORY_YEARS

def _partition_cached(tickers: list[str], make_cache_key: Callable[[str], str]) -> tuple[dict, list[str]]:
    """Split tickers into cached results and a list of uncached tickers.

    Args:
        tickers: List of ticker symbols to look up.
        make_cache_key: Callable that takes a ticker string and returns its cache key.

    Returns:
        A tuple of (cached_dict, uncached_tickers).
    """
    cached = {}
    uncached = []
    for ticker in tickers:
        data = cache.get(make_cache_key(ticker))
        if data:
            cached[ticker] = data
        else:
            uncached.append(ticker)
    return cached, uncached

def get_historical_prices(tickers: list[str]) -> dict:
    now = datetime.now(ZoneInfo("America/New_York"))
    start = (now - timedelta(days=HISTORY_DAYS)).strftime("%Y-%m-%d")
    end = (now + timedelta(days=1)).strftime("%Y-%m-%d")

    def _cache_key(ticker: str) -> str:
        return f"historical_prices_{ticker}_start:_{start}_end:_{end}"

    prices, uncached_tickers = _partition_cached(tickers, _cache_key)
    yfinance = yf.Tickers(" ".join(uncached_tickers))
    for ticker in uncached_tickers:
        data = yfinance.tickers[ticker].history(start=start, end=end)
        price_column = "Adj Close" if "Adj Close" in data.columns else "Close"
        ticker_prices = data[[price_column]]
        ticker_prices = {idx.strftime("%Y-%m-%d"): Decimal(str(row[price_column])) for idx, row in ticker_prices.iterrows()}
        cache.set(_cache_key(ticker), ticker_prices, timeout=60 * 60 * 24)
        prices[ticker] = ticker_prices
    return prices


def get_historical_dividends(tickers: list[str]) -> dict:
    def _cache_key(ticker: str) -> str:
        return f"historical_dividends_{ticker}"

    dividends, uncached_tickers = _partition_cached(tickers, _cache_key)
    if not uncached_tickers:
        return dividends

    yfinance = yf.Tickers(" ".join(uncached_tickers))
    for ticker in uncached_tickers:
        series = yfinance.tickers[ticker].dividends
        if series is None or series.empty:
            ticker_dividends = {}
        else:
            ticker_dividends = {
                idx.strftime("%Y-%m-%d"): Decimal(str(value))
                for idx, value in series.items()
                if value is not None and value > 0
            }
        cache.set(_cache_key(ticker), ticker_dividends, timeout=60 * 60 * 24)
        dividends[ticker] = ticker_dividends
    return dividends


def get_historical_splits(tickers: list[str]) -> dict:
    def _cache_key(ticker: str) -> str:
        return f"historical_splits_{ticker}"

    splits, uncached_tickers = _partition_cached(tickers, _cache_key)
    if not uncached_tickers:
        return splits

    yfinance = yf.Tickers(" ".join(uncached_tickers))
    for ticker in uncached_tickers:
        series = yfinance.tickers[ticker].splits
        if series is None or series.empty:
            ticker_splits = {}
        else:
            ticker_splits = {
                idx.strftime("%Y-%m-%d"): Decimal(str(value))
                for idx, value in series.items()
                if value is not None and value > 0
            }
        cache.set(_cache_key(ticker), ticker_splits, timeout=60 * 60 * 24)
        splits[ticker] = ticker_splits
    return splits

def get_realtime_price(ticker: str) -> dict:
    cache_key = f"current_quote_{ticker}"
    cached_data = cache.get(cache_key)

    if cached_data:
        return cached_data
    api_key = env("FINNHUB_API_KEY")
    url = f"https://finnhub.io/api/v1/quote?symbol={ticker}&token={api_key}"
    response = requests.get(url)
    response.raise_for_status()
    quote = response.json()
    if quote.get("dp") is None:
        raise QuoteFetchError(f"No real time quote for {ticker}")
    output = {"price": Decimal(str(quote["c"])), "percent_change_daily": Decimal(str(quote["dp"])) / 100}
    cache.set(cache_key, output, timeout=60)
    return output

def get_yfinance_data(tickers: list[str]) -> dict:
    def _cache_key(ticker: str) -> str:
        return f"financials_{ticker}"

    financials, uncached_tickers = _partition_cached(tickers, _cache_key)
    yfinance = yf.Tickers(" ".join(uncached_tickers))
    for ticker in uncached_tickers:
        info = yfinance.tickers[ticker].info

        financial_data = {
            "ticker": ticker,
            "short_name": info.get("shortName", ticker),
            "long_name": info.get("longName", ticker),
            "type": info.get("quoteType", "N/A"),
            "market": info.get("market", "N/A"),
            "exchange": info.get("fullExchangeName", "N/A")
        }
        if info.get("dividendYield", None):
            financial_data["dividend_yield"] = info["dividendYield"]
        else:
            financial_data["dividend_yield"] = 0
        
        quote_type = info.get("quoteType", "")
        if quote_type == "EQUITY":
            quarterly_financials = yfinance.tickers[ticker].quarterly_financials
            financial_data["market_cap"] = info.get("marketCap", 0)
            financial_data["net_income"] = quarterly_financials.loc["Net Income"].iloc[:4].sum() if "Net Income" in quarterly_financials.index else 0
            financial_data["total_revenue"] = quarterly_financials.loc["Total Revenue"].iloc[:4].sum() if "Total Revenue" in quarterly_financials.index else 0

        elif quote_type in ("ETF", "MUTUALFUND"):
            financial_data["expenseRatio"] = info.get("netExpenseRatio", 0) / 100
            
        cache.set(_cache_key(ticker), financial_data, timeout=60 * 60 * 24)
        financials[ticker] = financial_data
    return financials

def get_fred_data(series_id: str, compute_yoy: bool = False) -> list[dict]:
    cache_key = f"FRED_{series_id}_{compute_yoy}"
    cached_data = cache.get(cache_key)
    if cached_data:
        return cached_data
    
    api_key = env("FRED_API_KEY")
    url = f"https://api.stlouisfed.org/fred/series/observations"
    params = {
        "series_id": series_id,
        "api_key": api_key,
        "file_type": "json",
    }
    if compute_yoy:
        params["units"] = "pc1"

    response = requests.get(url, params=params)
    response.raise_for_status()
    data = response.json()["observations"]
    df = pd.DataFrame(data)
    df["date"] = pd.to_datetime(df["date"]).dt.date
    df["value"] = pd.to_numeric(df["value"], errors="coerce")
    df = df.drop(columns=["realtime_start", "realtime_end"])
    df = df.sort_values("date").reset_index(drop=True)

    df = df.dropna(subset=["value"])
    output = df.to_dict(orient="records")
    cache.set(cache_key, output, timeout=60 * 60 * 24)
    return output

_nyse_calendar = None

def _get_nyse_calendar():
    """Lazily instantiate and cache the NYSE calendar at module level."""
    global _nyse_calendar
    if _nyse_calendar is None:
        _nyse_calendar = mcal.get_calendar('NYSE')
    return _nyse_calendar

def get_market_reference_dates(reference_date: datetime = None) -> dict[str, date]:
    nyse = _get_nyse_calendar()

    def get_last_market_day_on_or_before(dt: datetime) -> date:
        schedule = nyse.valid_days(
            start_date=(dt - timedelta(days=10)).strftime('%Y-%m-%d'),
            end_date=dt.strftime('%Y-%m-%d')
        )
        if not schedule.empty:
            return schedule[-1].date()
        else:
            raise ValueError(f"No valid trading days found before {dt.date()}")

    if reference_date is None:
        reference_date = datetime.now(ZoneInfo("America/New_York"))

    checkpoints = {
        "yesterday": reference_date - timedelta(days=1),
        "1_week_ago": reference_date - timedelta(days=7),
        "1_month_ago": reference_date - timedelta(days=30),
        "year_to_date": datetime(reference_date.year, 1, 1),
        "1_year_ago": reference_date - timedelta(days=365),
        "3_years_ago": reference_date - timedelta(days=365 * 3),
        "5_years_ago": reference_date - timedelta(days=365 * 5),
    }

    # Align all checkpoints to the previous trading day
    adjusted = {
        label: get_last_market_day_on_or_before(dt)
        for label, dt in checkpoints.items()
    }

    return adjusted

def is_market_open(check_date: date) -> bool:
    nyse = _get_nyse_calendar()
    schedule = nyse.schedule(start_date=check_date, end_date=check_date)
    return not schedule.empty

def percent_change(current_price: Decimal, historical_price: Decimal) -> Decimal | str:
    if historical_price is not None:
        return (current_price - historical_price) / historical_price
    else:
        return "N/A"
    
_INCOME_STMT_FIELDS = {
    "revenues": ["Total Revenue", "Revenue"],
    "netIncomeLoss": ["Net Income", "Net Income Common Stockholders"],
    "operatingIncomeLoss": ["Operating Income", "Operating Revenue"],
    "grossProfit": ["Gross Profit"],
    "earningsPerShareBasic": ["Basic EPS"],
    "earningsPerShareDiluted": ["Diluted EPS"],
}

_BALANCE_SHEET_FIELDS = {
    "assets": ["Total Assets"],
    "liabilities": ["Total Liabilities Net Minority Interest", "Total Liab"],
    "stockholdersEquity": ["Stockholders Equity", "Total Stockholder Equity"],
    "cashAndCashEquivalentsAtCarryingValue": ["Cash And Cash Equivalents",
                                               "Cash Cash Equivalents And Short Term Investments"],
    "accountsReceivableNetCurrent": ["Accounts Receivable", "Net Receivables", "Receivables"],
    "inventoryNet": ["Inventory"],
}

_CASHFLOW_FIELDS = {
    "netCashProvidedByUsedInOperatingActivities": ["Operating Cash Flow",
                                                    "Total Cash From Operating Activities",
                                                    "Cash Flowsfromusedin Operating Activities Direct"],
    "paymentsOfDividends": ["Common Stock Dividend Paid", "Payment Of Dividends",
                            "Dividends Paid"],
    "paymentsForRepurchaseOfCommonStock": ["Repurchase Of Capital Stock",
                                           "Common Stock Repurchased"],
}

_CUTOFF_DATE = pd.Timestamp("2008-01-01")


def _extract_value(df: pd.DataFrame, col, labels: list[str]):
    """Return the first non-NaN value found for *labels* in *df* at column *col*."""
    for label in labels:
        if label in df.index:
            val = df.at[label, col]
            if pd.notna(val):
                return val
    return None


def _quarter_from_date(dt) -> int:
    """Return fiscal quarter (1-4) from a pandas Timestamp or date."""
    return (dt.month - 1) // 3 + 1


def get_quarterly_data(ticker: str) -> list[dict]:
    cache_key = f"quarterly_data_{ticker}"
    cached = cache.get(cache_key)
    if cached is not None:
        return cached

    t = yf.Ticker(ticker)
    income_stmt = t.quarterly_income_stmt
    balance_sheet = t.quarterly_balance_sheet
    cashflow = t.quarterly_cashflow

    all_dates = set()
    for df in (income_stmt, balance_sheet, cashflow):
        if df is not None and not df.empty:
            all_dates.update(df.columns)

    all_dates = sorted([d for d in all_dates if d >= _CUTOFF_DATE])

    quarters = []
    for dt in all_dates:
        record = {
            "year": dt.year,
            "quarter": _quarter_from_date(dt),
            "periodEnd": dt.strftime("%Y-%m-%d"),
        }

        if income_stmt is not None and not income_stmt.empty and dt in income_stmt.columns:
            for field, labels in _INCOME_STMT_FIELDS.items():
                record[field] = _extract_value(income_stmt, dt, labels)
        else:
            for field in _INCOME_STMT_FIELDS:
                record[field] = None

        if balance_sheet is not None and not balance_sheet.empty and dt in balance_sheet.columns:
            for field, labels in _BALANCE_SHEET_FIELDS.items():
                record[field] = _extract_value(balance_sheet, dt, labels)
        else:
            for field in _BALANCE_SHEET_FIELDS:
                record[field] = None

        if cashflow is not None and not cashflow.empty and dt in cashflow.columns:
            for field, labels in _CASHFLOW_FIELDS.items():
                record[field] = _extract_value(cashflow, dt, labels)
        else:
            for field in _CASHFLOW_FIELDS:
                record[field] = None

        # Convert numpy types to native Python for JSON serialisation
        for key, val in record.items():
            if val is not None and hasattr(val, "item"):
                record[key] = val.item()

        quarters.append(record)

    cache.set(cache_key, quarters, timeout=60 * 60 * 24)
    return quarters
