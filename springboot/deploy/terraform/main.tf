data "aws_caller_identity" "current" {}

locals {
  container_name              = "hist-load"
  tags                        = merge({ Project = "FattoreStreet", Component = "springboot-hist-load" }, var.tags)
  index_container_name        = "index-load"
  index_tags                  = merge({ Project = "FattoreStreet", Component = "springboot-index-load" }, var.tags)
  fundamentals_container_name = "fundamentals-load"
  fundamentals_tags           = merge({ Project = "FattoreStreet", Component = "springboot-fundamentals-load" }, var.tags)
  validate_container_name     = "validate-prices"
  validate_tags               = merge({ Project = "FattoreStreet", Component = "springboot-validate-prices" }, var.tags)
  asset_container_name        = "asset-load"
  asset_tags                  = merge({ Project = "FattoreStreet", Component = "springboot-asset-load" }, var.tags)
}

# ---------------------------------------------------------------------------
# Container image registry. Build + push with docker buildx (see README).
# ---------------------------------------------------------------------------
resource "aws_ecr_repository" "this" {
  name                 = var.name_prefix
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.tags
}

# Expire untagged images. CI moves :latest on every merge to main, and the image it
# displaces keeps its layers forever otherwise; two such orphans had already collected
# by the time this was added.
#
# Safe despite the repo holding multi-arch pushes. `docker buildx` pushes an OCI image
# index and tags only the index: the arm64 platform manifest and the buildx attestation
# manifest both show up as untagged, so a naive reading says this rule deletes what
# :latest resolves to. It does not. Per the ECR lifecycle policy evaluation rules, "if
# an image is referenced by a manifest list, it cannot be expired or archived without
# the manifest list being deleted or archived first", and reference artifacts (the
# attestation) are expired with their subject image rather than on their own. Only
# genuinely orphaned manifests age out.
#
# Keep this rule `untagged`. Widening it to `any` would expire the tagged index itself
# and take the live image with it.
resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after ${var.ecr_untagged_retention_days} days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = var.ecr_untagged_retention_days
        }
        action = { type = "expire" }
      }
    ]
  })
}

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = local.tags
}

# ---------------------------------------------------------------------------
# Networking: a dedicated SG for the task, plus one ingress rule on the EC2
# instance's SG so the task can reach Postgres on 5432.
# ---------------------------------------------------------------------------
resource "aws_security_group" "task" {
  name_prefix = "${var.name_prefix}-task-"
  description = "Fargate IEX HIST load task"
  vpc_id      = var.vpc_id

  egress {
    description = "All outbound (ECR pull, IEX download, Postgres)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "ec2_postgres_from_task" {
  description              = "Allow Fargate HIST load task to reach Postgres"
  type                     = "ingress"
  from_port                = var.db_port
  to_port                  = var.db_port
  protocol                 = "tcp"
  security_group_id        = var.ec2_security_group_id
  source_security_group_id = aws_security_group.task.id
}

# ---------------------------------------------------------------------------
# IAM
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# Execution role: pulls the image, writes logs, reads the secrets.
resource "aws_iam_role" "execution" {
  name_prefix        = "${var.name_prefix}-exec-"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = local.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "secrets_read" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.env_secret_arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.secrets_read.json
}

# Task role: the app itself makes no AWS API calls, so this stays empty.
resource "aws_iam_role" "task" {
  name_prefix        = "${var.name_prefix}-task-"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
  tags               = local.tags
}

# ---------------------------------------------------------------------------
# ECS cluster + one-shot task definition (ARM64 to match the Graviton image).
# ---------------------------------------------------------------------------
resource "aws_ecs_cluster" "this" {
  name = var.name_prefix
  tags = local.tags
}

resource "aws_ecs_task_definition" "this" {
  family = var.name_prefix

  # Leave superseded revisions registered instead of deregistering them.
  # ecs:DeregisterTaskDefinition does not support resource-level permissions, so
  # allowing it means granting it on "*", and the developer role scopes ECS
  # writes to fattorestreet-* on purpose. Keeping old revisions costs nothing,
  # and the schedules always target the revision this module just registered.
  # It also leaves a rollback: repoint a schedule at an earlier revision.
  skip_destroy = true

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = local.container_name
      image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
      essential = true

      environment = [
        { name = "APP_RUN_MODE", value = "hist-load" },
        { name = "HIST_LOAD_DAYS", value = tostring(var.hist_load_days) },
        { name = "HIST_LOAD_EQUITY_ONLY", value = tostring(var.hist_load_equity_only) },
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
      ]

      # Pull individual keys out of the single fattorestreet/env JSON secret.
      # ECS syntax: <secret-arn>:<json-key>:<version-stage>:<version-id> (last two left empty).
      # No SECRET_KEY: that key is Django's, and nothing in this image reads it since the
      # /admin routes and their JWT verification were retired. It stays in the blob for Django.
      # SEC_CONTACT_EMAIL is not sensitive, but it lives in the same JSON secret,
      # so pulling it from there beats adding a variable that would put an email
      # address in tfvars. Without it the SEC User-Agent is empty and data.sec.gov
      # answers 403 "Undeclared Automated Tool", which fails corporate-action
      # detection after the price load.
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SEC_CONTACT_EMAIL", valueFrom = "${var.env_secret_arn}:SEC_CONTACT_EMAIL::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.this.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "hist-load"
        }
      }
    }
  ])

  tags = local.tags
}

