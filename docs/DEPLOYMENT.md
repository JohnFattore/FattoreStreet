# Deployment & Infrastructure

The **Portfolio Manager** is designed to be cloud-agnostic but is currently optimized for AWS.

## ☁️ Production Hosting (AWS)

The production environment runs on a single EC2 instance behind Route 53:
- **EC2 instance**: Runs all containers on a shared Docker bridge network (`dockerNet`).
- **Nginx container**: Terminates SSL (Let's Encrypt via certbot), serves the React build and Django static files, and reverse-proxies to Django and Spring Boot.
- **EBS volume** (`/mnt/ebs`): Persists PostgreSQL data and certbot certificates across container recreation.
- **Route 53**: DNS management — `A` record points to the EC2 instance.
- **Secrets Manager**: One secret (`fattorestreet/env`) holds all app config/credentials. Containers receive only its ARN (`SECRETS_ARN`); each image's entrypoint fetches and exports the values at start. The EC2 instance role grants `secretsmanager:GetSecretValue`.

### Dev Ops System Overview
1.  **DNS (Route 53)**: `A` record points to the EC2 instance's public IP.
2.  **Nginx (ports 80/443)**: Port 80 serves ACME challenges and redirects to HTTPS; port 443 terminates SSL and routes `/django/`, `/springboot/`, `/pgadmin4/`, and static paths (see `nginx/nginx.conf`).
3.  **App containers**: Django (Gunicorn), two Celery containers (worker + beat), Spring Boot.
4.  **Data stores**: PostgreSQL 17 and Redis 8 containers on the same Docker network, with Postgres data on the EBS volume.

## 🚀 Build & Deploy

- **CI**: GitHub Actions (`.github/workflows/ci.yml`) runs frontend, Django, and Spring Boot tests plus a secret scan on every push/PR to `main`.
- **Build**: `deploy/build.sh` re-runs all tests, builds the React app and the three Docker images (`johnfattore/{nginx,django,springboot}`), and pushes them to Docker Hub.
- **Deploy**: Docker Compose on the EC2 host, from the `deploy/` directory:
  - `docker-compose.yml` — the deploy unit: `django`, `celery-worker`, `celery-beat`, `springboot`, `nginx`. Deploy = `docker compose pull && docker compose up -d`.
  - `docker-compose.infra.yml` — stateful infra: `postgres`, `redis`, `pgadmin4`. Never touched by a routine deploy; managed explicitly with `docker compose -f docker-compose.infra.yml up -d`.
  - `SECRETS_ARN`/`AWS_REGION` come from `deploy/.env` on the host (template: `deploy/.env.example`).
- **Migrations**: `docker compose run --rm django python manage.py migrate` after deploying a release that changes models.
- **Runbook**: `deploy/run.sh` documents one-time host setup (network, secret creation, certbot) and the routine deploy/migrate/logs commands.

Watchtower-based auto-updates are retired: deploys are an explicit, ordered `compose pull && up -d`, so nginx and the backends restart in a coordinated way.

## 🔄 Environments

### Staging
- **Goal**: Mirror production as closely as possible.
- **Config**: Uses `nginx.conf` (local), updates Port mappings.
- **Network**: Docker Bridge network (172.x.x.x), same as production.

### Production
- **Config**: Uses production `nginx.conf`; SSL terminates at Nginx with Let's Encrypt certificates renewed via certbot (webroot challenge).
