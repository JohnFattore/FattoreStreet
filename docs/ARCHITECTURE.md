# Architecture Overview

The **Portfolio Manager** is a full-stack web application designed to track financial assets, provide chatbots, and manage restaurant reviews. It follows a decoupled architecture with a Django REST Framework backend and a React frontend.

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
        Celery[Celery Async Workers]
        Redis[(Redis Broker)]
        ExtAPI[External APIs (Price Data)]
    end

    User --> React
    React -->|API Requests| Nginx
    Nginx -->|Proxy| Gunicorn
    Gunicorn --> Django
    Django -->|Query/Save| Postgres
    Django -->|Async Tasks| Celery
    Celery -->|Queue| Redis
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
| **Task Queue** | Celery & Redis | Handles background tasks like fetching stock prices. |
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
- **`react-app/`**: Frontend source code.
    - **`src/components/`**: Reusable UI components.
    - **`src/pages/`**: Main application views.
    - **`src/store/`**: Redux state slices.
- **`springboot/`**: Spring Boot `sec-api` service (SEC EDGAR data, IEX-derived daily prices, and market index membership under `/index-members` and `/admin/indexes/*`).
- **`kubernetes/`**: Scripts and configs for K8s deployment.
- **`aws/`**: AWS specific configuration files.

## 🔄 Data Flow

1.  **Authentication**: Users log in via React login forms. Django verifies credentials and issues simpleJWT tokens.
2.  **Portfolio Data**: 
    - User adds an asset (e.g., "AAPL").
    - Django requests metadata from external APIs (yfinance/Finnhub).
    - Data is stored in Postgres.
    - Async workers (Celery) periodically update prices.
3.  **Deployment**:
    - Build scripts create Docker images.
    - Images are pushed to container registry.
    - Production pulls updated images and restarts containers (using Docker Compose or Kubernetes).
