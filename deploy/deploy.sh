#!/bin/sh
# FattoreStreet automated deploy -- run on the EC2 host as root by SSM
# RunCommand (.github/workflows/docker-build.yml) after the host clone has
# been reset to the commit being deployed. Safe to run by hand too:
#   sudo ./deploy.sh [tag]      # tag defaults to latest
#
# Idempotent converge: fixes the common pre-existing container states
# (leftover docker-run containers from the pre-compose setup, name squatters,
# missing network) before bringing the compose stack up to the requested
# image tag. One-time setup and manual operations live in DEPLOY.md and
# compose.sh.
#
# Celery is intentionally NOT deployed here -- the "celery" profile services
# stay manual (see compose.sh).

set -eu

TAG=${1:-latest}
cd "$(dirname "$0")"

echo "=== Deploying tag $TAG ==="

# Shared bridge network (both compose files reference it as external)
docker network inspect dockerNet >/dev/null 2>&1 \
  || docker network create --driver bridge dockerNet

# Evict containers squatting on our names that compose doesn't own (leftover
# docker-run containers, watchtower, crashed one-offs). A name collision is
# the #1 way `compose up` hard-fails. Graceful stop first -- postgres wants a
# SIGTERM; its data is safe on the /mnt/ebs bind mount either way.
# celery-worker/celery-beat are compose-owned and manual -- left alone.
evict_unless_project() {
  name=$1
  project=$2
  if docker inspect "$name" >/dev/null 2>&1; then
    owner=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$name")
    if [ "$owner" != "$project" ]; then
      echo "removing non-compose container: $name"
      docker stop "$name" >/dev/null 2>&1 || true
      docker rm "$name"
    fi
  fi
}
for name in django springboot nginx celery watchtower; do
  evict_unless_project "$name" fattorestreet
done
for name in postgres redis pgadmin4; do
  evict_unless_project "$name" fattorestreet-infra
done

# Stateful infra: no-op unless docker-compose.infra.yml changed (majors are
# pinned; routine deploys never bump them)
docker compose -f docker-compose.infra.yml up -d

# App services: pull the exact tag, converge, drop services deleted from the
# compose file
export TAG
docker compose pull
docker compose up -d --remove-orphans

# Django migrations (Spring Boot's Flyway migrates itself on start)
docker compose run --rm django python manage.py migrate

# Reclaim disk from superseded image layers (watchtower's cleanup is retired)
docker image prune -f

# Health check: nginx must answer over HTTPS or the deploy (and the GitHub
# Action behind it) fails
echo "=== Health check ==="
tries=0
until curl -fsk https://localhost/ -o /dev/null; do
  tries=$((tries + 1))
  if [ "$tries" -ge 6 ]; then
    echo "health check failed: https://localhost/ not answering" >&2
    exit 1
  fi
  sleep 5
done

echo "=== Deploy of $TAG complete ==="
