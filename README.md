# Portfolio Manager Setup

**A Full-Stack Financial Portfolio & Social Platform**

This repository contains the source code for a comprehensive web application featuring:

- **Finance**: Stock tracking, historical data, and market indexes—Django for the main portfolio API, plus a **Spring Boot** service for SEC EDGAR filings, quarterly fundamentals, corporate actions, FRED economic data, and daily prices.
- **Social**: Restaurant reviews and recommendations, a blog, and media recommendations (books, movies, shows, music, podcasts, games, websites).
- **AI**: An investing chatbot assistant (Google Gemini) and LLM-summarized 10-K filings; optional **local AI** tooling lives under `llm/` (see [llm/README.md](llm/README.md)).

---

## 📚 Documentation

We have organized the documentation to help you get started quickly:

- **[🚀 Getting Started](docs/GETTING_STARTED.md)**: Setup guide for local development and staging.
- **[🏗 Architecture](docs/ARCHITECTURE.md)**: High-level system design, tech stack, data flow, and development practices.
- **[📖 API Reference](docs/API_REFERENCE.md)**: Django and Spring Boot HTTP endpoints.
- **[☁️ Deployment](docs/DEPLOYMENT.md)**: Infrastructure guide (Docker Compose on AWS EC2, GHCR images, SSM-driven deploys).

**CI**: Pull requests and pushes to `main` run [GitHub Actions](.github/workflows/ci.yml) (React lint/build/tests, Django tests, Spring Boot tests, detect-secrets). See [Getting Started](docs/GETTING_STARTED.md) (Testing and CI sections) for local equivalents.

Service-specific detail: [django/README.md](django/README.md), [springboot/README.md](springboot/README.md), [react-app/README.md](react-app/README.md).

---

## 🛠 Project structure

| Directory | Description |
|-----------|-------------|
| **[`django/`](django/)** | **Primary API**. Django REST Framework—auth, portfolio, chatbot, restaurants, changeflow (changelog + feedback), blog, entertainment. |
| **[`springboot/`](springboot/)** | **SEC microservice**. Spring Boot—EDGAR data, quarterly financials, corporate actions, index membership, daily prices (IEX ingest), FRED economic data. |
| **[`react-app/`](react-app/)** | **Frontend**. React, TypeScript, Vite. |
| **[`llm/`](llm/)** | **Local AI**. llama.cpp, optional SD/TTS helpers (not required for the web app). |
| **[`nginx/`](nginx/)** | Reverse proxy configuration for composed deployments. |
| **[`deploy/`](deploy/)** | Docker Compose stacks, build/deploy scripts, and the automated-deploy runbook ([DEPLOY.md](deploy/DEPLOY.md)). |
| **[`docs/`](docs/)** | Project documentation. |

---

## ⚡ Quick start

For full details, see [Getting Started](docs/GETTING_STARTED.md). Typical local ports: Django **8000**, Spring Boot **8080**, Vite **5173**.

**Django (primary API)** — requires [uv](https://docs.astral.sh/uv/) (`brew install uv`). Create `django/.env` with at least `SECRET_KEY` and `DATABASE` (e.g. `DATABASE=sqlite` for a quick start; see [Getting Started](docs/GETTING_STARTED.md) for the full list):

```bash
cd django
uv sync
uv run python manage.py migrate
uv run python manage.py runserver
```

**Spring Boot (SEC / filings service)** — requires **Java 25**, **Maven**, and **PostgreSQL**. Copy or create `springboot/.env` with database credentials (no `SECRET_KEY`: the service has no authenticated routes; see [springboot/README.md](springboot/README.md)):

```bash
cd springboot
mvn spring-boot:run
```

**Frontend**:

```bash
cd react-app
npm install
npm run dev
```
