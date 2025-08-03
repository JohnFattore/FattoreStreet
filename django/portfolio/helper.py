import yfinance as yf
from datetime import date, timedelta
from .choices import ASSET_TYPES, EXCHANGES, MARKETS
from decimal import Decimal
from django.core.cache import cache
from typing import Optional
import requests
import environ

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