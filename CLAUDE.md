# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FattoreStreet is a full-stack financial portfolio and social platform. It's a monorepo with three deployable services behind an Nginx reverse proxy:

| Component | Directory | Technology |
|-----------|-----------|------------|
| Frontend | `react-app/` | React 18, TypeScript, Vite, Redux Toolkit (RTK Query) |
| Backend API | `django/` | Django 5, DRF, Redis cache, SimpleJWT |
| SEC Microservice | `springboot/` | Spring Boot 4.1, Java 17, Spring Data JPA |
| Local AI | `llm/` | llama.cpp (Qwen2.5-7B), stable-diffusion.cpp, Kokoro TTS |

## Commands

### React (`react-app/`)
```bash
npm run dev          # Dev server (port 5173)
npm run staging      # Staging mode
npm run build        # TypeScript check + Vite build
npm run lint         # ESLint (max-warnings=0, strict)
npm run lint:styles  # Stylelint (css/scss, standard-scss config)
npm run format:check # Prettier check (CI); npm run format rewrites in place
npx vitest --run     # Run tests once
npm run test         # Watch mode with UI + coverage
npx vitest run --reporter=verbose  # Verbose test output
# Single test: npx vitest run --reporter=verbose -t "test name"
```

### Django (`django/`)
```bash
uv sync                                          # Install deps into .venv (from uv.lock)
uv run python manage.py runserver                # Dev server (port 8000)
uv run python manage.py migrate                  # Apply migrations
uv run python manage.py makemigrations           # Generate migrations
uv run python manage.py test                     # All tests
uv run python manage.py test tests.test_users    # Specific file
uv run python manage.py test tests.test_users.TestUserAPI.test_login  # Specific test
uv run python manage.py sync_blog_posts --dry-run # Import docs/blog-posts/*.md into blog.Post
uv add <package>                                 # Add dependency (pyproject.toml + uv.lock)
```

### Spring Boot (`springboot/`)
```bash
mvn spring-boot:run                              # Run locally (port 8080)
mvn test                                         # All tests
mvn test -Dtest=MainControllerTest               # Specific class
mvn test -Dtest=MainControllerTest#testGetQuarters  # Specific method
mvn clean test                                   # Clean + test
```

### CI
GitHub Actions (`.github/workflows/ci.yml`) runs on pushes and PRs to `main`: React lint (ESLint) + style lint (Stylelint) + format check (Prettier) + Sass compile + build + tests, Django tests, Spring Boot format check (Spotless) + lint (Checkstyle) + `mvn verify` (tests + coverage floor + SpotBugs/FindSecBugs + PMD + Error Prone), and a detect-secrets scan (`pre-commit run detect-secrets --all-files`, config in `.pre-commit-config.yaml`). All four must pass before merge. `docker-build.yml` builds the nginx/django/springboot images (build-only on PRs; pushes to `main` publish to GHCR tagged `latest` + commit SHA).

## Architecture

### Request Flow
```
Browser → Nginx (443 SSL)
  /             → React static files (SPA, try_files → index.html)
  /django/      → Django (port 8000)       [prefix stripped]
  /springboot/  → Spring Boot (port 8080)  [prefix stripped]
  /pgadmin4/    → pgAdmin                  [prefix stripped]
  /static/      → Django collected static files
```

Django mounts each app under its own prefix (`users/`, `portfolio/`, `restaurants/`, `chatbot/`, `changeflow/`, `blog/`, `entertainment/`, plus `admin/`), with API routes at `<app>/api/...` — e.g. `POST /django/users/api/token/` from the browser reaches Django as `POST /users/api/token/`. Set `DJANGO_FORCE_SCRIPT_NAME=/django` in production so redirects and `{% url %}` include the prefix.

### Authentication
- Django issues SimpleJWT Access + Refresh tokens via `POST /users/api/token/`
- React stores tokens in Redux (redux-persist), Axios interceptor adds `Authorization: Bearer <token>`
- RTK Query base query handles automatic 401 refresh
- Spring Boot admin endpoints require `Authorization: Bearer` with a Django SimpleJWT access token; JWT signing uses the same `SECRET_KEY` as Django, and only `user_id` claim `1` is allowed for `/admin/**`

### Django Apps
- `portfolio/` — Asset & account CRUD, yfinance price data, quarterly financials
- `users/` — Registration, JWT tokens
- `chatbot/` — Boglehead AI advisor (Google Gemini)
- `restaurants/` — Restaurant reviews/recommendations
- `changeflow/` — Changelog + feedback tickets
- `blog/` — Blog posts with categories and tags; Markdown in `docs/blog-posts/` is the source of truth, imported into `Post` rows by `manage.py sync_blog_posts` (drafts unless `--publish` or front matter `published_at`)
- `entertainment/` — Media recommendations (books, movies, shows, music, podcasts, games, websites)

**Scheduled jobs**: the IEX HIST daily price ingest runs as a Fargate one-shot task on an EventBridge Scheduler cron (see `springboot/deploy/terraform/`), not inside Django; after a successful load the same task runs corporate-action price adjustment (`PriceAdjustmentService.adjustAllTickers`), which re-detects SEC actions for the stalest ~1/7 of tickers per night (rolling weekly refresh via `listings.last_sec_detection_at`) plus any ticker with a >25% overnight move. External-data helpers (`portfolio/helper.py`) cache lazily in Redis on first request.

