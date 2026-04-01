# Portfolio Manager Setup

**A Full-Stack Financial Portfolio & Social Platform**

This repository contains the source code for a comprehensive web application featuring:

- **Finance**: Stock tracking, historical data, and market indexes—Django for the main portfolio API, plus a **Spring Boot** service for SEC EDGAR filings, quarterly fundamentals, and related market data.
- **Social**: Restaurant reviews and recommendation engine.
- **AI**: An investing chatbot assistant; optional **local AI** tooling lives under `llm/` (see [llm/README.md](llm/README.md)).

---

## 📚 Documentation

We have organized the documentation to help you get started quickly:

- **[🚀 Getting Started](docs/GETTING_STARTED.md)**: Setup guide for local development and staging.
- **[🏗 Architecture](docs/ARCHITECTURE.md)**: High-level system design, tech stack, data flow, and development practices.
- **[📖 API Reference](docs/API_REFERENCE.md)**: Django and Spring Boot HTTP endpoints.
- **[☁️ Deployment](docs/DEPLOYMENT.md)**: Infrastructure guide (Docker, Kubernetes, cloud).

**CI**: Pull requests and pushes to `main` run [GitHub Actions](.github/workflows/ci.yml) (React lint/build/tests, Django tests, Spring Boot tests, detect-secrets). See [Getting Started](docs/GETTING_STARTED.md) (Testing and CI sections) for local equivalents.

Service-specific detail: [django/README.md](django/README.md), [springboot/README.md](springboot/README.md), [react-app/README.md](react-app/README.md).

---

## 🛠 Project structure

| Directory | Description |
|-----------|-------------|
| **[`django/`](django/)** | **Primary API**. Django REST Framework—auth, portfolio, chatbot, restaurants, Celery tasks. |
| **[`springboot/`](springboot/)** | **SEC microservice**. Spring Boot—EDGAR data, quarterly financials, corporate actions, index membership, daily prices (IEX ingest). |
| **[`react-app/`](react-app/)** | **Frontend**. React, TypeScript, Vite. |
| **[`llm/`](llm/)** | **Local AI**. llama.cpp, optional SD/TTS helpers (not required for the web app). |
| **[`nginx/`](nginx/)** | Reverse proxy configuration for composed deployments. |
| **[`kubernetes/`](kubernetes/)** | Kubernetes manifests and related DevOps assets. |
| **[`docs/`](docs/)** | Project documentation. |

---

## ⚡ Quick start

For full details, see [Getting Started](docs/GETTING_STARTED.md). Typical local ports: Django **8000**, Spring Boot **8080**, Vite **5173**.

**Django (primary API)**:

```bash
cd django
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python3 manage.py migrate
python3 manage.py runserver
```

**Spring Boot (SEC / filings service)** — requires **Java 17**, **Maven**, and **PostgreSQL**. Copy or create `springboot/.env` with database credentials and **`SECRET_KEY` set to the same value as Django** (JWT verification for `/admin/**`; see [springboot/README.md](springboot/README.md)):

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
