variable "aws_region" {
  description = "AWS region (same region/VPC as the EC2 instance running Postgres)."
  type        = string
}

variable "name_prefix" {
  description = "Prefix applied to all created resource names."
  type        = string
  default     = "fattorestreet-hist-load"
}

# ---------------------------------------------------------------------------
# Networking — must match the VPC where the EC2 Postgres instance lives.
# ---------------------------------------------------------------------------

variable "vpc_id" {
  description = "VPC id of the EC2 instance running Postgres."
  type        = string
}

variable "public_subnet_ids" {
  description = <<-EOT
    Public subnet ids for the Fargate task. Public + assign_public_ip lets the task pull the image
    from ECR and download from iextrading.com WITHOUT a NAT gateway (a NAT would cost ~$32/mo and
    erase the savings). Use subnets in the same AZ(s) as the EC2 instance to keep DB latency low.
  EOT
  type        = list(string)
}

variable "ec2_security_group_id" {
  description = <<-EOT
    Security group attached to the EC2 instance running Postgres. Terraform adds a single ingress
    rule to it allowing 5432 from the Fargate task's security group.
  EOT
  type        = string
}

# ---------------------------------------------------------------------------
# Database connection (Postgres on the EC2 box).
# ---------------------------------------------------------------------------

variable "db_host" {
  description = "Private IP or private DNS name of the EC2 instance running Postgres."
  type        = string
}

variable "db_port" {
  description = "Postgres port."
  type        = number
  default     = 5432
}

variable "db_name" {
  description = "Postgres database name the Spring Boot service uses."
  type        = string
  default     = "springboot"
}

variable "db_username" {
  description = "Postgres username (not treated as a secret)."
  type        = string
  default     = "postgres"
}

variable "env_secret_arn" {
  description = <<-EOT
    Secrets Manager ARN of the single app-config secret (fattorestreet/env), a JSON object of
    KEY -> value pairs. The task pulls POSTGRES_PASSWORD and SECRET_KEY out of it by key, so the
    EC2 containers and this Fargate task share one secret. Pass the base ARN (no :key suffix);
    Terraform appends the JSON-key selectors.
  EOT
  type        = string
}

# ---------------------------------------------------------------------------
# Image + task sizing.
# ---------------------------------------------------------------------------

variable "image_tag" {
  description = "Image tag to run from the ECR repo created by this module."
  type        = string
  default     = "latest"
}

variable "task_cpu" {
  description = "Fargate task CPU units (1024 = 1 vCPU)."
  type        = number
  default     = 1024
}

variable "task_memory" {
  description = "Fargate task memory (MiB). 4096 mirrors the t4g.medium headroom; tune down after profiling."
  type        = number
  default     = 4096
}

variable "hist_load_days" {
  description = "Number of trading days the load walks back (HIST_LOAD_DAYS). Already-loaded days are skipped."
  type        = number
  default     = 20
}

variable "hist_load_equity_only" {
  description = <<-EOT
    Restrict the post-load corporate-action adjustment to non-fund tickers (HIST_LOAD_EQUITY_ONLY).
    ETF detection has no XBRL equivalent and brute-force fetches hundreds of filings per fund against
    the SEC rate limit, which is what stretched nightly runs past 17 hours. Set false to include funds
    again, accepting that runtime.
  EOT
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Schedule.
# ---------------------------------------------------------------------------

variable "schedule_expression" {
  description = "EventBridge Scheduler cron. Default 06:30 UTC daily (after US markets settle)."
  type        = string
  default     = "cron(30 6 * * ? *)"
}

variable "schedule_timezone" {
  description = "IANA timezone for schedule_expression (e.g. America/New_York)."
  type        = string
  default     = "Etc/UTC"
}

variable "schedule_enabled" {
  description = "Whether the schedule is ENABLED. Set false to deploy the task without auto-running it yet."
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# Index load (second daily job; shares the cluster, ECR image, IAM roles, and
# task SG with the hist load — only the task definition, log group, and
# schedule are its own).
# ---------------------------------------------------------------------------

variable "index_load_name_prefix" {
  description = "Name for the index-load task definition family, log group, and schedule."
  type        = string
  default     = "fattorestreet-index-load"
}

variable "index_load_task_cpu" {
  description = "Index-load Fargate task CPU units. The load is SEC-rate-limit bound (mostly idle), so 0.5 vCPU suffices."
  type        = number
  default     = 512
}

variable "index_load_task_memory" {
  description = "Index-load Fargate task memory (MiB). Parses one SEC companyfacts JSON at a time; tune after profiling."
  type        = number
  default     = 2048
}

variable "index_load_scope" {
  description = "Metrics refresh scope (INDEX_LOAD_SCOPE): russell1000 (IWB holdings universe) or all."
  type        = string
  default     = "russell1000"
}

variable "index_load_min_processed" {
  description = <<-EOT
    Minimum listings the metrics refresh must process before the rebuild runs
    (INDEX_LOAD_MIN_PROCESSED). Below it the task keeps yesterday's index members and exits 1 —
    the rebuild deletes members by index code, so rebuilding after a mostly-failed refresh
    (SEC outage, empty new calendar year) would shrink the live indexes.
  EOT
  type        = number
  default     = 800
}

variable "index_load_schedule_expression" {
  description = "EventBridge Scheduler cron for the index load. Default 09:30 UTC daily, 3h after the hist load so fresh DailyPrice rows exist."
  type        = string
  default     = "cron(30 9 * * ? *)"
}

variable "index_load_schedule_enabled" {
  description = "Whether the index-load schedule is ENABLED. Set false to deploy the task without auto-running it yet."
  type        = bool
  default     = true
}

variable "notification_email" {
  description = <<-EOT
    Email address alerted (via an SNS topic + EventBridge rule) when a task in the cluster stops
    with a non-zero exit code or fails to start. Both nightly loads exit 1 only on total failure,
    so an alert means that night's run did no useful work. Empty string (the default) disables
    the alerting resources entirely. The subscription must be confirmed once from the
    confirmation email AWS sends after `terraform apply`.
  EOT
  type        = string
  default     = ""
}

variable "log_retention_days" {
  description = "CloudWatch log retention for the task."
  type        = number
  default     = 30
}

variable "ecr_untagged_retention_days" {
  description = <<-EOT
    Days an untagged ECR image is kept before the lifecycle policy expires it. Untagged images here
    are superseded :latest targets left behind by the next CI push. Keep this comfortably longer
    than the window in which you would want to roll back by digest.
  EOT
  type        = number
  default     = 14
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default     = {}
}
