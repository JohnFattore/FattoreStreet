# Django Backend API

Django 5 + Django REST Framework backend providing portfolio management, market data, and supporting services.

## Stack

- Python 3, Django 5.0, Django REST Framework
- PostgreSQL (via psycopg2)
- django-redis (caching layer, Redis-backed in production)
- SimpleJWT (authentication)
- yfinance (market data)
- Google Generative AI (chatbot)

## Apps

| App | Purpose |
|-----|---------|
| `portfolio` | Asset & account CRUD, yfinance adjusted-close prices/dividends/splits/info, quarterly financials |
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

## Background Jobs & Caching

Django runs no task queue. External-data helpers in `portfolio/helper.py` (yfinance, Finnhub) fetch lazily on the first request and cache the result in Redis (24h TTL for yfinance, 60s for quotes). FRED economic data is served by the Spring Boot service (`POST /fred-data`), not Django.

The IEX HIST daily price ingest is scheduled outside Django: an EventBridge Scheduler cron launches a one-shot Fargate task running Spring Boot in `hist-load` mode (see `springboot/deploy/terraform/`). Other Spring Boot admin jobs (price adjustments, corporate actions, etc.) are run manually.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SECRET_KEY` | (required) | Django secret key |
| `DEBUG` | `True` | Debug mode |
| `DATABASE` | (required) | `postgresLocal` or `postgresDocker` |
| `POSTGRES_PASSWORD` | (required) | PostgreSQL password |
| `REDIS_URL` | (required when `DEBUG=False`) | Redis connection URL for the cache backend (e.g. `redis://localhost:6379`) |
| `DJANGO_FORCE_SCRIPT_NAME` | (empty) | Public URL prefix where nginx serves Django (e.g. `/django`). Set in production so admin login redirects and `{% url %}` resolve under `/django/...`. Omit or leave empty for local `runserver` at `/admin/`. |
| `BLOG_POSTS_DIR` | `../docs/blog-posts` | Directory `sync_blog_posts` reads Markdown posts from. The Docker build context is `django/` alone, so containers must set this (or pass `--path`) to reach a mounted checkout. |

## Getting Started

### Prerequisites

- [uv](https://docs.astral.sh/uv/) (`brew install uv`) — installs Python 3.14 and all dependencies
- PostgreSQL
- Redis

### Install Dependencies

Creates `.venv/` and installs the exact versions from `uv.lock`:

```bash
uv sync
```

### Database Migrations

```bash
uv run python manage.py makemigrations
uv run python manage.py migrate
```

### Run Dev Server

```bash
uv run python manage.py runserver
```

### Run Tests

```bash
uv run python manage.py test
```

### Publish Blog Posts

Blog post copy lives as Markdown in `docs/blog-posts/`; the database is a rendering target. `sync_blog_posts` imports those files into `blog.Post` rows, matching on slug so re-running updates a post in place instead of duplicating it.

```bash
uv run python manage.py sync_blog_posts --dry-run   # report what would change
uv run python manage.py sync_blog_posts             # import as drafts
uv run python manage.py sync_blog_posts --publish   # publish newly created posts
uv run python manage.py sync_blog_posts --path /some/dir
```

Posts are created **unpublished** unless the file sets `published_at` or you pass `--publish`, so an imported post stays invisible to `/blog/api/posts/` until you publish it in the admin. Re-importing never clears an existing `published_at`.

Front matter is optional; without it the title comes from the first `#` heading, the slug from the filename, and the excerpt from the first paragraph. Supported keys:

```markdown
---
title: How RTK Query Refreshes JWTs
slug: rtk-query-401-refresh
excerpt: One-sentence summary shown in the post list.
categories: [Engineering, React]
tags: [rtk-query, jwt]
published_at: 2026-07-25
cover_image_url: https://example.com/cover.png
---

# How RTK Query Refreshes JWTs

Body starts here.
```

Any other key is an error, and unknown categories or tags are created on demand. Because the slug decides create-vs-update, run `--dry-run` first against a database that already has posts — the output prints `<file> -> <slug>` for each file.

### Managing Dependencies

Dependencies are declared in `pyproject.toml` (exact pins) and locked in `uv.lock`.

```bash
uv add <package>        # add a dependency (updates pyproject.toml + uv.lock)
uv remove <package>     # remove a dependency
uv sync                 # sync .venv to the lockfile
```

## Production

- WSGI via Gunicorn
- Nginx reverse proxy
- PostgreSQL database
- All services run in Docker containers
- Kubernetes manifests in `kubernetes/`
- Hosted on AWS EC2
- Django admin: with `DJANGO_FORCE_SCRIPT_NAME=/django` (matching nginx `location /django/`), use **`/django/admin/`** in the browser; local dev without that env continues to use **`/admin/`**.

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## User Deactivation Policy

- User accounts should be deactivated (`is_active=False`) rather than hard-deleted in normal operations.
- Django admin is configured to prioritize deactivate/reactivate actions and prevent user hard deletes.
- User-linked records (including changeflow tickets) remain associated with the user while deactivated.
