# Hermetic build: produces the React bundle and Django static files itself, so
# the image builds from a clean checkout (nginx/dist and nginx/static are
# gitignored build artifacts). Build from the REPO ROOT:
#   docker build -f nginx/Dockerfile -t johnfattore/nginx .

# Stage 1: React build (sass generates the gitignored custom.css that
# index.html links, then Vite emits ../nginx/dist -- the /app layout mirrors
# the repo so the relative outDir in vite.config.ts works)
FROM node:20-slim AS react-build
WORKDIR /app/react-app
COPY react-app/package.json react-app/package-lock.json ./
RUN npm ci
COPY react-app/ ./
RUN npx sass src/styles/custom.scss src/styles/custom.css \
    && npm run build

# Stage 2: Django collectstatic (base image + uv pattern mirrors
# django/Dockerfile; dummy env values match the django job in ci.yml --
# settings just needs them present to import)
FROM python:3.14-slim AS django-static
COPY --from=ghcr.io/astral-sh/uv:0.11.26 /uv /uvx /bin/
WORKDIR /app/django
ENV UV_PYTHON_DOWNLOADS=never
COPY django/pyproject.toml django/uv.lock ./
RUN uv sync --frozen --no-cache
COPY django/ ./
# STATIC_ROOT is ../nginx/static relative to the Django BASE_DIR
RUN SECRET_KEY=build-only-dummy-secret-key \
    DATABASE=sqlite \
    REDIS_URL=redis://127.0.0.1:6379/0 \
    GOOGLE_API_KEY=build-only-dummy-google-api-key \
    uv run python manage.py collectstatic --noinput

# Stage 3: nginx serving the artifacts from the stages above
FROM nginx:1.31
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/index.html /var/www/index.html
COPY --from=react-build /app/nginx/dist /var/www/dist
COPY --from=django-static /app/nginx/static /var/www/static
