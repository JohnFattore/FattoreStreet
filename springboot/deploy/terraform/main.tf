locals {
  container_name       = "hist-load"
  tags                 = merge({ Project = "FattoreStreet", Component = "springboot-hist-load" }, var.tags)
  index_container_name = "index-load"
  index_tags           = merge({ Project = "FattoreStreet", Component = "springboot-index-load" }, var.tags)
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
  family                   = var.name_prefix
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
        { name = "DB_URL", value = "jdbc:postgresql://${var.db_host}:${var.db_port}/${var.db_name}" },
        { name = "DB_USERNAME", value = var.db_username },
      ]

      # Pull individual keys out of the single fattorestreet/env JSON secret.
      # ECS syntax: <secret-arn>:<json-key>:<version-stage>:<version-id> (last two left empty).
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SECRET_KEY", valueFrom = "${var.env_secret_arn}:SECRET_KEY::" },
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
    resources = [
      "${aws_ecs_task_definition.this.arn_without_revision}:*",
      aws_ecs_task_definition.this.arn,
      "${aws_ecs_task_definition.index_load.arn_without_revision}:*",
      aws_ecs_task_definition.index_load.arn,
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
  family                   = var.index_load_name_prefix
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
      secrets = [
        { name = "POSTGRES_PASSWORD", valueFrom = "${var.env_secret_arn}:POSTGRES_PASSWORD::" },
        { name = "SECRET_KEY", valueFrom = "${var.env_secret_arn}:SECRET_KEY::" },
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
