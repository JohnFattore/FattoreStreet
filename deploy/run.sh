#!/bin/sh
# FattoreStreet container launch commands.
#
# SAFE TO COMMIT: no secret values live in this file. All app config/credentials
# come from a single AWS Secrets Manager secret (fattorestreet/env); each of our
# containers is passed only its ARN via -e SECRETS_ARN and fetches the rest at
# start (see each image's docker-entrypoint.sh). Fill in the placeholders below
# (<...>) on the host before running.

# networks: dockerNet for the app stack, pgadminNet to isolate pgadmin4
# (members: pgadmin4, postgres, nginx only)
sudo docker network create --driver bridge dockerNet
sudo docker network create --driver bridge pgadminNet

# ---------------------------------------------------------------------------
# Secrets: ALL app config lives in ONE Secrets Manager secret, fattorestreet/env.
# Each container is passed only -e SECRETS_ARN (+ -e AWS_REGION); its image's
# docker-entrypoint.sh fetches this secret and exports every key as an env var at
# start. Extra keys a given service doesn't use are harmless. One-time setup
# (run with the REAL values; they are NOT stored in this file):
#
#   aws secretsmanager create-secret --name fattorestreet/env --secret-string '{
#     "DATABASE": "postgresDocker",
#     "DEBUG": "False",
#     "DJANGO_FORCE_SCRIPT_NAME": "/django",
#     "REDIS_URL": "redis://redis:6379",
#     "HOME_URL": "https://fattorestreet.com/",
#     "SECRET_KEY": "<django-and-springboot-shared-secret-key>",
#     "POSTGRES_PASSWORD": "<postgres-password>",
#     "DB_USERNAME": "postgres",
#     "DB_URL": "jdbc:postgresql://postgres:5432/springboot",
#     "SEC_CONTACT_EMAIL": "johnefattore@gmail.com",
#     "LLM_SERVER_URL": "http://localhost:8081",
#     "SHOW_JPA_SQL": "false",
#     "FRED_API_KEY": "<fred-api-key>",
#     "PGADMIN_DEFAULT_PASSWORD": "<pgadmin-password>",
#     "PGADMIN_RO_DB_PASSWORD": "<postgres-password-for-read-only-pgadmin_ro-role>"
#   }'
#
# Requires: EC2 instance role with secretsmanager:GetSecretValue on the secret,
# and IMDSv2 hop limit >= 2 (see springboot/deploy/terraform README / the
# secrets-from-arn skill). Substitute the secret's real ARN below.
SECRETS_ARN=arn:aws:secretsmanager:us-east-1:<ACCOUNT_ID>:secret:fattorestreet/env-XXXXXX
# ---------------------------------------------------------------------------

# postgres
# POSTGRES_PASSWORD is the single DB-password key: the postgres image reads it
# ONLY during first-time init (empty data dir), and django + springboot use the
# same key to connect. The volume below is already initialized, so the entrypoint
# ignores it here -- the real password lives in the volume + in fattorestreet/env
# (POSTGRES_PASSWORD).
#
# FRESH VOLUME ONLY: to re-initialize from an empty /mnt/ebs/postgres-data, the
# password must be supplied once. Fetch it from the secret on the host (the EC2
# instance role can read it) and pass it just for that run, e.g.:
#   PW=$(aws secretsmanager get-secret-value --secret-id "$SECRETS_ARN" \
#         --query SecretString --output text | jq -r .POSTGRES_PASSWORD)
#   sudo docker run ... -e POSTGRES_PASSWORD="$PW" ... postgres:17
# Published on all interfaces: the nightly hist-load Fargate task connects at
# the host's private IP (SG-restricted to the task's security group on 5432).
# Also joined to pgadminNet so the isolated pgadmin4 container can reach it.
sudo docker run -d \
  --name postgres \
  --network dockerNet \
  -v /mnt/ebs/postgres-data:/var/lib/postgresql/data \
  -p 0.0.0.0:5432:5432 \
  postgres:17
sudo docker network connect pgadminNet postgres

# django (config from fattorestreet/env via SECRETS_ARN)
sudo docker run --name django --network dockerNet -d \
  -e SECRETS_ARN=$SECRETS_ARN \
  -e AWS_REGION=us-east-1 \
  johnfattore/django

# springboot (config from fattorestreet/env via SECRETS_ARN)
sudo docker run --name springboot --network dockerNet -d \
  -e SECRETS_ARN=$SECRETS_ARN \
  -e AWS_REGION=us-east-1 \
  johnfattore/springboot

