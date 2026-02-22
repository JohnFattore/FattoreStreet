import logging

import requests
from celery import shared_task
from django.conf import settings

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
    succeeded = []
    failed = {}
    for item in series_ids:
        try:
            get_fred_data(item["series_id"], item["compute_yoy"])
            succeeded.append(item["series_id"])
            logger.info(f"Cached FRED series {item['series_id']}")
        except Exception as e:
            failed[item["series_id"]] = str(e)
            logger.error(f"Failed to cache FRED series {item['series_id']}: {e}")
    return {"succeeded": succeeded, "failed": failed, "total": len(series_ids)}

@shared_task
def load_yfinance_cache():
    tickers = get_all_us_tickers()
    logger.info(f"Loading yfinance cache for {len(tickers)} tickers")
    batch_size = 10
    batches = [tickers[i:i + batch_size] for i in range(0, len(tickers), batch_size)]
    batches_succeeded = 0
    failed_batches = []
    for batch in batches:
        try:
            get_yfinance_data(batch)
            batches_succeeded += 1
            logger.info(f"Cached yfinance batch: {batch}")
        except Exception as e:
            failed_batches.append({"tickers": batch, "error": str(e)})
            logger.error(f"yfinance batch {batch} errored: {e}")
    return {
        "total_tickers": len(tickers),
        "batch_size": batch_size,
        "batches_succeeded": batches_succeeded,
        "batches_failed": len(failed_batches),
        "failed_batches": failed_batches,
    }

@shared_task
def load_iex_hist(days: int = 5) -> dict:
    base_url = settings.SPRINGBOOT_INTERNAL_URL
    admin_key = settings.ADMIN_API_KEY
    url = f"{base_url}/admin/load-hist"

    logger.info(f"Triggering IEX HIST load for {days} days at {url}")
    try:
        response = requests.get(
            url,
            params={"days": days},
            headers={"X-Admin-Key": admin_key},
            timeout=7200,
        )
        response.raise_for_status()
        result = response.json()
        logger.info(f"IEX HIST load complete: {result}")
        return result
    except requests.RequestException as e:
        logger.error(f"IEX HIST load failed: {e}")
        return {"error": str(e)}


@shared_task
def refresh_corporate_actions(force: bool = False) -> dict:
    base_url = settings.SPRINGBOOT_INTERNAL_URL
    admin_key = settings.ADMIN_API_KEY
    url = f"{base_url}/admin/adjust-prices"

    logger.info(f"Triggering corporate actions refresh (force={force}) at {url}")
    try:
        response = requests.get(
            url,
            params={"force": str(force).lower()},
            headers={"X-Admin-Key": admin_key},
            timeout=7200,
        )
        response.raise_for_status()
        result = response.json()
        logger.info(f"Corporate actions refresh complete: {result}")
        return result
    except requests.RequestException as e:
        logger.error(f"Corporate actions refresh failed: {e}")
        return {"error": str(e)}