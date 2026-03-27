# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FattoreStreet is a full-stack financial portfolio and social platform. It's a monorepo with three deployable services behind an Nginx reverse proxy:

| Component | Directory | Technology |
|-----------|-----------|------------|
| Frontend | `react-app/` | React 18, TypeScript, Vite, Redux Toolkit (RTK Query) |
| Backend API | `django/` | Django 5, DRF, Celery, Redis, SimpleJWT |
| SEC Microservice | `springboot/` | Spring Boot 3.4, Java 17, Spring Data JPA |
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
python3 manage.py runserver              # Dev server (port 8000)
python3 manage.py migrate                # Apply migrations
python3 manage.py makemigrations         # Generate migrations
python3 manage.py test                   # All tests
python3 manage.py test tests.test_users  # Specific file
python3 manage.py test tests.test_users.TestUserAPI.test_login  # Specific test
celery -A mysite worker --beat -E -n beat  # Celery worker + scheduler
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
  /                     → React static files (SPA)
  /users/api/           → Django (port 8000)
  /portfolio/api/       → Django (port 8000)
  /restaurants/api/     → Django (port 8000)
  /chatbot/api/         → Django (port 8000)
  /admin/               → Django admin
  /springboot/          → Spring Boot (port 8080)  [path rewritten]
```

### Authentication
- Django issues SimpleJWT Access + Refresh tokens via `POST /users/api/token/`
- React stores tokens in Redux (redux-persist), Axios interceptor adds `Authorization: Bearer <token>`
- RTK Query base query handles automatic 401 refresh
- Spring Boot admin endpoints require separate `X-Admin-Key` header

### Django Apps
- `portfolio/` — Asset & account CRUD, yfinance price data, FRED economic data, quarterly financials
- `users/` — Registration, JWT tokens
- `chatbot/` — Boglehead AI advisor (Google Gemini)
- `restaurants/` — Restaurant reviews/recommendations
- `changeflow/` — Changelog + feedback tickets

**Async Tasks (Celery)**: `load_fred_cache`, `load_yfinance_cache`, `load_iex_hist`, `refresh_corporate_actions` — scheduled via django-celery-beat.

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
- All API calls use **RTK Query** via `src/functions/api.ts` with a custom Axios base query
- API responses use snake_case (Django convention); RTK Query transforms to camelCase for components
- Redux slices in `src/reducers/` for auth and portfolio state
- All pages protected by `<StateHandler>` for loading/error display

## Conventions

Per-language conventions live in the cursor rules (single source of truth). Per-service `CLAUDE.md` files import them directly. Key rules are also listed in the code-review checklist at `.claude/commands/code-review.md`.

## Behavior Rules

@.cursor/rules/auto-update-tests.mdc

@.cursor/rules/auto-update-docs.mdc

## Environment Variables

### Django (`.env` in `django/`)
- `SECRET_KEY` — required
- `DEBUG` — bool, default `True`
- `DATABASE` — `postgresLocal`, `postgresDocker`, or omit for SQLite
- `REDIS_URL` — required in production
- `SPRINGBOOT_INTERNAL_URL` — default `http://springboot:8080`
- `ADMIN_API_KEY` — X-Admin-Key for Spring Boot admin endpoints

### Spring Boot (`.env` in `springboot/`, auto-imported)
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL connection
- `ADMIN_API_KEY` — validates incoming X-Admin-Key header
- `LLM_SERVER_URL` — llama.cpp server (default `http://localhost:8081`)
- `DJANGO_PORTFOLIO_BASE_URL` — Django base URL for validation calls

### React (`.env.*` per mode)
- `VITE_API_URL` — backend base URL per environment (dev/staging/production)
