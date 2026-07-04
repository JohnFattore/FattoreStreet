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

- **CI**: GitHub Actions (`.github/workflows/ci.yml`) runs frontend, Django, and Spring Boot tests plus a secret scan on every push/PR to `main`. A second workflow (`.github/workflows/docker-build.yml`) builds all three Docker images on every PR as a merge check (build-only); the nginx image builds the React bundle and Django static files itself, so it works from a clean checkout.
- **Publish**: on pushes to `main`, the same workflow pushes the images to GHCR (`ghcr.io/johnfattore/{nginx,django,springboot}`) tagged `latest` and the commit SHA, authenticated with the built-in `GITHUB_TOKEN` — no registry credentials are stored. `deploy/build.sh` remains as a legacy/emergency local build path.
- **Deploy (automated)**: after publishing, the workflow assumes an IAM role via GitHub OIDC and sends an SSM RunCommand (no SSH) to the EC2 instance tagged `App=fattorestreet`. The command resets the host's repo clone to the deployed commit and runs `deploy/deploy.sh <sha>`, which self-heals container state (evicts name-squatting non-compose containers), converges the compose stack to the SHA tag, applies Django migrations, prunes superseded images, and health-checks nginx; its output streams back into the Action log. One-time AWS/GitHub setup: [`deploy/DEPLOY.md`](../deploy/DEPLOY.md).
  - `docker-compose.yml` — the deploy unit: `django`, `celery-worker`, `celery-beat`, `springboot`, `nginx`.
  - `docker-compose.infra.yml` — stateful infra: `postgres`, `redis`, `pgadmin4`. Converged but never version-bumped by a routine deploy (majors are pinned).
  - `SECRETS_ARN`/`AWS_REGION` come from `deploy/.env` on the host (template: `deploy/.env.example`).
- **Rollback**: re-run the deploy with an older commit's SHA, or on the host `sudo ./deploy.sh <old-sha>`.
- **Runbook**: `deploy/compose.sh` documents one-time host setup, celery (manual by design, behind the `celery` profile), and emergency manual commands. `deploy/run.sh` is kept as reference for the pre-Compose setup (including secret creation and certbot commands).

Watchtower-based auto-updates are retired: deploys are an explicit, ordered, health-checked `deploy.sh` run, so nginx and the backends restart in a coordinated way.

## 🔄 Environments

### Local (Compose)
- **Goal**: Approximate production on a dev machine (for day-to-day coding, the dev servers — `npm run dev`, `runserver`, `spring-boot:run` — remain the primary loop).
- **Config**: `deploy/docker-compose.dev.yml` — builds django/springboot from source, plain dev env vars instead of Secrets Manager, stock nginx on `http://localhost` with `nginx/nginx.dev.conf` (no SSL) and the frontend built via `npx vite build --mode compose`. Commands in `deploy/compose.sh`.

### Staging
- **Goal**: Mirror production as closely as possible.
- **Config**: Uses `nginx.conf` (local), updates Port mappings.
- **Network**: Docker Bridge network (172.x.x.x), same as production.

### Production
- **Config**: Uses production `nginx.conf`; SSL terminates at Nginx with Let's Encrypt certificates renewed via certbot (webroot challenge).
