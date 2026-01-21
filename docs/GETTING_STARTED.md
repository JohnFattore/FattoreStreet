# Getting Started

This guide will help you set up the **Portfolio Manager** project locally for development.

## Prerequisites

Ensure you have the following installed on your machine:
- **Python 3.8+** (for Django backend)
- **Node.js 18+** & **npm** (for React frontend)
- **Docker** & **Docker Compose** (optional, for running in containerized mode)
- **PostgreSQL** (optional, SQLite is used by default for dev)

---

## 🚀 Quick Start (Development)

### 1. Clone the Repository

```bash
git clone https://github.com/JohnFattore/FattoreStreet.git
cd Portfolio-Manager-Backend
```

### 2. Backend Setup (Django)

The backend handles the API, database, and business logic.

1.  **Navigate to the backend directory:**
    ```bash
    cd django
    ```

2.  **Create and activate a virtual environment:**
    ```bash
    python3 -m venv venv
    source venv/bin/activate  # On Windows: venv\Scripts\activate
    ```

3.  **Install dependencies:**
    ```bash
    pip install -r requirements.txt
    ```

4.  **Run Migrations:**
    Initialize the database (SQLite by default).
    ```bash
    python3 manage.py migrate
    ```

5.  **Start the Development Server:**
    ```bash
    python3 manage.py runserver
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
    celery -A mysite worker --beat -E -n beat
    ```

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

### Backend Tests
```bash
cd django
python3 manage.py test
```

### Frontend Tests
```bash
cd react-app
npm test
```
