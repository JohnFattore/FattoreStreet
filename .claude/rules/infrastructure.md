---
paths:
  - "deploy/**"
  - "springboot/deploy/**"
  - "nginx/**"
  - "**/Dockerfile"
  - "**/docker-compose*"
  - "**/*.tf"
---

# Infrastructure Conventions

There is no Kubernetes here. The stack is Docker Compose on a single Graviton EC2
instance, plus two one-shot Fargate tasks managed by Terraform.

## Docker

- Each service (`react-app`, `django`, `springboot`) has its own Dockerfile
- Use multi-stage builds where applicable (Vite build stage then Nginx serve stage for the React app)
- Images are ARM64 (Graviton). Build with `docker buildx build --platform linux/arm64`
- Images publish to GHCR as `ghcr.io/johnfattore/{nginx,django,springboot}`, tagged `latest` + commit SHA

## Deployment (`deploy/`)

- `deploy/docker-compose.yml` is the deploy unit (django, springboot, nginx);
  `deploy/docker-compose.infra.yml` holds stateful infra (postgres, redis, pgadmin4)
  and is never touched by a routine deploy
- `deploy/deploy.sh` is the idempotent converge script run on the EC2 host as root by
  SSM RunCommand; `deploy/compose.sh` is the operator runbook; `deploy/DEPLOY.md`
  documents one-time setup
- **CI is the build-and-publish path.** `.github/workflows/docker-build.yml` builds on
  merge to `main`, pushes to GHCR, and deploys via `aws ssm send-command` targeting
  instances tagged `App=fattorestreet`. `deploy/build.sh` is legacy/emergency only
- `deploy/run.sh` is the pre-compose `docker run` reference, kept for historical detail;
  don't extend it

## Scheduled Fargate tasks (`springboot/deploy/terraform/`)

- The nightly IEX HIST price load and index load run as **ephemeral Fargate tasks**, not
  inside Django and not on the EC2 box. Terraform is the source of truth for the ECR repo,
  ECS cluster, both task definitions, IAM roles, security group rule, EventBridge
  schedules, and SNS failure alerting
- Local state, no remote backend. `terraform apply` from `springboot/deploy/terraform/`
- Run mode is selected purely by the `APP_RUN_MODE` env var (`server`, `hist-load`,
  `index-load`) on the shared image. Adding a mode means a new `ApplicationRunner` gated
  by `@ConditionalOnProperty(name = "app.run-mode", ...)` that calls `System.exit`
- CI publishes the springboot image to **both** GHCR and ECR on merge to `main`, and the
  task definitions pin `:latest`, so merging is what deploys a new run mode
- **`terraform apply` is not a deploy.** A task definition can reference a runner the ECR
  image does not contain, which silently degrades the task to `server` mode and leaves it
  running (and billing) forever. Merge to `main` first, then apply, then check the cluster
- A healthy cluster has **zero** running tasks between scheduled runs. Use the
  `aws-inspect` skill to check live state

## Nginx

- Config in `nginx/nginx.conf` (`nginx.dev.conf` for local)
- Reverse proxy routing to the three services; serves the static frontend assets directly

## Secrets

- One AWS Secrets Manager secret, `fattorestreet/env`, a flat JSON shared by every service
- EC2 containers receive only `SECRETS_ARN` and fetch the rest at start via each image's
  `docker-entrypoint.sh`; Fargate injects individual keys natively through the task
  definition's `secrets` block, so the entrypoint no-ops there
- Never hardcode credentials or connection strings. See `.claude/rules/secrets-check.md`

## General

- Always use environment variables for service URLs, database credentials, and API keys
- Keep infrastructure config declarative and version-controlled
- Region is `us-east-1` everywhere
