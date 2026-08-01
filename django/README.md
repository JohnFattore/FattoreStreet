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
| `blog` | Public blog posts (Markdown body) via `/blog/api/posts/`; content lives as Markdown in `blog/journal/` and `blog/learning-topics/` (see [Blog posts](#blog-posts)) |

Market index membership APIs live in the Spring Boot service (`sec-api`), not in Django.

If you still have legacy `indexes_*` tables in PostgreSQL from an older deploy, drop them after migrating traffic to Spring (optional one-time):

```sql
DROP TABLE IF EXISTS indexes_historicalindexmember CASCADE;
DROP TABLE IF EXISTS indexes_historicalstock CASCADE;
DROP TABLE IF EXISTS indexes_indexmember CASCADE;
DROP TABLE IF EXISTS indexes_stock CASCADE;
```

## Blog posts

Blog content is Markdown in the repo, not rows typed into the admin. Two directories, both shipped inside the image:

| Directory | Contents | Category | Author |
|-----------|----------|----------|--------|
| `blog/journal/` | The author's own posts | whatever front matter sets | left as-is |
| `blog/learning-topics/` | Daily study topics, one per GitHub issue, filenames prefixed with the issue number | `LLM Notebook`, applied automatically | `claude`, applied automatically |

The `claude` account is a byline, not a login: the import creates it on first run with an unusable password, and reuses it (never rewriting its password) afterwards.

`deploy/deploy.sh` runs `sync_blog_posts` on every deploy, so merging to `main` publishes. To run it by hand:

```bash
uv run python manage.py sync_blog_posts --dry-run   # report changes, write nothing
uv run python manage.py sync_blog_posts             # import
uv run python manage.py sync_blog_posts --path DIR --category NAME --author USER  # one-off import
```

Posts are matched on **slug**, which is derived from the filename (`CLAUDE_CODE.md` → `claude-code`) with a learning topic's leading issue number stripped (`111_THE_JWT_TRUST_BOUNDARY.md` → `the-jwt-trust-boundary`). Re-running updates in place; it never duplicates, never deletes, never clears or moves `published_at`, and never touches a post that has no file behind it. Two files resolving to one slug is an error raised before anything is written.

Publication date comes from the version stamp under the title (`_FattoreStreet @ [`sha`](…) — 2026-07-19_`), which is what makes a re-import a genuine no-op. `--publish` only affects posts that have neither a stamp nor a `published_at`.

Front matter is optional and fenced by `---`. Supported keys: `title`, `slug`, `excerpt`, `categories`, `tags`, `published_at`, `cover_image_url`; anything else is an error. Without it, the title comes from the first `#` heading and the excerpt from the first real paragraph (the version stamp and source lines are skipped). Use `slug:` to pin a post whose filename does not match the slug it is already published under.

The format itself is owned by the `blog-editor` skill (`.claude/skills/blog-editor/`).

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
- Docker Compose stack and deploy scripts in `deploy/`
- Hosted on AWS EC2 (Graviton/ARM64)
- Django admin: with `DJANGO_FORCE_SCRIPT_NAME=/django` (matching nginx `location /django/`), use **`/django/admin/`** in the browser; local dev without that env continues to use **`/admin/`**.

## Documentation

- [API Reference](../docs/API_REFERENCE.md) (covers all endpoints for Django and Spring Boot)

## User Deactivation Policy

- User accounts should be deactivated (`is_active=False`) rather than hard-deleted in normal operations.
- Django admin is configured to prioritize deactivate/reactivate actions and prevent user hard deletes.
- User-linked records (including changeflow tickets) remain associated with the user while deactivated.
