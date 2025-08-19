from celery import shared_task
from .helper import get_yfinance_data, get_fred_data

@shared_task
def load_fred_cache():
    series_ids = [
    { "series_id": "DGS10", "compute_yoy": False },
    { "series_id": "CPIAUCSL", "compute_yoy": True },
    { "series_id": "UNRATE", "compute_yoy": False },
    { "series_id": "DTWEXBGS", "compute_yoy": True },
    { "series_id": "FEDFUNDS", "compute_yoy": False },
    { "series_id": "GDP", "compute_yoy": True }]
    for series_id, compute_yoy in series_ids:
        get_fred_data(series_id, compute_yoy)

@shared_task
def load_yfinance_cache():
    tickers = ["AAPL", "MSFT", "VTI", "VOO", "V"]
    for ticker in tickers:
        get_yfinance_data(ticker)