# ---------------------------------------------------------------------------
# EventBridge Scheduler: RunTask on a cron. Replaces the Celery beat trigger.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name_prefix        = "${var.name_prefix}-sched-"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
  tags               = local.tags
}

data "aws_iam_policy_document" "scheduler_run_task" {
  statement {
    actions = ["ecs:RunTask"]
    # Wildcards only, and built from variables rather than from the task
    # definition resources. `family:*` already matches every revision, so
    # pinning the current one adds nothing.
    #
    # The string interpolation is deliberate. Referencing
    # `aws_ecs_task_definition.this.arn_without_revision` would be tidier, but it
    # makes this document unknown at plan time whenever a task definition is
    # replaced, so Terraform plans a policy update and calls iam:PutRolePolicy on
    # every apply. `iam:Put*` is an explicit deny on the local developer role, so
    # that turns every routine task definition change into CloudShell work. The
    # family names are plain variables, so composing the ARNs by hand keeps this
    # document static: it is known at plan time and does not change when a task
    # definition is replaced.
    resources = [
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.name_prefix}:*",
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.index_load_name_prefix}:*",
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.fundamentals_load_name_prefix}:*",
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.validate_prices_name_prefix}:*",
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${var.asset_load_name_prefix}:*",
    ]
    condition {
      test     = "ArnLike"
      variable = "ecs:cluster"
      values   = [aws_ecs_cluster.this.arn]
    }
  }

  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.execution.arn, aws_iam_role.task.arn]
    condition {
      test     = "StringLike"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "scheduler_run_task" {
  name   = "run-task"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler_run_task.json
}

resource "aws_scheduler_schedule" "this" {
  name       = var.name_prefix
  group_name = "default"
  state      = var.schedule_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      # Pinned to this revision; `terraform apply` rolls it forward on deploy, and the :tag image is
      # still resolved at launch so re-pushing the same tag picks up new image content.
      task_definition_arn = aws_ecs_task_definition.this.arn
      task_count          = 1
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.public_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = true
      }
    }

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

# ---------------------------------------------------------------------------
# Index load: second daily one-shot task (metrics refresh + Fattore index
# rebuilds). Shares the ECR image, cluster, IAM roles, and task SG above —
# APP_RUN_MODE selects the behavior. Scheduled after the hist load so the
# refresh sees fresh DailyPrice rows.
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "index_load" {
  name              = "/ecs/${var.index_load_name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = local.index_tags
}

resource "aws_ecs_task_definition" "index_load" {
  family = var.index_load_name_prefix

  # See the note on aws_ecs_task_definition.this.
  skip_destroy = true

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.index_load_task_cpu
  memory                   = var.index_load_task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = local.index_container_name
      image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
      essential = true

      environment = [
        { name = "APP_RUN_MODE", value = "index-load" },
        { name = "INDEX_LOAD_SCOPE", value = var.index_load_scope },
        { name = "INDEX_LOAD_MIN_PROCESSED", value = tostring(var.index_load_min_processed) },
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
      ]

      # Pull individual keys out of the single fattorestreet/env JSON secret.
      # ECS syntax: <secret-arn>:<json-key>:<version-stage>:<version-id> (last two left empty).
      # SEC_CONTACT_EMAIL is required: the metrics refresh calls data.sec.gov for
      # every ticker, and an empty User-Agent gets 403 "Undeclared Automated Tool",
      # which skips every ticker and exits the task non-zero.
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SEC_CONTACT_EMAIL", valueFrom = "${var.env_secret_arn}:SEC_CONTACT_EMAIL::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.index_load.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "index-load"
        }
      }
    }
  ])

  tags = local.index_tags
}

