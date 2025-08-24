import yfinance as yf
from datetime import date, timedelta, datetime
from decimal import Decimal
from django.core.cache import cache
import requests
import environ
import pandas_market_calendars as mcal
import pandas as pd
from .choices import ASSET_TYPES, EXCHANGES, MARKETS

# Initialise environment variables
env = environ.Env()
environ.Env.read_env()

# should really just keep data in cache
def get_historical_prices(ticker: str):
    now = datetime.today() - timedelta(hours=7)
    start = (now - timedelta(days=367 * 5)).strftime("%Y-%m-%d")
    end = (now + timedelta(days=1)).strftime("%Y-%m-%d")
    cache_key = f"historical_prices_{ticker}_start:_{start}_end_{end}"
    cached_data = cache.get(cache_key)
    if cached_data:
        prices = cached_data
    else:
        yfinance = yf.Ticker(ticker)
        data = yfinance.history(start=start, end=end)
        prices = data[["Close"]]
        prices = {idx.strftime("%Y-%m-%d"): Decimal(row["Close"]) for idx, row in prices.iterrows()}
        cache.set(cache_key, prices, timeout=60 * 60 * 24)
    return prices

def get_realtime_price(ticker: str):
    cache_key = f"current_quote_{ticker}"
    cached_data = cache.get(cache_key)

    if cached_data:
        return cached_data
    api_key = env("FINNHUB_API_KEY")
    url = f"https://finnhub.io/api/v1/quote?symbol={ticker}&token={api_key}"
    response = requests.get(url)
    quote = response.json()
    if response.status_code >= 400 or not quote["dp"]:
        raise Exception(f"No real time quote for {ticker}")
    output = {"price": Decimal(quote["c"]), "percent_change_daily": Decimal(quote["dp"]/100)}
    cache.set(cache_key, output, timeout=60)
    return output

def get_yfinance_data(ticker: str):
    cache_key = f"financials_{ticker}"
    cached_data = cache.get(cache_key)
    if cached_data:
        return cached_data
    yfinance = yf.Ticker(ticker)
    info = yfinance.info
    market = info["market"]
    if market not in {m[0] for m in MARKETS}:
        raise Exception(f"Market {market} not recognized")

    type = info["quoteType"]
    if type not in {t[0] for t in ASSET_TYPES}:
        raise Exception(f"type {type} not recognized")

    exchange = info["fullExchangeName"]

    if exchange in {"NasdaqGS", "NasdaqGM", "NasdaqCM", "Nasdaq"}:
        exchange = "NASDAQ"
    elif exchange in {"NYSEArca"}:
        exchange = "NYSE"

    if exchange not in {e[0] for e in EXCHANGES}:
        raise Exception(f"exchange {exchange} not recognized")

    financials = {
        "ticker": ticker,
        "short_name": info["shortName"],
        "long_name": info["longName"],
        "type": type,
        "market": market,
        "exchange": exchange
    }

    if info["quoteType"] == "EQUITY":
        # dividend yield, forward 
        financials["display_name"] =  info["displayName"]
        if info.get("dividendYield", None):
            financials["dividendYield"] = info["dividendYield"]
        else:
            financials["dividendYield"] = 0
        quarterly_financials = yfinance.quarterly_financials
        financials["market_cap"] = info["marketCap"]
        financials["net_income"] = quarterly_financials.loc["Net Income"].iloc[:4].sum()
        financials["total_revenue"] = quarterly_financials.loc["Total Revenue"].iloc[:4].sum()

    elif info["quoteType"] in ("ETF", "MUTUALFUND"):
        financials["expenseRatio"] = info["netExpenseRatio"] / 100

    cache.set(cache_key, financials, timeout=60 * 60 * 24)
    return financials

def get_fred_data(series_id: str, compute_yoy: bool = False):
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
    del df["realtime_start"]
    del df["realtime_end"]
    df = df.sort_values("date").reset_index(drop=True)

    df = df.dropna(subset=["value"])
    output = df.to_dict(orient="records")
    cache.set(cache_key, output, timeout=60 * 60 * 24)
    return output

def get_market_reference_dates(reference_date: datetime = None):
    def get_last_market_day_on_or_before(date: datetime) -> datetime:
        nyse = mcal.get_calendar('NYSE')
        schedule = nyse.valid_days(
            start_date=(date - timedelta(days=10)).strftime('%Y-%m-%d'),
            end_date=date.strftime('%Y-%m-%d')
        )
        if not schedule.empty:
            return schedule[-1].date()
        else:
            raise ValueError(f"No valid trading days found before {date.date()}")
    if reference_date is None:
        reference_date = datetime.today() - timedelta(hours=7)

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
        label: get_last_market_day_on_or_before(date)
        for label, date in checkpoints.items()
    }

    return adjusted

def is_market_open(date: datetime):
    nyse = mcal.get_calendar('NYSE')
    schedule = nyse.schedule(start_date=date, end_date=date)
    return not schedule.empty

def percent_change(current_price: Decimal, historical_price: Decimal):
    if historical_price:
        return (current_price - historical_price) / historical_price
    else:
        return "N/A"