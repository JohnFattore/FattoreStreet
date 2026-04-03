# Production Deployment Checklist

## 1. Django Migrations

- **Build & push** the new Django Docker image (`kubernetes/build.sh` handles this)
- **Exec into Django container** and run `python3 manage.py migrate`
- Key migrations since last deploy: **changeflow** (Ticket model - 0004), **portfolio** (Account model + account_type - 0025/0026), **blog** (initial - 0001)

## 2. Spring Boot Schema / Flyway Setup

Flyway is configured with `ddl-auto=validate` and a baseline migration exists (`V1__initial_schema.sql`).

- Add Flyway dependency to `pom.xml`
- Create baseline migration `V1__initial_schema.sql`
- Set `ddl-auto` to `validate`
- **Run `flyway baseline`** on prod DB so it doesn't try to re-apply V1
- **Create V2 migration** for any new entity changes since last deploy (IndexMember, ListingIndexMetrics, etc.)

## 3. Spring Boot Data Population (in order)

- **Load assets & listings**: `GET /springboot/admin/asset-load` with `Authorization: Bearer` (Django access JWT for user id `1`)
- **Bulk load IEX prices**: `psql -d springboot < springboot/data/daily_prices_data.sql` (1GB file, ~16M rows)
- **Sync SEC financial frames**: `GET /springboot/admin/sync-frames`
- **Adjust prices** (splits/dividends): `GET /springboot/admin/adjust-prices`
- **Refresh index metrics**: `POST /springboot/admin/indexes/refresh-stocks`
- **Rebuild cap-ranked indexes**: `POST /springboot/admin/indexes/rebuild` (optional `?code=FAT50`, `FAT100`, or `FAT1000`; omit `code` to rebuild all). Legacy: `rebuild-fattore-50`, `rebuild-fattore-100`, `rebuild-fattore-1000`.
- **Summarize filings** (optional, uses LLM): `GET /springboot/admin/summarize-filings`

## 4. Celery Periodic Tasks

Schedules are stored in the database via `django-celery-beat` (DatabaseScheduler), so you need to set them up via Django admin:

- **Set up `load_yfinance_cache`** — daily, e.g. 6:30 PM ET (after market close)
- **Set up `load_fred_cache`** — daily or weekly
- **Delete any stale tasks** from previous deploys (check Django admin `/admin/django_celery_beat/periodictask/`)
- **Verify Redis is running** on prod and `REDIS_URL` is correct

## 5. Build & Deploy

- **Run tests**: `npx vitest --run` (React), `python3 manage.py test` (Django), `mvn test` (Spring Boot)
- **Build React**: `npm run build` (outputs to `nginx/dist/`)
- React build exists in `nginx/dist/`
- **Build & push Docker images**: `kubernetes/build.sh`
- **SSH to prod** and run updated `run.sh`
- **Run Django migrations** inside the container
- **Verify SSL cert** is still valid (Certbot renewal)

## 6. Post-Deploy Verification

- **Hit each route** through nginx: `/`, `/admin/`, `/users/api/`, `/portfolio/api/`, `/springboot/`
- **Check Celery** is running: Django admin → Celery Results, or `docker logs celery`
- **Verify data**: spot-check a few tickers via the Spring Boot API
- **Check Watchtower** isn't fighting you with old images

