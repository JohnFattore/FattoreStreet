import logging

from celery import shared_task
from .helper import get_yfinance_data, get_fred_data, get_all_us_tickers

logger = logging.getLogger(__name__)

@shared_task
def load_fred_cache():
    series_ids = [
        {"series_id": "DGS10", "compute_yoy": False},
        {"series_id": "CPIAUCSL", "compute_yoy": True},
        {"series_id": "UNRATE", "compute_yoy": False},
        {"series_id": "DTWEXBGS", "compute_yoy": True},
        {"series_id": "FEDFUNDS", "compute_yoy": False},
        {"series_id": "GDP", "compute_yoy": True},
    ]
    for item in series_ids:
        try:
            get_fred_data(item["series_id"], item["compute_yoy"])
            logger.info(f"Cached FRED series {item['series_id']}")
        except Exception as e:
            logger.error(f"Failed to cache FRED series {item['series_id']}: {e}")

@shared_task
def load_yfinance_cache():
    tickers = get_all_us_tickers()
    logger.info(f"Loading yfinance cache for {len(tickers)} tickers")
    batch_size = 10
    batches = [tickers[i:i + batch_size] for i in range(0, len(tickers), batch_size)]
    for batch in batches:
        try:
            get_yfinance_data(batch)
            logger.info(f"Cached yfinance batch: {batch}")
        except Exception as e:
            logger.error(f"yfinance batch {batch} errored: {e}")