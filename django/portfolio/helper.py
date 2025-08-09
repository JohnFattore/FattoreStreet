import yfinance as yf
from datetime import date, timedelta, datetime
from decimal import Decimal
from django.core.cache import cache
from typing import Optional
import requests
import environ
import pandas_market_calendars as mcal

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
        output = {"price": Decimal(price), "percent_change": 0}
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
        output = {"price": Decimal(quote["c"]), "percent_change": Decimal(quote["dp"]/100)}
        cache.set(cache_key, output, timeout=60)
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