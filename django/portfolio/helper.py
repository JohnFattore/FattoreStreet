import yfinance as yf
from datetime import date, timedelta, datetime
from decimal import Decimal
from django.core.cache import cache
from typing import Optional
import requests
import environ
import pandas_market_calendars as mcal
import pandas as pd
from .choices import ASSET_TYPES, EXCHANGES, MARKETS

# Initialise environment variables
env = environ.Env()
environ.Env.read_env()

def get_ticker_price(ticker: str, date: Optional[date] = None):
    if date:
        yfinance = yf.Ticker(ticker)
        cache_key = f"historical_quote_{ticker}_{date}"
        cached_data = cache.get(cache_key)

        if cached_data:
            return cached_data
        
        data = yfinance.history(start=date.strftime("%Y-%m-%d"), end=(date + timedelta(days=1)).strftime("%Y-%m-%d"))
        price = data['Close'].get(date.strftime("%Y-%m-%d"), None)
        if price == None:
            raise Exception(f"No Price for ticker {ticker} on day {date}")
        output = {"price": Decimal(price), "percent_change_daily": 0}
        cache.set(cache_key, output, timeout=60 * 60 * 24)
        return output
    else:
        cache_key = f"current_quote_{ticker}_{date}"
        cached_data = cache.get(cache_key)

        if cached_data:
            return cached_data
        api_key = env("FINNHUB_API_KEY")
        url = f"https://finnhub.io/api/v1/quote?symbol={ticker}&token={api_key}"
        response = requests.get(url)
        quote = response.json()
        output = {"price": Decimal(quote["c"]), "percent_change_daily": Decimal(quote["dp"]/100)}
        cache.set(cache_key, output, timeout=60)
        return output

def get_yfinance_data(ticker: str):
    cache_key = f"financials_{ticker}"# also check date, should update if the historical dates are out of date
    cached_data = cache.get(cache_key)
    if cached_data:
        return cached_data
    dates = get_market_reference_dates()
    yfinance = yf.Ticker(ticker)
    info = yfinance.info
    market = info["market"]
    if market not in {m[0] for m in MARKETS}:
        raise Exception(f"Market {market} not recognized")

    type = info["quoteType"]
    if type not in {t[0] for t in ASSET_TYPES}:
        raise Exception(f"type {type} not recognized")

    exchange = info["fullExchangeName"]

    if exchange in {"NasdaqGS", "NasdaqGM", "NasdaqCM"}:
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

    for label, date in dates.items():
        try:
            price = get_ticker_price(ticker, date)["price"]
        except:
            price = 0
        financials[label] = price

    if info["quoteType"] == "EQUITY":
        # dividend yield, forward PE
        quarterly_financials = yfinance.quarterly_financials
        financials["market_cap"] = info["marketCap"]
        financials["net_income"] = quarterly_financials.loc["Net Income"].iloc[:4].sum()
        financials["total_revenue"] = quarterly_financials.loc["Total Revenue"].iloc[:4].sum()

    elif info["quoteType"] == "ETF":
        financials["market_cap"] = 0
        financials["ttm_pe"] = 0
        if info.get("marketCap") != None:
            financials["market_cap"] = info["marketCap"]
        financials["expenseRatio"] = info["netExpenseRatio"] / 100
        if info.get("ttm_pe") != None:
            financials["ttm_pe"] = info["trailingPE"]

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

def get_market_reference_dates(reference_date: datetime = None):
    if reference_date is None:
        reference_date = datetime.today()

    checkpoints = {
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