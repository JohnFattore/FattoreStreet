#!/bin/sh
# FattoreStreet Docker Compose runbook (successor to the docker-run commands
# in run.sh, which is kept as-is for reference on the current live setup).
#
# SAFE TO COMMIT: no secret values live in this file. All app config/credentials
# come from a single AWS Secrets Manager secret (fattorestreet/env); containers
# are passed only its ARN via SECRETS_ARN (from deploy/.env, see .env.example)
# and fetch the rest at start (see each image's docker-entrypoint.sh).
#
# The containers are defined in the compose files next to this script:
#   docker-compose.yml        app services (django, celery-worker, celery-beat,
#                             springboot, nginx) -- the deploy unit
#   docker-compose.infra.yml  stateful infra (postgres, redis, pgadmin4) --
#                             never touched by a routine deploy
#
# This file is a copy/paste runbook, not a script to execute top-to-bottom.

# ---------------------------------------------------------------------------
# One-time host setup
# ---------------------------------------------------------------------------

# 1. Shared bridge network (both compose files reference it as external)
sudo docker network create --driver bridge dockerNet

# 2. Env file for compose: copy the template and fill in the real secret ARN
#    (the fattorestreet/env secret itself is created per the doc block in
#    run.sh; requires the EC2 instance role to allow secretsmanager:GetSecretValue
#    and IMDSv2 hop limit >= 2)
cp .env.example .env

# 3. Stateful infra (postgres, redis, pgadmin4). POSTGRES_PASSWORD is only read
# on FIRST-TIME init of an empty /mnt/ebs/postgres-data volume -- see the
# comments in docker-compose.infra.yml for the fetch-from-secret recipe.
# PGADMIN_DEFAULT_PASSWORD seeds the pgadmin admin account on first run only.
sudo docker compose -f docker-compose.infra.yml up -d

# 4. App services
sudo docker compose up -d

# 5. SSL cert: run the certbot certonly command from run.sh (nginx must be up
# to serve the webroot challenge; rerun to renew)

# ---------------------------------------------------------------------------
# One-time cutover from the old docker-run/watchtower setup (run.sh)
# ---------------------------------------------------------------------------
# Watchtower is retired: deploys are now an explicit compose pull/up. Data is
# safe -- postgres/certbot state lives in the /mnt/ebs bind mounts.
#
#   sudo docker stop watchtower && sudo docker rm watchtower
#   sudo docker stop nginx django springboot celery postgres redis pgadmin4
#   sudo docker rm   nginx django springboot celery postgres redis pgadmin4
#   sudo docker compose -f docker-compose.infra.yml up -d
#   sudo docker compose up -d

# ---------------------------------------------------------------------------
# Routine operations (from this directory)
# ---------------------------------------------------------------------------

# deploy the latest pushed images (celery is off by default -- see below)
sudo docker compose pull && sudo docker compose up -d

# celery-worker/celery-beat sit behind the "celery" profile: bare compose
# commands skip them. Include them (enables the FRED/yfinance cache tasks):
sudo docker compose --profile celery pull && sudo docker compose --profile celery up -d

# apply Django migrations (one-shot container, same image + secret as django)
sudo docker compose run --rm django python manage.py migrate

# status / logs / shell
sudo docker compose ps
sudo docker compose logs -f django
sudo docker exec -it django bash

# stop the whole app (infra keeps running)
sudo docker compose down

# infra changes (rare; postgres/redis version bumps etc.)
sudo docker compose -f docker-compose.infra.yml up -d

# ---------------------------------------------------------------------------
# Local development deployment (docker-compose.dev.yml)
# ---------------------------------------------------------------------------
# Approximates production on a dev machine: builds django/springboot from
# source, plain dev env vars instead of Secrets Manager, stock nginx on
# http://localhost (no SSL). No sudo needed with Docker Desktop.

# 1. Build the frontend for the compose mode (URLs point at http://localhost)
#    and collect Django static files -- nginx bind-mounts both:
#      cd react-app
#      npx sass src/styles/custom.scss src/styles/custom.css
#      npx vite build --mode compose --emptyOutDir
#      cd ../django && uv run python manage.py collectstatic --noinput

# 2. Build images + start the stack (from deploy/)
docker compose -f docker-compose.dev.yml up -d --build

# 3. Apply Django migrations (Spring Boot's Flyway migrates itself on start)
docker compose -f docker-compose.dev.yml run --rm django python manage.py migrate

# App: http://localhost  |  Django admin: http://localhost/django/admin/

# status / logs / teardown (-v also wipes the dev database volume)
docker compose -f docker-compose.dev.yml ps
docker compose -f docker-compose.dev.yml logs -f django
docker compose -f docker-compose.dev.yml down
docker compose -f docker-compose.dev.yml down -v
