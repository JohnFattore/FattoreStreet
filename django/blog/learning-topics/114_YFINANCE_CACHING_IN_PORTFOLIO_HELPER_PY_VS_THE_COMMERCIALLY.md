# yfinance caching in portfolio/helper.py vs. the commercially-free data-licensing rule

_FattoreStreet @ [`f337c3fe`](https://github.com/JohnFattore/FattoreStreet/tree/f337c3fef734f52b9a5a4a696997a3960944a332) — 2026-07-19_

_Source: [#114](https://github.com/JohnFattore/FattoreStreet/issues/114)_

## Overview

`django/portfolio/helper.py` is the Django app's "no task queue" external-data layer: instead of Celery workers or scheduled jobs, every function fetches lazily on first request and memoizes the result in Django's cache framework (`django.core.cache.cache`), following the `_partition_cached` split-cached/uncached pattern documented in `.claude/rules/django-drf.md`. That's a clean, low-infrastructure caching design worth understanding on its own. But tracing it all the way through surfaces a real tension with another repo rule: `.claude/rules/data-licensing-commercial-free.md` states yfinance may **only** be used for dev-only, ephemeral verification — never persisted, never returned from an API, never rendered in the UI — and calls out "caches that act like persistence" (which Redis, used here, explicitly is) as covered by that constraint. In production (`DEBUG=False`), `mysite/settings.py` swaps the cache backend from a no-op `DummyCache` to a real `django_redis.cache.RedisCache`, and several `AllowAny` (unauthenticated, public) views in `portfolio/views.py` return `helper.py`'s yfinance-derived values directly in JSON responses. This is exactly the "case in point" this rule is trying to prevent, and it's worth deeply understanding both why the caching pattern was built this way and where it now conflicts with the licensing rule the codebase itself insists on.

## Files to read

- `django/portfolio/helper.py` (322 lines, read the whole thing) — especially:
  - Lines 26-44 (`_partition_cached`): the shared split-cached/uncached-tickers helper every function below calls
  - Lines 46-63 (`get_historical_prices`): `cache.set(..., timeout=60 * 60 * 24)` at line 61 — 24-hour persistence of yfinance OHLCV history
  - Lines 66-87 (`get_historical_dividends`) and 90-111 (`get_historical_splits`): same 24h cache pattern (lines 85, 109) for yfinance dividend/split series
  - Lines 130-164 (`get_yfinance_data`): company financials/name/market-cap/net-income pulled from `yfinance.tickers[ticker].info` and `quarterly_financials`, cached 24h at line 162
  - Lines 267-322 (`get_quarterly_data`): full quarterly income statement/balance sheet/cashflow from yfinance, cached 24h at line 321
  - Line 113-128 (`get_realtime_price`) for contrast: this one calls Finnhub, not yfinance, and only caches for 60 seconds — a good baseline to compare timeout choices against
- `django/mysite/settings.py` lines 108-124: the `if DEBUG` / `else` `CACHES` block — `DummyCache` (a no-op) in dev, real `django_redis.cache.RedisCache` backed by `REDIS_URL` in production. This is the switch that turns `cache.set(...)` in `helper.py` from "does nothing locally" into "genuinely persists in Redis for a day" in production.
- `django/portfolio/views.py` lines 80-198 — the views that call the yfinance-backed helpers and return their output as the HTTP response body: `QuoteRetrieveView` (line 88), `AssetInfoRetrieveView` (lines 108-136, financials + historical prices merged into one payload), `AssetHistoricalPricesRetrieveView` (line 150), `AssetHistoricalDividendsRetrieveView` (line 165), `AssetHistoricalSplitsRetrieveView` (line 180), `QuarterlyDataRetrieveView` (line 194). Note lines 81-82, 97-98, 144-145, 158-159, 173-174, 187-188: every one of these is `authentication_classes = []` / `permission_classes = [AllowAny]` — publicly reachable, no login required.
- `.claude/rules/data-licensing-commercial-free.md` (the whole file, it's short) — the non-negotiable constraint, the "yfinance exception (development only)" section, and specifically the line "Never... Store yfinance-sourced data... Return yfinance-sourced values from APIs... Render yfinance-sourced values in the UI."
- `.claude/rules/django-drf.md` — "No task queue: external-data helpers fetch lazily and cache in Redis (django-redis) when `DEBUG=False`" — the rule that describes the very pattern that conflicts with the licensing rule above.
- `springboot/deploy/terraform/main.tf` (skim, e.g. the EventBridge Scheduler section) as a point of contrast: the repo's commercially-free equivalents (SEC EDGAR fundamentals, IEX daily prices) are ingested and persisted in Postgres by Spring Boot on a schedule, not fetched live from a non-free provider on every user request.

## Questions to work through while reading

1. Walk through `AssetHistoricalPricesRetrieveView.get()` end to end for a ticker nobody has queried in the last 24 hours, in production. Where exactly does yfinance data first get written to Redis, and where does it get read back out and serialized into the HTTP response? At what point, if any, does this violate "Never store yfinance-sourced data" and "Never return yfinance-sourced values from APIs"?
2. `get_realtime_price` (Finnhub, 60s cache) and `get_historical_prices`/`get_yfinance_data`/`get_quarterly_data` (yfinance, 24h cache) sit side by side in the same file with the same `_partition_cached` pattern. Why might it have been easy to add the yfinance-backed functions the same way, "following the existing pattern," without the licensing distinction being obvious from the code itself?
3. The data-licensing rule explicitly says caches "that act like persistence" count as persistence. Is a 24-hour Redis TTL "ephemeral" in the sense the yfinance exception intends, or is it exactly the "persisted and displayed to end users" case the rule is written to prevent? Where would you draw the line (a 5-second cache? 60 seconds, like the Finnhub quote? never persisting at all and forcing DEBUG-gated behavior)?
4. If these endpoints needed to keep working without yfinance, what commercially-free replacements does the repo already have for each: real-time/recent price (compare to Spring Boot's `DailyPrice`/IEX ingest), dividends and splits (compare to `CorporateAction`/`corporateaction` detection from SEC filings), and quarterly financials (compare to Spring Boot's `Quarter` entity from `fundamentals/EdgarService`)? What's the actual gap between what those Spring Boot endpoints expose today and what `AssetInfoRetrieveView`/`QuarterlyDataRetrieveView` need?
5. `.claude/rules/data-licensing-commercial-free.md` says yfinance is fine for "verification/diagnostics in development" gated to `DEBUG` or similar. Is `if DEBUG: DummyCache` in `settings.py` actually equivalent to a "dev-only gate" on the yfinance *calls themselves* in `helper.py`, or does it only change caching behavior while the yfinance calls and their return values reaching the API/UI still happen unconditionally in both dev and prod?

## Primer: lazy-caching external data, and why the caching layer and the licensing boundary are different concerns

"Fetch lazily, cache the result" is a simple and effective way to avoid hammering a slow or rate-limited third-party API without standing up a background worker: the first request for a given key pays the latency cost and populates the cache; every subsequent request within the TTL is served from memory/Redis instead. This is exactly what `_partition_cached` plus `cache.get`/`cache.set` implements here, and it's a good pattern in general.

But a caching layer answers "how fresh does this data need to be, and how expensive is it to refetch?" — it says nothing about "am I allowed to store or show this data at all?" Those are orthogonal questions, and a data-licensing rule ("commercially-free only, persistence and UI both") constrains the *second* question regardless of how short or long a TTL is on the first. A cache with a 24-hour TTL is, from a licensing perspective, indistinguishable from a database table that's re-truncated and reloaded daily: for the 24-hour window, the non-free data is stored and served to end users. The fix for a licensing conflict is never "shorten the TTL" — it's "don't source this value from that provider for anything end users can see," which usually means substituting a commercially-free provider (as this repo already does elsewhere, e.g. IEX for prices, SEC EDGAR for fundamentals and corporate actions) or truly gating the non-free path to something that never reaches persistence or an API response (a local script, a `DEBUG`-only code path that itself never writes to cache/db and never gets `Response()`-returned).

## External references

- Django cache framework (per-view/low-level cache API, backends): https://docs.djangoproject.com/en/5.0/topics/cache/
- django-redis configuration: https://github.com/jazzband/django-redis
- SEC EDGAR company facts API (a commercially-free alternative data source already used elsewhere in this repo via Spring Boot's `EdgarService`): https://www.sec.gov/edgar/sec-api-documentation

## Exercise (optional)

Pick one function in `helper.py` — say `get_historical_dividends` — and sketch (in a scratch file, no need to actually change `helper.py`) what it would take to replace its yfinance call with a query against Spring Boot's existing `CorporateAction` data (via an HTTP call to a springboot endpoint, similar to how `DJANGO_PORTFOLIO_BASE_URL` is used in the other direction). Note what's straightforward (splits/dividends are already modeled as `CorporateAction` rows) and what's missing (e.g. does an endpoint already expose per-ticker corporate-action history to Django, or would one need to be added?).
