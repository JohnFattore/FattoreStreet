# Nightly Fargate loads (IEX HIST + index)

Runs two nightly loads as **ephemeral Fargate tasks** instead of keeping the RAM provisioned 24/7
on the EC2 box. The same Spring Boot jar runs the API on EC2 (default `APP_RUN_MODE=server`) and
the one-shot loads on Fargate — run mode is selected purely by the `APP_RUN_MODE` env var:

- **`hist-load`** (`HistLoadRunner`) — the bulky IEX price load (`IexHistService.loadHistData`),
  which runs once and exits. After a successful load the task also runs corporate-action price
  adjustment (`adjustAllTickers`, rolling SEC re-detection included); set
  `HIST_LOAD_ADJUST_ENABLED=false` on the task to skip it.
- **`index-load`** (`IndexLoadRunner`) — index metrics refresh (SEC companyfacts/submissions for
  the Russell 1000 universe) followed by cap-ranked rebuilds of FAT100, FAT1000, FAT50. Scheduled
  a few hours **after** the hist load so fresh `DailyPrice` rows exist. Guard: if the refresh
  processes fewer than `INDEX_LOAD_MIN_PROCESSED` listings (default 800), the task skips the
  rebuild and exits `1` — the rebuild deletes members by index code, so rebuilding after a
  mostly-failed refresh (SEC outage, empty new calendar year) would shrink the live indexes.

  The offset is a fixed gap, **not a dependency**: if the hist load fails or overruns, the index
  load still fires and computes metrics from the previous day's prices (the min-processed guard
  only protects against a failed *refresh*, not stale prices). One day of staleness in cap ranks
  is acceptable; the failure alert below is what tells you the hist load needs attention.

Both tasks share the ECR repo/image, ECS cluster, IAM roles, and task security group; each has its
own task definition, log group, and schedule.

## Architecture

```
EventBridge Scheduler (cron)                EventBridge Scheduler (cron, ~3h later)
        │ RunTask                                   │ RunTask
        ▼                                           ▼
Fargate task (ARM64, 1 vCPU / 4 GB)         Fargate task (ARM64, 0.5 vCPU / 2 GB)
   APP_RUN_MODE=hist-load                      APP_RUN_MODE=index-load
   → HistLoadRunner → loadHistData()           → IndexLoadRunner → refresh metrics
   → adjustAllTickers(false) → System.exit     → rebuild FAT100/FAT1000/FAT50 → System.exit
        │ 5432 (public subnet + public IP, no NAT)  │ 5432
        ▼                                           ▼
Postgres on the EC2 instance  (its SG gets one ingress rule from the shared task SG)
```

Cost shape: you pay for ~4 GB only for the minutes the task runs each night, not all month.
**No NAT gateway** — the task uses a public subnet with a public IP so it can reach ECR and
`iextrading.com` directly (a NAT would cost ~$32/mo and defeat the purpose).

## One-time prerequisites

1. **Secret** — the task reads the single `fattorestreet/env` secret (the same JSON secret the EC2
   containers use), pulling `POSTGRES_PASSWORD`, `SECRET_KEY`, and `SEC_CONTACT_EMAIL` out of it by
   key. `SEC_CONTACT_EMAIL` is not sensitive, but it lives in the same JSON, and sourcing it there
   beats a variable that would put an email address in tfvars. It is **required**: SEC sends it as
   the User-Agent, and an empty one gets `403 Undeclared Automated Tool` on every ticker. It should
   already exist; if not, create it as a JSON object:
   ```bash
   aws secretsmanager create-secret --name fattorestreet/env --secret-string '{
     "SEC_CONTACT_EMAIL": "<your-email>",
     "POSTGRES_PASSWORD": "<postgres-password>",
     "SECRET_KEY": "<django-secret-key>"
   }'   # ...plus the other app keys; see deploy/run.sh
   ```
   Put its ARN in `terraform.tfvars` as `env_secret_arn`. (If it uses a **customer-managed KMS key**,
   also grant the execution role `kms:Decrypt` on that key — the default `aws/secretsmanager` key
   needs nothing extra.)

