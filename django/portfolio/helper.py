import yfinance as yf
from datetime import date, timedelta, datetime
from decimal import Decimal
from django.core.cache import cache
import requests
import environ
import pandas_market_calendars as mcal
import pandas as pd
#from .choices import ASSET_TYPES, EXCHANGES, MARKETS

# Initialise environment variables
env = environ.Env()
environ.Env.read_env()

def get_historical_prices(tickers: list[str]):
    now = datetime.today() - timedelta(hours=7)
    start = (now - timedelta(days=367 * 25)).strftime("%Y-%m-%d") # how far back should i try to go?
    end = (now + timedelta(days=1)).strftime("%Y-%m-%d")
    prices = {}
    uncached_tickers = []
    for ticker in tickers:
        cache_key = f"historical_prices_{ticker}_start:_{start}_end:_{end}"
        cached_data = cache.get(cache_key)
        if cached_data:
            prices[ticker] = cached_data
        else:
            uncached_tickers.append(ticker)
    yfinance = yf.Tickers(" ".join(uncached_tickers))
    for ticker in uncached_tickers:
        data = yfinance.tickers[ticker].history(start=start, end=end)
        ticker_prices = data[["Close"]]
        ticker_prices = {idx.strftime("%Y-%m-%d"): Decimal(row["Close"]) for idx, row in ticker_prices.iterrows()}
        cache.set(cache_key, ticker_prices, timeout=60 * 60 * 24)
        prices[ticker] = ticker_prices
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

def get_yfinance_data(tickers: list[str]):
    financials = {}
    # only add non cached tickers in ticker list
    uncached_tickers = []
    for ticker in tickers:
        cache_key = f"financials_{ticker}"
        cached_data = cache.get(cache_key)
        if cached_data:
            financials[ticker] = cached_data
        else:
            uncached_tickers.append(ticker)
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
            
        cache_key = f"financials_{ticker}"
        cache.set(cache_key, financial_data, timeout=60 * 60 * 24)
        financials[ticker] = financial_data
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
    
def get_all_us_tickers():
    # NASDAQ listed
    nasdaq_url = "ftp://ftp.nasdaqtrader.com/SymbolDirectory/nasdaqlisted.txt"
    nasdaq_data = pd.read_csv(nasdaq_url, sep="|")
    nasdaq_tickers = nasdaq_data['Symbol'].tolist()

    # NYSE listed
    nyse_url = "ftp://ftp.nasdaqtrader.com/SymbolDirectory/otherlisted.txt"
    nyse_data = pd.read_csv(nyse_url, sep="|")
    nyse_tickers = nyse_data['ACT Symbol'].tolist()

    # Combine and remove duplicates
    all_tickers = list(set(nasdaq_tickers + nyse_tickers))
    return all_tickers