resource "aws_scheduler_schedule" "index_load" {
  name       = var.index_load_name_prefix
  group_name = "default"
  state      = var.index_load_schedule_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.index_load_schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      # Pinned to this revision; `terraform apply` rolls it forward on deploy, and the :tag image is
      # still resolved at launch so re-pushing the same tag picks up new image content.
      task_definition_arn = aws_ecs_task_definition.index_load.arn
      task_count          = 1
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.public_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = true
      }
    }

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

# ---------------------------------------------------------------------------
# Fundamentals load: third daily one-shot task (SEC XBRL frames sync — the
# scheduled equivalent of GET /admin/sync-frames). Shares the ECR image,
# cluster, IAM roles, and task SG above — APP_RUN_MODE selects the behavior.
#
# Scheduled clear of the other two rather than alongside them. The SEC rate
# limiter (sec.http.min-interval-ms) is per-process, so two tasks hitting
# data.sec.gov at once double the effective request rate toward SEC's ~10/s
# ceiling and earn 403s for both.
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "fundamentals_load" {
  name              = "/ecs/${var.fundamentals_load_name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = local.fundamentals_tags
}

resource "aws_ecs_task_definition" "fundamentals_load" {
  family = var.fundamentals_load_name_prefix

  # See the note on aws_ecs_task_definition.this.
  skip_destroy = true

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.fundamentals_load_task_cpu
  memory                   = var.fundamentals_load_task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = local.fundamentals_container_name
      image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
      essential = true

      environment = [
        { name = "APP_RUN_MODE", value = "fundamentals-load" },
        { name = "FUNDAMENTALS_LOAD_YEARS_BACK", value = tostring(var.fundamentals_load_years_back) },
        { name = "FUNDAMENTALS_LOAD_START_YEAR", value = tostring(var.fundamentals_load_start_year) },
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
      ]

      # Pull individual keys out of the single fattorestreet/env JSON secret.
      # ECS syntax: <secret-arn>:<json-key>:<version-stage>:<version-id> (last two left empty).
      # SEC_CONTACT_EMAIL is required: every frame fetch goes to data.sec.gov, and an empty
      # User-Agent gets 403 "Undeclared Automated Tool" on all of them, which the runner
      # detects and exits non-zero on.
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SEC_CONTACT_EMAIL", valueFrom = "${var.env_secret_arn}:SEC_CONTACT_EMAIL::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.fundamentals_load.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "fundamentals-load"
        }
      }
    }
  ])

  tags = local.fundamentals_tags
}

resource "aws_scheduler_schedule" "fundamentals_load" {
  name       = var.fundamentals_load_name_prefix
  group_name = "default"
  state      = var.fundamentals_load_schedule_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.fundamentals_load_schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      # Pinned to this revision; `terraform apply` rolls it forward on deploy, and the :tag image is
      # still resolved at launch so re-pushing the same tag picks up new image content.
      task_definition_arn = aws_ecs_task_definition.fundamentals_load.arn
      task_count          = 1
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.public_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = true
      }
    }

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

# ---------------------------------------------------------------------------
# Validate prices: weekly one-shot task (adjusted-price accuracy report — the
# scheduled equivalent of GET /admin/validate-adjusted-prices). Shares the ECR
# image, cluster, IAM roles, and task SG above.
#
# The one job in this module that writes nothing: it reads stored adjusted
# closes, compares them against a reference series served by Django, and
# publishes derived statistics to its own SNS topic. Reference data is
# diagnostics-only and may never be persisted or served, which is why this job
# has no database write path at all.
#
# Placed in the evening, clear of every observed load tail. It makes no SEC
# calls, so a long fundamentals-load tail cannot contend with it for the
# per-process SEC rate limiter.
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "validate_prices" {
  name              = "/ecs/${var.validate_prices_name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = local.validate_tags
}

