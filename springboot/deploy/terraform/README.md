# Nightly IEX HIST load on Fargate

Runs the bulky nightly price load (`IexHistService.loadHistData`) as an **ephemeral Fargate task**
instead of keeping 4 GB of RAM provisioned 24/7 on the EC2 box. The same Spring Boot jar runs the
API on EC2 (default `APP_RUN_MODE=server`) and the one-shot load on Fargate (`APP_RUN_MODE=hist-load`,
via `HistLoadRunner`, which runs the load once and exits).

## Architecture

```
EventBridge Scheduler (cron)
        │ RunTask
        ▼
Fargate task (ARM64, 1 vCPU / 4 GB, public subnet + public IP, no NAT)
   APP_RUN_MODE=hist-load  → HistLoadRunner → loadHistData() → System.exit
        │ 5432
        ▼
Postgres on the EC2 instance  (its SG gets one ingress rule from the task SG)
```

Cost shape: you pay for ~4 GB only for the minutes the task runs each night, not all month.
**No NAT gateway** — the task uses a public subnet with a public IP so it can reach ECR and
`iextrading.com` directly (a NAT would cost ~$32/mo and defeat the purpose).

## One-time prerequisites

1. **Secret** — the task reads the single `fattorestreet/env` secret (the same JSON secret the EC2
   containers use), pulling `POSTGRES_PASSWORD` and `SECRET_KEY` out of it by key. It should already
   exist; if not, create it as a JSON object:
   ```bash
   aws secretsmanager create-secret --name fattorestreet/env --secret-string '{
     "POSTGRES_PASSWORD": "<postgres-password>",
     "SECRET_KEY": "<django-secret-key>"
   }'   # ...plus the other app keys; see deploy/run.sh
   ```
   Put its ARN in `terraform.tfvars` as `env_secret_arn`. (If it uses a **customer-managed KMS key**,
   also grant the execution role `kms:Decrypt` on that key — the default `aws/secretsmanager` key
   needs nothing extra.)

2. **tfvars** — `cp terraform.tfvars.example terraform.tfvars` and fill in VPC, subnets, the EC2
   instance's security group id, and its private IP/DNS for `db_host`.

## Deploy

```bash
cd springboot/deploy/terraform
terraform init
terraform apply        # creates ECR repo, cluster, task def, IAM, SG rule, schedule
```

Then build the **ARM64** image and push it to the ECR repo Terraform created:

```bash
REPO=$(terraform output -raw ecr_repository_url)
REGION=$(terraform output -raw ... 2>/dev/null || echo us-east-1)   # or your var.aws_region
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPO%/*}"

# Build for linux/arm64 to match runtime_platform = ARM64. Build context is springboot/.
cd ../..                       # -> springboot/
docker buildx build --platform linux/arm64 -t "$REPO:latest" --push .
```

> The Dockerfile needs no changes — run mode is selected purely by the `APP_RUN_MODE` env var that
> the task definition sets.

## Test a run before trusting the schedule

```bash
CLUSTER=$(terraform output -raw ecs_cluster_name)
FAMILY=$(terraform output -raw task_definition_family)
SG=$(terraform output -raw task_security_group_id)

aws ecs run-task \
  --cluster "$CLUSTER" \
  --task-definition "$FAMILY" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxx],securityGroups=[$SG],assignPublicIp=ENABLED}"

# Watch output:
aws logs tail "$(terraform output -raw log_group_name)" --follow
```

Confirm it connects to Postgres, processes days, and exits `0`. **Profile peak memory** from the
task's CloudWatch metrics — if it sits well under 4 GB, set `task_memory = 2048` and re-apply.

## Switch the schedule over (manual, one step)

The old trigger is the django-celery-beat schedule for `portfolio.tasks.load_iex_hist`, stored as a
**database row** (`CELERY_BEAT_SCHEDULER = DatabaseScheduler`), not in code. Disable it so the job
isn't run twice:

- Django admin → **Periodic tasks** → uncheck/delete the `load_iex_hist` entry.

The `load_iex_hist` Celery task and the `/admin/load-hist` HTTP endpoint stay in place for manual
runs; only the *schedule* moves to EventBridge.

To pause the Fargate schedule without destroying anything: `schedule_enabled = false` + `terraform apply`.

## Tuning knobs

| Variable | Default | Notes |
|----------|---------|-------|
| `task_memory` | `4096` | Lower to `2048` after profiling a real run. |
| `hist_load_days` | `20` | Days walked back; already-loaded days are skipped (idempotent). |
| `schedule_expression` / `schedule_timezone` | `cron(0 2 * * ? *)` / `America/New_York` | When it runs. |
| `schedule_enabled` | `true` | Toggle the nightly trigger. 