# Django: dead-code removal + test additions + test cleanup

## Context

A coverage run (77 tests, 80% lines) showed Django's gaps are concentrated in `portfolio/helper.py` (39%), a handful of files at 0% that turn out to be **dead code** (no references anywhere in the repo), the entertainment app (zero tests), and `users/admin.py`/`users/services.py`. The user approved: delete the dead code (fits the current `remove-django-dead-code` branch), add tests for `portfolio/helper.py`, entertainment, and users, and clean up the existing test layout. Celery-task tests were explicitly descoped.

Conventions come from `.cursor/skills/django-tests/SKILL.md`: unittest + DRF `APITestCase` + `unittest.mock`, tests in `django/tests/test_<app>.py`, extend `BaseAPITestCase` (`django/tests/base.py`), mock all externals, test auth'd + unauth'd paths.

**Critical environment fact**: with `DEBUG=True`, `mysite/settings.py:113-119` sets `CACHES` to **DummyCache**, so any test asserting cache behavior must use `@override_settings(CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}})` plus `cache.clear()` in `setUp` and `addCleanup`. Also `portfolio/helper.py` calls `env("FINNHUB_API_KEY")`/`env("FRED_API_KEY")` with no default — patch `portfolio.helper.env` in those tests.

## Step 1 — Delete dead code

Delete (all verified unreferenced):
- `django/portfolio/EDGAR.py`, `django/portfolio/delete.py`, `django/portfolio/choices.py`
- `django/portfolio/management/` (entire dir — its only commands `migrate_assets_to_account.py`, `list_cache_keys.py` are dead)
- `django/restaurants/matrixFactorization.py`, `django/restaurants/matrixFactorModel.pth`, `django/restaurants/management/` (only `populate_restaurants.py`)
- Remove commented import at `django/restaurants/views.py:6`

No `pyproject.toml` changes needed (torch was never a declared dep; matrixFactorization was already un-importable). Note for user: `django/outliers2024-09-09.csv` is also unreferenced — flag but don't delete (out of approved scope).

## Step 2 — Test-layout cleanup

- **`tests/base.py`**: add `MarketDataPatchMixin` — patches `portfolio.serializers.get_historical_prices` (side_effect building from a class-level `mock_prices` dict) and `portfolio.serializers.is_market_open` (True), started in `setUp` with `self.addCleanup(patch.stopall)`. Mixin listed **before** `BaseAPITestCase` in bases; its `setUp` calls `super().setUp()` first.
- **`tests/test_portfolio.py`**: apply the mixin to the three classes that repeat that patch pair (setUps around lines 67-69, 128-130, 155-157). Move the three helper test classes (`Historical{Prices,Dividends,Splits}HelperTest`, ~lines 279-352) into the new `tests/test_helper.py`; drop now-unused imports.
- **`tests/test_account.py`**: `TestCase` → `BaseAPITestCase`, use `self.user` instead of hand-rolled user.
- **`tests/test_blog.py`**: `APITestCase` → `BaseAPITestCase`.
- **changeflow**: port the 2 real tests from `changeflow/tests.py` (`ChangeRequestAPITests`: create + list at `/changeflow/api/requests/`) into `tests/test_changeflow.py` using `self.authenticate_client()`; add an unauthenticated test (viewset is `IsAuthenticated` — expect 401 with JWT auth; assert actual). Delete `changeflow/tests.py`.
- Move `test_deactivation_service_marks_user_inactive` from `tests/test_changeflow.py:61-71` into `tests/test_users.py` (it tests `users/services.py`).
- Delete empty stubs: `entertainment/tests.py`, `portfolio/tests.py`, `restaurants/tests.py`, `users/tests.py` (discovery of `django/tests/` unaffected).

## Step 3 — New `tests/test_helper.py`

New file (test_portfolio.py is already 468 lines and endpoint-focused). Houses the 3 moved classes + new ones. Reuse the existing fake pattern: `@patch("portfolio.helper.yf.Tickers")` with `MagicMock(tickers={"AAPL": mock_ticker})`. Module constant `LOCMEM_CACHES`.