# Dedicated topic rather than reusing task_failures. That topic's policy grants
# publish only to events.amazonaws.com scoped to the failure rule's ARN, so
# reusing it would mean widening the policy to the task role and mixing routine
# weekly reports into a channel where every message currently means something broke.
resource "aws_sns_topic" "validation_reports" {
  count = var.notification_email != "" ? 1 : 0
  name  = "${var.validate_prices_name_prefix}-reports"
  tags  = local.validate_tags
}

resource "aws_sns_topic_subscription" "validation_reports_email" {
  count     = var.notification_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.validation_reports[0].arn
  protocol  = "email"
  endpoint  = var.notification_email
}

# The task role is otherwise empty (no other job calls an AWS API). This is the
# one grant it needs, scoped to the report topic only.
data "aws_iam_policy_document" "task_publish_reports" {
  count = var.notification_email != "" ? 1 : 0

  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.validation_reports[0].arn]
  }
}

resource "aws_iam_role_policy" "task_publish_reports" {
  count  = var.notification_email != "" ? 1 : 0
  name   = "publish-validation-reports"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_publish_reports[0].json
}

resource "aws_ecs_task_definition" "validate_prices" {
  family = var.validate_prices_name_prefix

  # See the note on aws_ecs_task_definition.this.
  skip_destroy = true

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.validate_prices_task_cpu
  memory                   = var.validate_prices_task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = local.validate_container_name
      image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
      essential = true

      environment = concat([
        { name = "APP_RUN_MODE", value = "validate-prices" },
        { name = "VALIDATE_PRICES_INDEX_CODE", value = var.validate_prices_index_code },
        { name = "VALIDATE_PRICES_MIN_DATE", value = var.validate_prices_min_date },
        { name = "VALIDATE_PRICES_MAX_TICKERS", value = tostring(var.validate_prices_max_tickers) },
        { name = "DJANGO_PORTFOLIO_BASE_URL", value = var.django_portfolio_base_url },
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
        ], var.notification_email != "" ? [
        { name = "VALIDATE_PRICES_SNS_TOPIC_ARN", value = aws_sns_topic.validation_reports[0].arn },
      ] : [])

      # No SECRET_KEY: nothing in the image reads it, and no SEC_CONTACT_EMAIL either —
      # this job talks to Django and Postgres, never to data.sec.gov.
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.validate_prices.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "validate-prices"
        }
      }
    }
  ])

  tags = local.validate_tags
}

resource "aws_scheduler_schedule" "validate_prices" {
  name       = var.validate_prices_name_prefix
  group_name = "default"
  state      = var.validate_prices_schedule_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.validate_prices_schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      # Pinned to this revision; `terraform apply` rolls it forward on deploy, and the :tag image is
      # still resolved at launch so re-pushing the same tag picks up new image content.
      task_definition_arn = aws_ecs_task_definition.validate_prices.arn
      task_count          = 1
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.public_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = true
      }
    }

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

# ---------------------------------------------------------------------------
# Asset load: monthly one-shot task (SEC ticker universe + ETF identity
# enrichment — the scheduled equivalent of GET /admin/asset-load). Shares the
# ECR image, cluster, IAM roles, and task SG above.
#
# Monthly rather than nightly: the SEC ticker indexes change slowly, and both
# Asset and Listing upsert by their natural keys, so re-running only costs SEC
# requests. Placed at 22:00 to stay clear of the validation report's Sunday
# 20:00 slot in months whose 1st is a Sunday.
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "asset_load" {
  name              = "/ecs/${var.asset_load_name_prefix}"
  retention_in_days = var.log_retention_days
  tags              = local.asset_tags
}

resource "aws_ecs_task_definition" "asset_load" {
  family = var.asset_load_name_prefix

  # See the note on aws_ecs_task_definition.this.
  skip_destroy = true

  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.asset_load_task_cpu
  memory                   = var.asset_load_task_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  container_definitions = jsonencode([
    {
      name      = local.asset_container_name
      image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
      essential = true

      environment = [
        { name = "APP_RUN_MODE", value = "asset-load" },
        { name = "ASSET_LOAD_OVERWRITE_EXISTING", value = tostring(var.asset_load_overwrite_existing) },
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
      ]

      # Pull individual keys out of the single fattorestreet/env JSON secret.
      # ECS syntax: <secret-arn>:<json-key>:<version-stage>:<version-id> (last two left empty).
      # SEC_CONTACT_EMAIL is required: both ticker indexes come from data.sec.gov, and an empty
      # User-Agent gets 403 "Undeclared Automated Tool" on all of them — which the runner detects
      # as a zero-ticker load and exits non-zero on. No SECRET_KEY: nothing in the image reads it.
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SEC_CONTACT_EMAIL", valueFrom = "${var.env_secret_arn}:SEC_CONTACT_EMAIL::" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.asset_load.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "asset-load"
        }
      }
    }
  ])

  tags = local.asset_tags
}

