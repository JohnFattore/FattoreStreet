# Django Backend API

Django 5 + Django REST Framework backend providing portfolio management, market data, and supporting services.

## Stack

- Python 3, Django 5.0, Django REST Framework
- PostgreSQL (via psycopg2)
- Celery + Redis (async tasks & scheduling)
- django-celery-beat (database-driven periodic tasks)
- django-redis (caching layer)
- SimpleJWT (authentication)
- yfinance (market data)
- FRED API (macroeconomic data)
- Google Generative AI (chatbot)

## Apps

| App | Purpose |
|-----|---------|
| `portfolio` | Asset & account CRUD, yfinance adjusted-close prices/dividends/splits/info, FRED data, quarterly financials |
| `users` | User registration, JWT token management |
| `chatbot` | Boglehead AI financial advisor (Google Gemini) |
| `restaurants` | Restaurant reviews and recommendations |
| `changeflow` | Changelog plus authenticated feedback ticket intake (`POST /changeflow/api/tickets/`) |
| `blog` | Public blog posts (Markdown body) via `/blog/api/posts/` |

Market index membership APIs live in the Spring Boot service (`sec-api`), not in Django.

If you still have legacy `indexes_*` tables in PostgreSQL from an older deploy, drop them after migrating traffic to Spring (optional one-time):

```sql
DROP TABLE IF EXISTS indexes_historicalindexmember CASCADE;
DROP TABLE IF EXISTS indexes_historicalstock CASCADE;
DROP TABLE IF EXISTS indexes_indexmember CASCADE;
DROP TABLE IF EXISTS indexes_stock CASCADE;
```

## Celery Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| `load_fred_cache` | Periodic | Pre-caches FRED economic series (DGS10, CPI, UNRATE, etc.) |
| `load_yfinance_cache` | Periodic | Pre-caches yfinance data for all portfolio tickers |
| `load_iex_hist` | Optional periodic | Calls Spring Boot `GET /admin/load-hist` (IEX HIST daily price ingest). Requires `SPRINGBOOT_BASE_URL` and Django user `pk=1` for admin JWT. Add a django-celery-beat periodic task pointing at `portfolio.tasks.load_iex_hist`; optional kwargs e.g. `{"days": 20}`. |

Other Spring Boot admin jobs (price adjustments, corporate actions, etc.) are not covered by this task; run them manually or automate separately. If django-celery-beat has a stale entry for `portfolio.tasks.refresh_corporate_actions`, remove it in Django admin if that task no longer exists.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SECRET_KEY` | (required) | Django secret key |
| `DEBUG` | `True` | Debug mode |
| `DATABASE` | (required) | `postgresLocal` or `postgresDocker` |
| `POSTGRES_PASSWORD` | (required) | PostgreSQL password |
| `REDIS_URL` | (required) | Redis connection URL (e.g. `redis://localhost:6379`) |
| `SPRINGBOOT_BASE_URL` | (empty) | Internal Spring Boot base URL for Celery triggers (no `/springboot/` prefix), e.g. `http://springboot:8080`. Required for `load_iex_hist`. |
| `SPRINGBOOT_REQUEST_TIMEOUT` | `600` | HTTP timeout in seconds for `load_iex_hist` (IEX ingest can be slow). |

## Getting Started

### Prerequisites

- Python 3.10+
- PostgreSQL
- Redis

### Install Dependencies

```bash
pip install -r requirements.txt
```

### Database Migrations

```bash
python3 manage.py makemigrations
python3 manage.py migrate
```

### Run Dev Server

```bash
python3 manage.py runserver
```

### Run Celery Worker + Beat

```bash
docker run -d -p 6379:6379 redis
celery -A mysite worker --beat -E -n beat
```

### Run Tests

```bash
python3 manage.py test
```

## Production

- WSGI via Gunicorn
- Nginx reverse proxy
- PostgreSQL database
- All services run in Docker containers
- Kubernetes manifests in `kubernetes/`
- Hosted on AWS EC2

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## User Deactivation Policy

- User accounts should be deactivated (`is_active=False`) rather than hard-deleted in normal operations.
- Django admin is configured to prioritize deactivate/reactivate actions and prevent user hard deletes.
- User-linked records (including changeflow tickets) remain associated with the user while deactivated.