2. **tfvars** — `cp terraform.tfvars.example terraform.tfvars` and fill in VPC, subnets, the EC2
   instance's security group id, and its private IP/DNS for `db_host`.

## Credentials

Every command below runs under `AWS_PROFILE=fattorestreet`, which assumes the
scoped role `FattoreStreetDeveloper` rather than using an admin key. Claude Code
sets it automatically via `.claude/settings.json`; set it yourself in a plain
shell. Setup is [`deploy/iam/CONSOLE-SETUP.md`](../../../deploy/iam/CONSOLE-SETUP.md),
rationale is [`deploy/DEPLOY.md`](../../../deploy/DEPLOY.md) §5.

The role deliberately has **no IAM write**. It can read the `fattorestreet-*`
roles, so `plan` is accurate, but any change to the IAM resources in `main.tf`
(the execution, task, or scheduler role) fails on `apply` with `AccessDenied`.
That is not a bug to route around by switching credentials: apply those from
CloudShell as an admin, then resume here. It also cannot read secret *values*,
which the module never needs, since `env_secret_arn` is only ever passed through
as a string.

Editing a task definition does **not** count as an IAM change. The scheduler's
`ecs:RunTask` policy is scoped to `family:*` wildcards only, which already match
every revision, so bumping a task definition leaves the policy byte-identical and
the apply stays inside what this role can do. Adding a *new* task definition or
role still needs CloudShell.

That property starts one apply from now. The change that removed the pinned
revision ARNs rewrites `aws_iam_role_policy.scheduler_run_task` itself, and
`iam:Put*` is an explicit deny, so that one update cannot come from here.

**CloudShell is not the way to do it.** State is local and gitignored, so a
CloudShell clone has the config but neither `terraform.tfstate` nor
`terraform.tfvars`. Uploading both, applying, and downloading the state back
works, but it puts the only copy of state on a round trip, and forgetting the
return leg leaves this directory silently stale.

Do the single IAM write in the console instead, then apply from here:

1. IAM → Roles → `fattorestreet-hist-load-sched-*` → inline policy `run-task` → edit.
2. Replace the document with the one `terraform console -plan` renders:
   ```
   terraform console -plan <<< 'data.aws_iam_policy_document.scheduler_run_task.json'
   ```
   Paste it verbatim. Anything else leaves a permanent diff.
3. `terraform apply` locally. The policy now matches, so Terraform plans no
   change to it and never calls `iam:PutRolePolicy`.

Step 3 only works because the document is static (see the comment in `main.tf`).
Were it still derived from the task definition resources, it would read as
unknown at plan time, Terraform would plan the update regardless of what is
already in AWS, and the apply would fail on the deny.

## Deploy

```bash
cd springboot/deploy/terraform
terraform init
terraform apply        # creates ECR repo, cluster, task def, IAM, SG rule, schedule
```

The image comes from CI. `.github/workflows/docker-build.yml` builds the springboot image on
every merge to `main` and pushes it to **both** GHCR (for the EC2 compose stack) and this ECR
repo, tagged `latest` + commit SHA. The task definitions pin `:latest`, so a merge to `main` is
all it takes for the next nightly run to pick up new code.

> The Dockerfile needs no changes — run mode is selected purely by the `APP_RUN_MODE` env var that
> the task definition sets.

**`terraform apply` is not a deploy.** Terraform can add a task definition referencing a runner
that the ECR image does not yet contain; the container then falls through to `APP_RUN_MODE=server`,
boots Tomcat, and **never exits**, so the task runs (and bills) forever and the failure alert below
never fires, because it only triggers on a task that *stops*. This happened once: `IndexLoadRunner`
landed 2026-07-18 against an image built 2026-07-01, and six orphaned tasks accumulated at ~$17/mo
each before anyone looked. If you add a run mode, merge it to `main` (so CI publishes the image)
before or with the `terraform apply`, and check the cluster afterwards.

To build and push by hand (CI unavailable, or bootstrapping before the repo exists):

