#!/bin/sh
# FattoreStreet host setup + operations runbook.
#
# SAFE TO COMMIT: no secret values live in this file. All app config/credentials
# come from a single AWS Secrets Manager secret (fattorestreet/env); containers
# are passed only its ARN via SECRETS_ARN (from deploy/.env, see .env.example)
# and fetch the rest at start (see each image's docker-entrypoint.sh).
#
# The containers themselves are defined in the compose files next to this
# script and are NOT started here:
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
cp .env.example .env

# 3. Secrets: ALL app config lives in ONE Secrets Manager secret,
# fattorestreet/env. Each container's entrypoint fetches this secret and
# exports every key as an env var at start. Extra keys a given service doesn't
# use are harmless. One-time setup (run with the REAL values; they are NOT
# stored in this file):
#
#   aws secretsmanager create-secret --name fattorestreet/env --secret-string '{
#     "DATABASE": "postgresDocker",
#     "DEBUG": "False",
#     "DJANGO_FORCE_SCRIPT_NAME": "/django",
#     "REDIS_URL": "redis://redis:6379",
#     "HOME_URL": "https://fattorestreet.com/",
#     "SPRINGBOOT_BASE_URL": "https://fattorestreet.com/springboot/",
#     "SECRET_KEY": "<django-and-springboot-shared-secret-key>",
#     "POSTGRES_PASSWORD": "<postgres-password>",
#     "DB_USERNAME": "postgres",
#     "DB_URL": "jdbc:postgresql://postgres:5432/springboot",
#     "SEC_CONTACT_EMAIL": "johnefattore@gmail.com",
#     "LLM_SERVER_URL": "http://localhost:8081",
#     "SHOW_JPA_SQL": "false"
#   }'
#
# Requires: EC2 instance role with secretsmanager:GetSecretValue on the secret,
# and IMDSv2 hop limit >= 2 (see springboot/deploy/terraform README / the
# secrets-from-arn skill).

# 4. Stateful infra (postgres, redis, pgadmin4). POSTGRES_PASSWORD is only read
# on FIRST-TIME init of an empty /mnt/ebs/postgres-data volume -- see the
# comments in docker-compose.infra.yml for the fetch-from-secret recipe.
# PGADMIN_DEFAULT_PASSWORD seeds the pgadmin admin account on first run only.
sudo docker compose -f docker-compose.infra.yml up -d

# 5. App services
sudo docker compose up -d

# 6. Certbot get SSL cert (nginx must be up to serve the webroot challenge;
# rerun to renew)
sudo docker run --rm \
  --name certbot \
  --network dockerNet \
  -v /mnt/ebs/certbot/letsencrypt:/etc/letsencrypt \
  -v /mnt/ebs/certbot/www:/var/www/certbot \
  certbot/certbot certonly \
  --webroot \
  -w /var/www/certbot \
  -d fattorestreet.com \
  --email johnefattore@gmail.com \
  --agree-tos

# ---------------------------------------------------------------------------
# One-time cutover from the old docker-run/watchtower setup
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

# deploy the latest pushed images
sudo docker compose pull && sudo docker compose up -d

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
