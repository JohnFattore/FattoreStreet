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
| `portfolio` | Asset & account CRUD, yfinance prices/info, FRED data, quarterly financials |
| `users` | User registration, JWT token management |
| `chatbot` | Boglehead AI financial advisor (Google Gemini) |
| `restaurants` | Restaurant reviews and recommendations |
| `indexes` | Market index tracking |
| `changeflow` | Changelog / change tracking |

## Celery Tasks

| Task | Schedule | Description |
|------|----------|-------------|
| `load_fred_cache` | Periodic | Pre-caches FRED economic series (DGS10, CPI, UNRATE, etc.) |
| `load_yfinance_cache` | Periodic | Pre-caches yfinance data for all portfolio tickers |
| `load_iex_hist` | Periodic | Triggers Spring Boot IEX HIST TOPS download (default 5 days) |
| `refresh_corporate_actions` | Periodic | Triggers Spring Boot price adjustments (splits & dividends) |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SECRET_KEY` | (required) | Django secret key |
| `DEBUG` | `True` | Debug mode |
| `DATABASE` | (required) | `postgresLocal` or `postgresDocker` |
| `POSTGRES_PASSWORD` | (required) | PostgreSQL password |
| `REDIS_URL` | (required) | Redis connection URL (e.g. `redis://localhost:6379`) |
| `SPRINGBOOT_INTERNAL_URL` | `http://springboot:8080` | Spring Boot base URL for Celery tasks |
| `ADMIN_API_KEY` | `spike` | Key for Spring Boot `X-Admin-Key` header |

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