```bash
REPO=$(terraform output -raw ecr_repository_url)
REGION=us-east-1   # your var.aws_region
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "${REPO%/*}"

# Build for linux/arm64 to match runtime_platform = ARM64. Build context is springboot/.
cd ../..                       # -> springboot/
docker buildx build --platform linux/arm64 -t "$REPO:latest" --push .
```

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

Same shape for the index load (use the `index_load_*` outputs; it shares the cluster and SG):

```bash
aws ecs run-task \
  --cluster "$CLUSTER" \
  --task-definition "$(terraform output -raw index_load_task_definition_family)" \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxx],securityGroups=[$SG],assignPublicIp=ENABLED}"

aws logs tail "$(terraform output -raw index_load_log_group_name)" --follow
```

Confirm the refresh progress lines reach 100%, three `Rebuilt FAT...` lines appear, and the exit
code is `0` (`aws ecs describe-tasks` → `containers[0].exitCode`). Then check
`GET /index-members?code=FAT50` (and `FAT100`/`FAT1000`) reflects the run. A cheap smoke test
before a full run: override `INDEX_LOAD_TICKER=AAPL` on the task
(`--overrides '{"containerOverrides":[{"name":"index-load","environment":[{"name":"INDEX_LOAD_TICKER","value":"AAPL"}]}]}'`)
to refresh one ticker and still rebuild.

## Schedule cutover (done)

This schedule replaced the old django-celery-beat trigger for `portfolio.tasks.load_iex_hist`;
Celery has since been removed from the Django service entirely. The Spring Boot
`/admin/load-hist` HTTP endpoint stays in place for manual runs.

To pause either Fargate schedule without destroying anything: `schedule_enabled = false` (hist load)
or `index_load_schedule_enabled = false` (index load) + `terraform apply`.

The index load had no Celery/beat trigger to replace — it was manual-only via the admin endpoints
(`POST /admin/indexes/refresh-stocks`, `POST /admin/indexes/rebuild`), which stay in place for
manual runs.

## Failure alerting

Set `notification_email` in `terraform.tfvars` and an EventBridge rule + SNS topic email you
whenever a task in the cluster stops with a non-zero exit code or fails to start. After the first
`terraform apply`, **confirm the subscription** from the email AWS sends (until then, no alerts
are delivered).

Both runners exit `1` only on total failure — the load throwing, every attempted day failing, or
the index refresh falling below the rebuild guard. Partial errors (some days, some tickers) exit
`0` and self-heal on the next night's idempotent run, so an alert always means that night's run
did no useful work. There are no automatic retries (`maximum_retry_attempts = 0`); on an alert,
check the CloudWatch logs and, once fixed, either wait for the next scheduled run or re-run
manually with the `aws ecs run-task` commands above.

Leave `notification_email = ""` to skip creating the alerting resources entirely.

## Tuning knobs

| Variable | Default | Notes |
|----------|---------|-------|
| `task_memory` | `4096` | Lower to `2048` after profiling a real run. |
| `hist_load_days` | `20` | Days walked back; already-loaded days are skipped (idempotent). |
| `schedule_expression` / `schedule_timezone` | `cron(30 6 * * ? *)` / `Etc/UTC` | When the hist load runs (tfvars example: `cron(0 2 * * ? *)` / `America/New_York`). |
| `schedule_enabled` | `true` | Toggle the nightly hist-load trigger. |
| `index_load_task_cpu` / `index_load_task_memory` | `512` / `2048` | The index load is SEC-rate-limit bound (mostly idle); profile the first real run. |
| `index_load_scope` | `russell1000` | Metrics refresh scope (`russell1000` or `all`). |
| `index_load_min_processed` | `800` | Rebuild guard threshold; below it the task keeps yesterday's members and exits `1`. |
| `index_load_schedule_expression` | `cron(30 9 * * ? *)` | When the index load runs (tfvars example: `cron(0 5 * * ? *)`); shares `schedule_timezone`. Keep it well after the hist load. |
| `index_load_schedule_enabled` | `true` | Toggle the nightly index-load trigger. |
| `notification_email` | `""` | Email alerted when a task exits non-zero or fails to start; `""` disables alerting. | 