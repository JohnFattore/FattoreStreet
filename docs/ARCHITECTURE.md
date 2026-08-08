# Architecture Overview

The **Portfolio Manager** is a full-stack web application designed to track financial assets, provide chatbots, and manage restaurant reviews. It follows a decoupled architecture with a Django REST Framework backend and a React frontend.

This document describes the product-level architecture: what the services are and how a request moves between them. For the *why* behind the price and corporate-action data pipeline (its problem statement, tenets, and load-bearing design decisions), see [High-Level Design](high-level-design.md), the root of the design tree in [`intent/`](intent/).

## 🏗 High-Level Architecture

```mermaid
graph TD
    User[User Browser]
    
    subgraph "Frontend"
        React[React App (Vite)]
    end
    
    subgraph "Backend Infrastructure"
        Nginx[NGINX Reverse Proxy]
        Gunicorn[Gunicorn App Server]
        Django[Django REST API]
        Postgres[(PostgreSQL DB)]
        Redis[(Redis Cache)]
        ExtAPI[External APIs (Price Data)]
    end

    User --> React
    React -->|API Requests| Nginx
    Nginx -->|Proxy| Gunicorn
    Gunicorn --> Django
    Django -->|Query/Save| Postgres
    Django -->|Cache| Redis
    Django -->|Fetch Data| ExtAPI
```

## 🛠 Tech Stack

| Component | Technology | Description |
|-----------|------------|-------------|
| **Frontend** | React, TypeScript, Vite | Fast, modern UI with strong typing. |
| **State Mgmt** | Redux | Global state management for user data and portfolio state. |
| **Styling** | Sass, Bootstrap | Custom styling and responsive layout. |
| **Backend** | Python, Django, DRF | Robust web framework and REST API toolkit. |
| **Database** | PostgreSQL | Relational database for production logic. |
| **Caching** | Redis | Caches external market/economic data fetched on request. |
| **Web Server** | Nginx & Gunicorn | Production-grade serving and reverse proxying. |
| **Containerization** | Docker | Consistent environments for staging and production. |
| **Cloud** | AWS (EC2/Fargate) | Hosting infrastructure. |

## 📂 Key Directories

- **`django/`**: Backend source code.
    - **`mysite/`**: Main Django settings and configuration.
    - **`portfolio/`**: Logic for asset tracking and external API integration.
    - **`users/`**: Authentication, user profiles, and JWT handling.
    - **`chatbot/`**: Logic for the AI investing assistant.
    - **`restaurants/`**: Restaurant review system.
    - **`changeflow/`**: Changelog and feedback tickets.
    - **`blog/`**: Blog posts with categories and tags.
    - **`entertainment/`**: Media recommendations (books, movies, shows, music, podcasts, games, websites).
- **`react-app/`**: Frontend source code.
    - **`src/components/`**: Reusable UI components.
    - **`src/pages/`**: Main application views.
    - **`src/reducers/`**: Redux state slices (store setup in `src/store.ts`).
- **`springboot/`**: Spring Boot `sec-api` service (SEC EDGAR data, IEX-derived daily prices, and market index membership under `/index-members`). Every route is public; the service authenticates nothing and holds no shared secret with Django. The data-loading jobs that used to sit behind `/admin/**` run as scheduled Fargate one-shots instead (see `springboot/deploy/terraform/`).
- **`deploy/`**: Docker Compose stacks and build/deploy scripts (see [Deployment](DEPLOYMENT.md)).
- **`nginx/`**: Reverse proxy configuration.

## 🔄 Data Flow

1.  **Authentication**: Users log in via React login forms. Django verifies credentials and issues simpleJWT tokens.
2.  **Portfolio Data**: 
    - User adds an asset (e.g., "AAPL").
    - Django requests metadata from external APIs (yfinance/Finnhub).
    - Data is stored in Postgres.
    - External API responses are cached in Redis on first request; daily prices are ingested by a scheduled Fargate run of the Spring Boot service. Five such one-shot tasks exist, selected by `APP_RUN_MODE` on the shared image: `hist-load` (daily prices + corporate-action adjustment), `index-load` (index metrics + rebuilds), `fundamentals-load` (SEC XBRL frames), `asset-load` (SEC ticker universe, monthly) and `validate-prices` (weekly read-only accuracy report). Terraform is the source of truth: `springboot/deploy/terraform/`.
3.  **Deployment**:
    - Build scripts create Docker images.
    - Images are pushed to container registry.
    - Production pulls updated images and restarts containers (using Docker Compose or Kubernetes).

## 🧩 Development philosophy

### Trunk-based development

We follow [Trunk Based Development](https://trunkbaseddevelopment.com/). We rely on a single `main` branch ("trunk") for source of truth.

- **Development**: Runs locally with SQLite/Dev Server.
- **Staging**: Runs locally via Docker storage to mimic production.
- **Production**: Deployed snapshot of the trunk.

### Coding conventions

- **Naming**: We strictly avoid Hungarian Notation (e.g., `strTicker`, `numShares`). We believe modern IDEs and typing make this obsolete and it hinders readability.
- **Formatting**: Python follows PEP 8. JavaScript/TypeScript follows Prettier standards.
