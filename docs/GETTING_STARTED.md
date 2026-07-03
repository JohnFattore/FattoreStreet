# Getting Started

This guide will help you set up the **Portfolio Manager** project locally for development.

## Prerequisites

Ensure you have the following installed on your machine:
- **[uv](https://docs.astral.sh/uv/)** (for Django backend — manages Python and dependencies)
- **Node.js 18+** & **npm** (for React frontend)
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

### 4. Background Tasks (Celery & Redis)

Some features (like fetching stock prices) require asynchronous workers.

1.  **Start Redis** (Message Broker):
    ```bash
    docker run -d -p 6379:6379 redis
    ```

2.  **Start Celery Worker** (in `django/` dir):
    ```bash
    uv run celery -A mysite worker --beat -E -n beat
    ```

3.  **Optional — scheduled IEX daily prices:** If you use django-celery-beat to run `portfolio.tasks.load_iex_hist`, set `SPRINGBOOT_BASE_URL` in `django/.env` to your Spring Boot service URL (same `SECRET_KEY` as Django for JWT). See [`django/README.md`](../django/README.md) (Celery tasks).

### 5. Frontend Styling (Sass)

We use Sass for custom styling of React Bootstrap.

- **Watch for changes (Auto-compile):**
    ```bash
    # In react-app/ directory
    sass --watch src/styles/custom.scss:src/styles/custom.css
    ```

---

## 🐳 Running with Docker (Staging Setup)

To replicate the production environment locally using Docker:

1.  **Navigate to the django directory** (where `docker-compose.yml` resides - *Note: Verify if compose file is in root or django dir, assuming django based on previous readme*):
    ```bash
    cd django
    ```

2.  **Build and Start Containers:**
    ```bash
    docker compose up --build
    ```
    This spins up:
    - **Nginx** (Reverse Proxy)
    - **Gunicorn** (Django App)
    - **Postgres** (Database)

3.  **Run Frontend (Staging Mode):**
    In `react-app/`:
    ```bash
    npm run staging
    ```

---

## 🧪 Testing

### CI (GitHub Actions)

On every push and pull request to `main`, [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs in parallel:

- **Frontend** (`react-app/`): `npm run lint`, `npm run build`, `npx vitest --run`
- **Django** (`django/`): `uv sync --frozen` then `uv run python manage.py test` with SQLite and minimal env (`SECRET_KEY`, `DATABASE=sqlite`, `REDIS_URL`)
- **Spring Boot** (`springboot/`): `mvn test` (H2 in-memory for tests). Local runs need `SECRET_KEY` in `.env` (same as Django) so JWT validation works for `/admin/**` integration checks.
- **Secrets**: `pre-commit run detect-secrets --all-files` (same baseline as local pre-commit)

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
