import logging
from urllib.parse import urljoin

import requests
from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
from rest_framework_simplejwt.tokens import AccessToken

from .helper import get_all_us_tickers, get_fred_data, get_yfinance_data

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
def load_iex_hist(days: int = 20) -> dict:
    base = (settings.SPRINGBOOT_BASE_URL or "").strip().rstrip("/")
    if not base:
        raise ValueError("SPRINGBOOT_BASE_URL is not set")

    User = get_user_model()
    admin_user = User.objects.get(pk=1)

    url = urljoin(base + "/", "admin/load-hist")
    token = str(AccessToken.for_user(admin_user))
    headers = {"Authorization": f"Bearer {token}"}
    timeout = getattr(settings, "SPRINGBOOT_REQUEST_TIMEOUT", 10800)

    response = requests.get(
        url,
        params={"days": days},
        headers=headers,
        timeout=timeout,
    )
    response.raise_for_status()

    try:
        body = response.json()
    except ValueError:
        body = (response.text or "")[:500]

    return {
        "status_code": response.status_code,
        "days": days,
        "body": body,
    }