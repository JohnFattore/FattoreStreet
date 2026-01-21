# Deployment & Infrastructure

The **Portfolio Manager** is designed to be cloud-agnostic but is currently optimized for AWS.

## ☁️ Production Hosting (AWS)

The production environment currently uses:
- **AWS Fargate (ECS)**: Runs the application containers (Gunicorn & Nginx).
- **AWS RDS**: Managed PostgreSQL database.
- **Application Load Balancer (ALB)**: Handles HTTPS termination and traffic routing.
- **Route 53**: DNS management.

### Dev Ops System Overview
1.  **DNS (Route 53)**:
    - `A` record points to the ALB.
    - `CNAME` validates the SSL certificate.
2.  **Load Balancer (ALB)**:
    - Listeners on Port 80 (HTTP) redirect to Port 443 (HTTPS).
    - Port 443 forwards to the Fargate service Target Group.
3.  **Fargate Service**:
    - **Nginx Container**: Reverse proxy, serves static files (React build), forwards API requests to Gunicorn.
    - **Gunicorn Container**: Runs the Django WSGI server.
4.  **Database**:
    - Gunicorn communicates with RDS (Postgres) via private VPC networking.

### Domain Naming
- **Certificate**: `*.fattore.com` (Wildcard support).
- **Hosted Zone**: `fattorestreet.com`.

## 🚀 CI/CD Pipeline

The project uses a continuous integration and deployment pipeline.
- **Build**: `kubernetes/build.sh` script handles building Docker images.
- **Watchtower**: A container running in the cluster monitors for new image versions and automatically updates the running services.
- **Tests**: Automated tests run for both Django and React before build.

## 📦 Kubernetes

While currently running via ECS/Docker Compose strategies, the `kubernetes/` folder contains manifests for migrating to a full K8s cluster.
- This allows for auto-scaling of pods if traffic increases.
- Useful for managing the complex interaction between Celery workers, Redis, and the Web server.

## 🔄 Environments

### Staging
- **Goal**: Mirror production as closely as possible.
- **Config**: Uses `nginx.conf` (local), updates Port mappings.
- **Network**: Docker Bridge network (172.x.x.x) vs Host networking in some AWS configurations.

### Production
- **Config**: Uses production `nginx.conf` with SSL termination handled by ALB (so Nginx sees HTTP).