resource "aws_scheduler_schedule" "asset_load" {
  name       = var.asset_load_name_prefix
  group_name = "default"
  state      = var.asset_load_schedule_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.asset_load_schedule_expression
  schedule_expression_timezone = var.schedule_timezone

  target {
    arn      = aws_ecs_cluster.this.arn
    role_arn = aws_iam_role.scheduler.arn

    ecs_parameters {
      # Pinned to this revision; `terraform apply` rolls it forward on deploy, and the :tag image is
      # still resolved at launch so re-pushing the same tag picks up new image content.
      task_definition_arn = aws_ecs_task_definition.asset_load.arn
      task_count          = 1
      launch_type         = "FARGATE"

      network_configuration {
        subnets          = var.public_subnet_ids
        security_groups  = [aws_security_group.task.id]
        assign_public_ip = true
      }
    }

    retry_policy {
      maximum_retry_attempts = 0
    }
  }
}

# ---------------------------------------------------------------------------
# Failure alerting: email (SNS) when any task in the cluster stops with a
# non-zero exit code or never starts. Every job here exits 1 only on total
# failure (partial errors self-heal next run), so an alert means that run did
# no useful work. Created only when notification_email is set.
#
# The rule matches on clusterArn rather than per-task, so new schedules fall
# inside its blast radius automatically — including manual `aws ecs run-task`
# invocations, which is desirable.
# ---------------------------------------------------------------------------
resource "aws_sns_topic" "task_failures" {
  count = var.notification_email != "" ? 1 : 0
  name  = "${var.name_prefix}-task-failures"
  tags  = local.tags
}

resource "aws_sns_topic_subscription" "task_failures_email" {
  count     = var.notification_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.task_failures[0].arn
  protocol  = "email"
  endpoint  = var.notification_email
}

data "aws_iam_policy_document" "task_failures_topic" {
  count = var.notification_email != "" ? 1 : 0

  statement {
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.task_failures[0].arn]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_cloudwatch_event_rule.task_failures[0].arn]
    }
  }
}

resource "aws_sns_topic_policy" "task_failures" {
  count  = var.notification_email != "" ? 1 : 0
  arn    = aws_sns_topic.task_failures[0].arn
  policy = data.aws_iam_policy_document.task_failures_topic[0].json
}

resource "aws_cloudwatch_event_rule" "task_failures" {
  count       = var.notification_email != "" ? 1 : 0
  name        = "${var.name_prefix}-task-failures"
  description = "A scheduled task (hist, index, fundamentals, asset load or price validation) stopped with a non-zero exit code or failed to start"

  event_pattern = jsonencode({
    source      = ["aws.ecs"]
    detail-type = ["ECS Task State Change"]
    detail = {
      clusterArn = [aws_ecs_cluster.this.arn]
      lastStatus = ["STOPPED"]
      "$or" = [
        { containers = { exitCode = [{ anything-but = [0] }] } },
        { stopCode = ["TaskFailedToStart"] }
      ]
    }
  })

  tags = local.tags
}

resource "aws_cloudwatch_event_target" "task_failures_sns" {
  count = var.notification_email != "" ? 1 : 0
  rule  = aws_cloudwatch_event_rule.task_failures[0].name
  arn   = aws_sns_topic.task_failures[0].arn

  input_transformer {
    input_paths = {
      group    = "$.detail.group"
      exitCode = "$.detail.containers[0].exitCode"
      reason   = "$.detail.stoppedReason"
      taskArn  = "$.detail.taskArn"
    }
    # Output must be a JSON value; the surrounding quotes make it a JSON string.
    input_template = "\"FattoreStreet scheduled task failed: <group> stopped with exit code <exitCode>. Reason: <reason>. Task: <taskArn>. Logs: /ecs/${var.name_prefix}, /ecs/${var.index_load_name_prefix}, /ecs/${var.fundamentals_load_name_prefix}, /ecs/${var.asset_load_name_prefix} or /ecs/${var.validate_prices_name_prefix}.\""
  }
}
