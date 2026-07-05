#!/bin/sh
# LEGACY / EMERGENCY ONLY: CI is the build-and-publish path now. Every merge
# to main builds these images in GitHub Actions, pushes them to GHCR, and
# deploys via SSM (.github/workflows/docker-build.yml, deploy/DEPLOY.md).
# Use this script only if CI is unavailable; pushing from a laptop requires
# `docker login ghcr.io` with a PAT that has the write:packages scope.
set -e

cd ..

echo "=== Running React tests ==="
cd react-app
npx sass src/styles/custom.scss src/styles/custom.css
npx vitest run
cd ..

echo "=== Running Django tests ==="
cd django
uv sync --frozen
uv run python manage.py test
cd ..

echo "=== Running Spring Boot tests ==="
cd springboot
./mvnw test
cd ..

echo "=== All tests passed — starting builds ==="

# nginx is hermetic: it builds the React bundle and Django static itself,
# but its COPY paths are relative to the repo root, so build from here
docker build -f nginx/Dockerfile -t ghcr.io/johnfattore/nginx .

cd django
docker build -t ghcr.io/johnfattore/django .
cd ..

cd springboot
docker build -t ghcr.io/johnfattore/springboot .
cd ..

docker push ghcr.io/johnfattore/nginx
docker push ghcr.io/johnfattore/django
docker push ghcr.io/johnfattore/springboot

echo "Fattore Street is ready to rock and roll"