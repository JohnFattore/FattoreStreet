# Production Deployment Checklist

## 1. Database Rename (if desired)

Right now Django uses database name `postgres` and Spring Boot uses `sec-api`. Renaming to `django` and `springboot`:

- **On the prod server**: Create the new databases and migrate data:
  - `CREATE DATABASE django;` then `pg_dump postgres | psql django`
  - `CREATE DATABASE springboot;` then `pg_dump sec-api | psql springboot`
- **Update Django** `settings.py` — change `'NAME': 'postgres'` to `'django'` in the `postgresDocker` block
- **Update Django** `settings.py` — change `'NAME': 'postgres'` to `'django'` in the `postgresLocal` block
- **Update `run.sh`** — change Spring Boot `DB_URL` to `jdbc:postgresql://postgres:5432/springboot`
- **Update Spring Boot** `.env` — change `DB_URL` to `jdbc:postgresql://localhost:5432/springboot`

## 2. Django Migrations

- **Build & push** the new Django Docker image (`kubernetes/build.sh` handles this)
- **Exec into Django container** and run `python3 manage.py migrate`
- Key migrations since last deploy: **changeflow** (Ticket model - 0004), **portfolio** (Account model + account_type - 0025/0026), **blog** (initial - 0001)

## 3. Spring Boot Schema / Flyway Setup

Currently using `ddl-auto=update` (Hibernate auto-generates schema). To switch to Flyway:

- **Add Flyway dependency** to `pom.xml`
- **Create baseline migration** — dump current prod schema as `V1__baseline.sql` in `src/main/resources/db/migration/`
- **Run `flyway baseline`** on prod DB so it doesn't try to re-apply V1
- **Change `ddl-auto`** from `update` to `validate`
- **Create V2 migration** for any new entity changes since last deploy (IndexMember, ListingIndexMetrics, etc.)

**Alternative (simpler):** Keep `ddl-auto=update` for now. Hibernate will add new columns/tables automatically. You can adopt Flyway later when schema changes get riskier.

## 4. Spring Boot Data Population (in order)

- **Load assets & listings**: `GET /springboot/admin/asset-load` (with `X-Admin-Key` header)
- **Bulk load IEX prices**: `psql -d springboot < springboot/data/daily_prices_data.sql` (1GB file, ~16M rows)
- **Sync SEC financial frames**: `GET /springboot/admin/sync-frames`
- **Adjust prices** (splits/dividends): `GET /springboot/admin/adjust-prices`
- **Refresh index metrics**: `POST /springboot/admin/indexes/refresh-stocks`
- **Rebuild cap-ranked indexes**: `POST /springboot/admin/indexes/rebuild` (optional `?code=FAT50`, `FAT100`, or `FAT1000`; omit `code` to rebuild all). Legacy: `rebuild-fattore-50`, `rebuild-fattore-100`, `rebuild-fattore-1000`.
- **Summarize filings** (optional, uses LLM): `GET /springboot/admin/summarize-filings`

## 5. Celery Periodic Tasks

Schedules are stored in the database via `django-celery-beat` (DatabaseScheduler), so you need to set them up via Django admin:

- **Set up `load_yfinance_cache`** — daily, e.g. 6:30 PM ET (after market close)
- **Set up `load_fred_cache`** — daily or weekly
- **Delete any stale tasks** from previous deploys (check Django admin `/admin/django_celery_beat/periodictask/`)
- **Verify Redis is running** on prod and `REDIS_URL` is correct

## 6. Fix `run.sh` Issues

- **Duplicate container name**: Both celery containers are named `celery` — rename the worker to `celery-worker`
- **Missing env vars**: Spring Boot container is missing `ADMIN_API_KEY`, `SEC_CONTACT_EMAIL`, `LLM_SERVER_URL`
- **Missing env vars**: Django container is missing `SECRET_KEY`, `GOOGLE_API_KEY`, `FRED_API_KEY`, `FINNHUB_API_KEY`, `SEC_CONTACT_EMAIL`
- **Consider `.env` files** instead of inline secrets in run.sh (`--env-file .env`)

## 7. Build & Deploy

- **Run tests**: `npx vitest --run` (React), `python3 manage.py test` (Django), `mvn test` (Spring Boot)
- **Build React**: `npm run build` (outputs to `nginx/dist/`)
- **Build & push Docker images**: `kubernetes/build.sh`
- **SSH to prod** and run updated `run.sh`
- **Run Django migrations** inside the container
- **Verify SSL cert** is still valid (Certbot renewal)

## 8. Post-Deploy Verification

- **Hit each route** through nginx: `/`, `/admin/`, `/users/api/`, `/portfolio/api/`, `/springboot/`
- **Check Celery** is running: Django admin → Celery Results, or `docker logs celery`
- **Verify data**: spot-check a few tickers via the Spring Boot API
- **Check Watchtower** isn't fighting you with old images