# nginx
sudo docker run --name nginx --network dockerNet \
  -v /mnt/ebs/certbot/letsencrypt:/etc/letsencrypt \
  -v /mnt/ebs/certbot/www:/var/www/certbot \
  -p 80:80 -p 443:443 \
  --log-driver=awslogs \
  --log-opt awslogs-region=us-east-1 \
  --log-opt awslogs-group=nginx-logs \
  --log-opt awslogs-stream=nginx-{{.ID}} \
  -d johnfattore/nginx
# docker run only attaches one network at create; join pgadminNet after so
# nginx can proxy /pgadmin4/ to the isolated pgadmin4 container
sudo docker network connect pgadminNet nginx

# Redis (major pinned like postgres above; bump deliberately with the
# redis-py/django-redis client pins in django/pyproject.toml). No host port:
# redis has no auth, and django reaches it over dockerNet (redis://redis:6379);
# debug with `docker exec -it redis redis-cli`.
sudo docker run --network dockerNet --name redis -d redis:8

# pgadmin4
# The pgadmin image hard-fails at startup ("You need to define the
# PGADMIN_DEFAULT_EMAIL and PGADMIN_DEFAULT_PASSWORD ...") when the password env
# is empty and /var/lib/pgadmin/pgadmin4.db doesn't exist yet, so fetch it from
# fattorestreet/env on the host (same pattern as the postgres fresh-volume
# recipe) instead of relying on a host shell var. The account seeds on first run
# and persists in the /mnt/ebs/pgadmin-data volume thereafter (pgadmin runs as
# uid 5050, which must own the mount).
#
# Least privilege (mirrors docker-compose.infra.yml): no host port (nginx
# reaches pgadmin4:80 over pgadminNet), isolated on pgadminNet (can only reach
# postgres, not redis/django/springboot), read-only rootfs with only the data
# volume and the tmpfs mounts writable, all capabilities dropped, no privilege
# escalation, bundled postfix disabled (root process, unused), memory/PID
# limits. Major pinned like postgres/redis. Register the DB connection in
# pgadmin as the read-only pgadmin_ro role (created once via
# sql/pgadmin-readonly.sql), not postgres.
sudo mkdir -p /mnt/ebs/pgadmin-data
sudo chown 5050:5050 /mnt/ebs/pgadmin-data
PGADMIN_PW=$(aws secretsmanager get-secret-value --secret-id "$SECRETS_ARN" \
  --query SecretString --output text | jq -r .PGADMIN_DEFAULT_PASSWORD)
sudo docker run -d \
  --name pgadmin4 \
  --network pgadminNet \
  -v /mnt/ebs/pgadmin-data:/var/lib/pgadmin \
  --read-only \
  --tmpfs /tmp \
  --tmpfs /var/log/pgadmin \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --memory 512m \
  --pids-limit 100 \
  -e PGADMIN_DEFAULT_EMAIL=johnefattore@gmail.com \
  -e PGADMIN_DEFAULT_PASSWORD="$PGADMIN_PW" \
  -e PGADMIN_LISTEN_PORT=80 \
  -e PGADMIN_DISABLE_POSTFIX=True \
  dpage/pgadmin4:9

# Certbot get SSL cert
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

# watchtower for updates, doesnt work maybe as well as i hope. if django restarts, nginx needs to as well
sudo docker run -d \
  --name watchtower \
  --network dockerNet \
  -e WATCHTOWER_POLL_INTERVAL=300 \
  -e WATCHTOWER_CLEANUP=true \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower

# ---------------------------------------------------------------------------
# Teardown / maintenance
# ---------------------------------------------------------------------------
sudo docker stop postgres
sudo docker container rm postgres
sudo docker image rm postgres

sudo docker stop django
sudo docker container rm django
sudo docker image rm johnfattore/django

sudo docker stop springboot
sudo docker container rm springboot
sudo docker image rm johnfattore/springboot

sudo docker stop nginx
sudo docker container rm nginx
sudo docker image rm johnfattore/nginx

sudo docker stop pgadmin4
sudo docker container rm pgadmin4
sudo docker image rm dpage/pgadmin4

sudo docker stop redis
sudo docker container rm redis

sudo docker stop watchtower
sudo docker container rm watchtower

# exec into django
sudo docker exec -it django bash

# migrate database changes
python3 manage.py migrate
