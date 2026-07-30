# Getting Started

This guide will help you set up the **Portfolio Manager** project locally for development.

## Prerequisites

Ensure you have the following installed on your machine:
- **[uv](https://docs.astral.sh/uv/)** (for Django backend — manages Python and dependencies)
- **Node.js 26** & **npm** (for React frontend — matches CI and the `nginx/Dockerfile` build stage; Node 20 and below can no longer run the test suite)
- **Docker** & **Docker Compose** (optional, for running in containerized mode)
- **PostgreSQL** (optional, SQLite is used by default for dev)

---

## 🚀 Quick Start (Development)

### 1. Clone the Repository

```bash
git clone https://github.com/JohnFattore/FattoreStreet.git
cd FattoreStreet
```

### 2. Backend Setup (Django)

The backend handles the API, database, and business logic.

1.  **Navigate to the backend directory:**
    ```bash
    cd django
    ```

2.  **Install dependencies:**
    Dependencies are managed with [uv](https://docs.astral.sh/uv/) (`brew install uv`, or see the [installation docs](https://docs.astral.sh/uv/getting-started/installation/)). This creates a `.venv/` automatically and installs the exact versions from `uv.lock`:
    ```bash
    uv sync
    ```

3.  **Run Migrations:**
    Initialize the database (SQLite by default).
    ```bash
    uv run python manage.py migrate
    ```

4.  **Start the Development Server:**
    ```bash
    uv run python manage.py runserver
    ```
    The API will be available at `http://localhost:8000`.

### 3. Frontend Setup (React + Vite)

The frontend is a React application built with Vite.

1.  **Open a new terminal** and navigate to the frontend directory:
    ```bash
    cd react-app
    ```

2.  **Install dependencies:**
    ```bash
    npm install
    ```

3.  **Start the Development Server:**
    ```bash
    npm run dev
    ```
    The app will be available at `http://localhost:5173`.

### 4. Caching (Redis)

With `DEBUG=True` (the local default) Django uses a dummy cache and Redis is not needed. With `DEBUG=False`, external market/economic data is cached in Redis; start one and set `REDIS_URL`:

```bash
docker run -d -p 6379:6379 redis
```

Scheduled IEX daily price ingest runs in AWS (EventBridge Scheduler + Fargate), not locally — see [`springboot/deploy/terraform/`](../springboot/deploy/terraform/README.md).

### 5. Frontend Styling (Sass)

We use Sass for custom styling of React Bootstrap.

- **Watch for changes (Auto-compile):**
    ```bash
    # In react-app/ directory
    sass --watch src/styles/custom.scss:src/styles/custom.css
    ```

---

## 🐳 Running with Docker (Local Compose Deployment)

To approximate the production environment locally, use `deploy/docker-compose.dev.yml` — it builds Django/Spring Boot from source, uses plain dev env vars (no AWS Secrets Manager), and serves everything through nginx at `http://localhost`.

1.  **Build the frontend and static files** (nginx bind-mounts them):
    ```bash
    cd react-app
    npx sass src/styles/custom.scss src/styles/custom.css
    npx vite build --mode compose --emptyOutDir
    cd ../django && uv run python manage.py collectstatic --noinput
    ```

2.  **Build images and start the stack:**
    ```bash
    cd deploy
    docker compose -f docker-compose.dev.yml up -d --build
    docker compose -f docker-compose.dev.yml run --rm django python manage.py migrate
    ```
    This spins up nginx, Django (Gunicorn), Spring Boot (Flyway migrates its schema on start), Postgres 17, and Redis 8. Open `http://localhost`.

3.  **Teardown** (`-v` also wipes the dev database volume):
    ```bash
    docker compose -f docker-compose.dev.yml down -v
    ```

Full command reference: [`deploy/compose.sh`](../deploy/compose.sh).

---

## 🧪 Testing

### CI (GitHub Actions)

On every push and pull request to `main`, [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs in parallel:

- **Frontend** (`react-app/`): `npm run lint`, `npm run build`, `npx vitest --run`
- **Django** (`django/`): `uv sync --frozen` then `uv run python manage.py test` with SQLite and minimal env (`SECRET_KEY`, `DATABASE=sqlite`, `REDIS_URL`)
- **Spring Boot** (`springboot/`): `mvn test` (H2 in-memory for tests). Local runs need `SECRET_KEY` in `.env` (same as Django) so JWT validation works for `/admin/**` integration checks.
- **Secrets**: `pre-commit run detect-secrets --all-files` (same baseline as local pre-commit)

A second workflow, [`.github/workflows/docker-build.yml`](../.github/workflows/docker-build.yml), builds all three production Docker images (`nginx`, `django`, `springboot`) on the same triggers as a merge check — images are built but not pushed. The nginx image is hermetic: it compiles the React bundle and runs `collectstatic` inside the build, so it needs no pre-built local artifacts.

A third workflow, [`.github/workflows/claude-code-review.yml`](../.github/workflows/claude-code-review.yml), runs `anthropics/claude-code-action` on PR open/ready/push and posts its findings as a single sticky comment, edited in place on re-runs. It is advisory: the job succeeds whatever the review finds and never gates a merge. Setting up CI from scratch needs one repository secret for it, `CLAUDE_CODE_OAUTH_TOKEN` — generate the value with `claude setup-token` and add it under Settings → Secrets and variables → Actions. Without the secret the job fails immediately on a missing credential; nothing else in CI depends on it.

The frontend **lint** step runs `eslint` with zero warnings allowed; if it fails on GitHub, run `npm run lint` in `react-app/` and fix or suppress the reported issues.

### Backend Tests
```bash
cd django
uv run python manage.py test
```

To mirror the CI Django environment locally:

```bash
cd django
SECRET_KEY=test DATABASE=sqlite REDIS_URL=redis://127.0.0.1:6379/0 uv run python manage.py test
```

### Frontend Tests
```bash
cd react-app
npm test
```

For a quick non-interactive run (same as CI):

```bash
cd react-app
npx vitest --run
```