| Class | Tests (patch targets) |
|---|---|
| `PartitionCachedTests` | cached/uncached split; all-uncached on empty cache (LocMem override) |
| `HistoricalPricesCacheTests` | cache hit skips second `.history()` call (assert `history.call_count == 1` — note: full-hit path still constructs `yf.Tickers("")`, so don't assert Tickers uncalled); `Close` fallback when no `Adj Close`; dividends: empty series → `{}`, zero/None values filtered |
| `RealtimePriceTests` | patch `portfolio.helper.env` + `portfolio.helper.requests.get`: happy path (`{"c": 178.72, "dp": 1.2}` → Decimal price + dp/100); `dp: None` → `QuoteFetchError` and nothing cached; second call served from 60s cache |
| `YfinanceDataTests` | EQUITY branch sums last 4 quarters only (5-column `quarterly_financials` DataFrame with `pd.Timestamp` columns, rows `Net Income`/`Total Revenue`); missing rows → 0; ETF branch `netExpenseRatio` 3.0 → `expenseRatio` 0.03 and no `net_income` key; `dividend_yield` defaults to 0 |
| `FredDataTests` | patch env + requests.get; payload includes `"."` value (→ NaN → dropped): parses/sorts/drops-NaN and strips `realtime_*`; `compute_yoy=True` sends `params["units"] == "pc1"` (absent otherwise); second call cached |
| `MarketCalendarTests` | **no mocks** (pandas_market_calendars is offline-deterministic): `get_market_reference_dates(datetime(2024, 7, 5))` — pin exact expected dates by running the function locally first (July 4 + Jan 1 are holidays); `is_market_open`: 2024-07-03 True, 2024-07-04 False, 2024-07-06 (Sat) False |
| `PercentChangeTests` | `(110, 100)` → `Decimal("0.1")`; `None` historical → `"N/A"` |
| `QuarterlyDataTests` | `@patch("portfolio.helper.yf.Ticker")` (singular): field mapping from income/balance DataFrames + empty cashflow → cashflow fields None, `year/quarter/periodEnd` correct, values converted to native `float` (assert `isinstance`); label fallback (`"Revenue"` populates `revenues`); pre-2008 columns excluded; `cache.set(..., [])` short-circuits (`cached is not None`) with `Ticker` never called |
| `AllUsTickersTests` | `@patch("portfolio.helper.pd.read_csv")` `side_effect=[df1, df2]`: combines + dedupes `Symbol`/`ACT Symbol`; exception → `ConnectionError` |

## Step 4 — New `tests/test_entertainment.py`

View is public, renders `entertainment/recommendation_list.html`; `settings.HOME_URL` defaults fine. Use `reverse("entertainment:recommendation_list")`.

- `RecommendationListViewTests`: 200 + `assertTemplateUsed`; groups keyed by **Type label** with empty groups dropped (view: `entertainment/views.py:10-17`); group order follows `Type.choices`; recs sorted by `title` within group (view overrides model ordering); `home_url` in context; empty DB → `groups == {}`.
- `RecommendationModelTests`: `IntegrityError` on duplicate (type, title, artist) — wrap in `transaction.atomic()`; same title + different type allowed; default ordering `-created_at`; `__str__` (assert against actual implementation in `entertainment/models.py`).

## Step 5 — Extend `tests/test_users.py`

- `UserServiceTests` (absorbs the moved deactivation test): `deactivate_user` returns the user and persists `is_active=False`; only-updates-is_active proof — set `first_name` in memory without saving, call service, `refresh_from_db()` → `first_name` unchanged (verifies `update_fields=["is_active"]`, `users/services.py:6`).
- `UserAdminTests`: instantiate `DeactivationFirstUserAdmin(User, AdminSite())` (fresh site, avoids global registry); request via `self.factory.get(...)` with `request.user = superuser`. Tests: `deactivate_selected_users` action deactivates queryset; `reactivate_selected_users` reactivates; `get_actions` lacks `delete_selected` but has both custom actions (`users/admin.py:26-29`); `has_delete_permission` False with and without `obj`.

## Verification

```bash
cd /Users/spike/GitHub/FattoreStreet/django
uv run python manage.py check
uv run python manage.py test          # expect ~115+ tests, all passing (was 77)
uv run --with coverage coverage run --source=. manage.py test && uv run --with coverage coverage report
# expect: helper.py 39% → ~90%, entertainment/views.py 45% → 100%, users/admin.py 67% → 100%, overall 80% → high 80s
grep -rn "matrixFactor|EDGAR|list_cache_keys|migrate_assets_to_account|populate_restaurants" -E --include="*.py" .   # → empty
```

## Gotchas

- DummyCache under tests makes cache assertions pass vacuously — always override to LocMemCache and `cache.clear()` in setUp + `addCleanup`.
- `quarterly_*` DataFrame fakes need `pd.Timestamp` columns (cutoff compare) and exact row-index labels from `_INCOME_STMT_FIELDS` etc.
- Compute market-calendar expected dates by running the real functions once — don't guess holiday adjustments.
- MRO: `class X(MarketDataPatchMixin, BaseAPITestCase)` with mixin `setUp` calling `super().setUp()` first.
