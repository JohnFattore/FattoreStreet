# Portfolio Manager Setup

**A Full-Stack Financial Portfolio & Social Platform**

This repository contains the source code for a comprehensive web application featuring:
- **Finance**: Stock tracking, historical data analysis, and market index monitoring.
- **Social**: Restaurant reviews and recommendation engine.
- **AI**: An investing chatbot assistant.

---

## 📚 Documentation

We have organized the documentation to help you get started quickly:

- **[🚀 Getting Started](docs/GETTING_STARTED.md)**: Setup guide for Local Development and Staging.
- **[🏗 Architecture](docs/ARCHITECTURE.md)**: High-level system design, tech stack, and data flow.
- **[📖 API Reference](docs/API_REFERENCE.md)**: Details on available backend endpoints.
- **[☁️ Deployment](docs/DEPLOYMENT.md)**: Infrastructure guide (AWS, Docker, Kubernetes).

---

## 🛠 Project Structure

The repository is organized into a monorepo structure:

| Directory | Description |
|-----------|-------------|
| **[`django/`](django/)** | **Backend**. Python Django REST Framework application. |
| **[`react-app/`](react-app/)** | **Frontend**. React application built with Vite. |
| **[`docs/`](docs/)** | **Documentation**. Detailed guides and references. |
| **[`kubernetes/`](kubernetes/)** | **DevOps**. Deployment scripts and K8s manifests. |
| **[`aws/`](aws/)** | **Cloud**. AWS specific configurations. |

---

## 🧩 Development Philosophy

### Trunk Based Development
We follow [Trunk Based Development](https://trunkbaseddevelopment.com/). We rely on a single `main` branch ("trunk") for source of truth.
- **Development**: Runs locally with SQLite/Dev Server.
- **Staging**: Runs locally via Docker storage to mimic production.
- **Production**: Deployed snapshot of the trunk.

### Coding Conventions
- **Naming**: We strictly avoid Hungarian Notation (e.g., `strTicker`, `numShares`). We believe modern IDEs and typing make this obsolete and it hinders readability.
- **Formatting**: Python follows PEP 8. JavaScript/TypeScript follows Prettier standards.

---

## ⚡ Quick Start

For full details, see [Getting Started](docs/GETTING_STARTED.md).

**Backend**:
```bash
cd django
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python3 manage.py runserver
```

**Frontend**:
```bash
cd react-app
npm install
npm run dev
```