### Spring Boot Packages
```
com.fattorestreet.sec_api/
  controller/        REST endpoints
  client/            SEC HTTP client (WebService)
  fundamentals/      EdgarService, quarterly financials, financial ratios
  corporateaction/   Dividends/splits detection, price adjustment
    support/         Parsers, extractors, persisters
  listing/           Assets, listings, ETF identity enrichment
  filing/            10-K MD&A fetch + LLM summarization
  marketdata/        Daily prices, IEX HIST binary ingest
  economic/          FRED economic data client + in-memory cache
  index/             Index membership, metrics refresh
  repository/        Spring Data JPA repositories
  model/             JPA entities
  config/, util/     Shared helpers
```
Test classes mirror source under `src/test/java/.../sec_api/`.

### Spring Boot Key Entities
- `Asset` — SEC company (cik, isFund)
- `Listing` — Ticker → Asset mapping
- `Quarter` — Quarterly financials (revenues, netIncome, OCF, EPS, etc.)
- `DailyPrice` — IEX OHLCV with adjusted prices
- `CorporateAction` — Splits/dividends (SPLIT/DIVIDEND, effectiveDate, ratio, sourceType)
- `FilingSummary` — LLM-generated 10-K summaries
- `MarketIndex`, `IndexMember`, `ListingIndexMetrics` — Index infrastructure

### React State & API
- All API calls use **RTK Query** via `src/functions/api/` (`djangoApi.ts`, `springbootApi.ts`, shared `baseQuery.ts`); the only intentional exception is the raw axios calls in `src/pages/Admin.tsx`
- Client-only state lives in `createSlice` reducers in `src/reducers/` (`user` for JWT tokens + dark mode, `location`, `watchList`, `adminSuccessBar`); the `user` slice captures tokens from the `login`/`refreshLogin` mutations via `extraReducers` matchers
- 401 handling is a single global axios response interceptor in `src/App.tsx` that dispatches the `refreshLogin` mutation and retries — it covers RTK Query (whose baseQuery uses global axios) and Admin.tsx alike
- API responses use snake_case (Django convention); RTK Query transforms to camelCase for components
- All pages protected by `<StateHandler>` for loading/error display

## Conventions

Per-language conventions live in `.claude/rules/` (single source of truth), scoped to their service via `paths:` frontmatter and auto-loaded when matching files are touched. Repo-wide behavior rules (auto-update tests/docs, data licensing) live there too, without frontmatter, and load every session. Key rules are also listed in the code-review checklist at `.claude/skills/code-review/SKILL.md`.

Additional conventions:
- No Hungarian notation
- Python follows PEP 8; TypeScript strict mode is enabled
- camelCase in frontend, snake_case in Django; RTK Query `transformResponse` converts between them
- Infra: Docker Compose stacks and deploy scripts in `deploy/` (images on GHCR, deployed via AWS SSM), Nginx in `nginx/`, Terraform for the one-shot Fargate price load in `springboot/deploy/terraform/`

## Skills & Rules

- Skills (invocable workflows): `.claude/skills/<name>/SKILL.md` with `name`/`description` frontmatter
- Rules (always-on or path-scoped conventions): `.claude/rules/<topic>.md`; add a `paths:` frontmatter list of globs to scope a rule to matching files, omit frontmatter for rules that apply every session

## Environment Variables

### Django (`.env` in `django/`)
- `SECRET_KEY` — required
- `DEBUG` — bool, default `True`
- `DATABASE` — required: `postgresLocal`, `postgresDocker`, or any other value (e.g. `sqlite`) for SQLite; unset raises at startup
- `POSTGRES_PASSWORD` — required when `DATABASE=postgresDocker`
- `REDIS_URL` — cache backend, required when `DEBUG=False`
- `GOOGLE_API_KEY` — Gemini key for the chatbot app
- `FINNHUB_API_KEY` — portfolio quotes
- `SEC_CONTACT_EMAIL` — email for SEC API User-Agent header (required by SEC)
- `DJANGO_FORCE_SCRIPT_NAME` — set to `/django` when served behind the nginx prefix
- `BLOG_POSTS_DIR` — Markdown source directory for `manage.py sync_blog_posts` (defaults to `docs/blog-posts/`)

### Spring Boot (`.env` in `springboot/`, auto-imported)
- `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` — PostgreSQL connection (`POSTGRES_PASSWORD` is the shared DB-password key used by Django and the postgres image too)
- `SECRET_KEY` — must match Django `SECRET_KEY` for JWT verification on admin routes (`app.django-jwt-secret` defaults to this value)
- `LLM_SERVER_URL` — llama.cpp server (default `http://localhost:8081`)
- `DJANGO_PORTFOLIO_BASE_URL` — Django base URL for validation calls
- `SEC_CONTACT_EMAIL` — email for SEC API User-Agent header (required by SEC)
- `FRED_API_KEY` — FRED key for the public `POST /fred-data` economic data endpoint

### React (`.env.*` per mode)
- `VITE_APP_DJANGO_URL` — Django base URL (`http://127.0.0.1:8000/` in dev, `https://fattorestreet.com/django/` in production)
- `VITE_APP_SPRINGBOOT_URL` — Spring Boot base URL
- `VITE_APP_FINNHUB_URL` — Finnhub API base URL

Committed modes: `development`, `production`, `test`, `compose` (for the local nginx compose stack).
