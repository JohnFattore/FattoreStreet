# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FattoreStreet is a full-stack financial portfolio and social platform. It's a monorepo with three deployable services behind an Nginx reverse proxy:

| Component | Directory | Technology |
|-----------|-----------|------------|
| Frontend | `react-app/` | React 18, TypeScript, Vite, Redux Toolkit (RTK Query) |
| Backend API | `django/` | Django 5, DRF, Celery, Redis, SimpleJWT |
| SEC Microservice | `springboot/` | Spring Boot 4.1, Java 17, Spring Data JPA |
| Local AI | `llm/` | llama.cpp (Qwen2.5-7B), stable-diffusion.cpp, Kokoro TTS |

## Commands

### React (`react-app/`)
```bash
npm run dev          # Dev server (port 5173)
npm run staging      # Staging mode
npm run build        # TypeScript check + Vite build
npm run lint         # ESLint (max-warnings=0, strict)
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
uv run celery -A mysite worker --beat -E -n beat # Celery worker + scheduler
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
- `portfolio/` — Asset & account CRUD, yfinance price data, FRED economic data, quarterly financials
- `users/` — Registration, JWT tokens
- `chatbot/` — Boglehead AI advisor (Google Gemini)
- `restaurants/` — Restaurant reviews/recommendations
- `changeflow/` — Changelog + feedback tickets
- `blog/` — Blog posts with categories and tags
- `entertainment/` — Media recommendations (books, movies, shows, music, podcasts, games, websites)

**Async Tasks (Celery)**: `load_fred_cache`, `load_yfinance_cache`, `load_iex_hist` — scheduled via django-celery-beat.

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

Per-language conventions live in the cursor rules (single source of truth). Per-service `CLAUDE.md` files import them directly. Key rules are also listed in the code-review checklist at `.claude/commands/code-review.md`.

## Behavior Rules

@.cursor/rules/auto-update-tests.mdc

@.cursor/rules/auto-update-docs.mdc

@.cursor/rules/data-licensing-commercial-free.mdc

## Shared Skills & Commands

Claude Code and Cursor share skills/commands via cross-references. When creating a new command or skill:

- **New Claude Code command** (`.claude/commands/foo.md`): also create `.cursor/skills/foo/SKILL.md` with frontmatter + `@.claude/commands/foo.md`
- **New Claude Code agent** (`.claude/agents/foo.md`): also create `.cursor/skills/foo/SKILL.md` with frontmatter + `@.claude/agents/foo.md`
- **New Cursor skill** (`.cursor/skills/foo/SKILL.md`): also create `.claude/commands/foo.md` referencing the shared content

Content lives in one place; the other tool gets a pointer file. Never duplicate the content.

## Environment Variables

### Django (`.env` in `django/`)
- `SECRET_KEY` — required
- `DEBUG` — bool, default `True`
- `DATABASE` — required: `postgresLocal`, `postgresDocker`, or any other value (e.g. `sqlite`) for SQLite; unset raises at startup
- `POSTGRES_PASSWORD` — required when `DATABASE=postgresDocker`
- `REDIS_URL` — always required (Celery broker; also the cache backend when `DEBUG=False`)
- `GOOGLE_API_KEY` — Gemini key for the chatbot app
- `FINNHUB_API_KEY`, `FRED_API_KEY` — portfolio quotes and FRED economic data
- `SEC_CONTACT_EMAIL` — email for SEC API User-Agent header (required by SEC)
- `SPRINGBOOT_BASE_URL` — internal Spring Boot base URL for the `load_iex_hist` Celery task (no nginx prefix, e.g. `http://springboot:8080`)
- `DJANGO_FORCE_SCRIPT_NAME` — set to `/django` when served behind the nginx prefix

### Spring Boot (`.env` in `springboot/`, auto-imported)
- `DB_URL`, `DB_USERNAME`, `POSTGRES_PASSWORD` — PostgreSQL connection (`POSTGRES_PASSWORD` is the shared DB-password key used by Django and the postgres image too)
- `SECRET_KEY` — must match Django `SECRET_KEY` for JWT verification on admin routes (`app.django-jwt-secret` defaults to this value)
- `LLM_SERVER_URL` — llama.cpp server (default `http://localhost:8081`)
- `DJANGO_PORTFOLIO_BASE_URL` — Django base URL for validation calls
- `SEC_CONTACT_EMAIL` — email for SEC API User-Agent header (required by SEC)

### React (`.env.*` per mode)
- `VITE_APP_DJANGO_URL` — Django base URL (`http://127.0.0.1:8000/` in dev, `https://fattorestreet.com/django/` in production)
- `VITE_APP_SPRINGBOOT_URL` — Spring Boot base URL
- `VITE_APP_FINNHUB_URL` — Finnhub API base URL

Committed modes: `development`, `production`, `test`, `compose` (for the local nginx compose stack).
