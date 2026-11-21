# ---------------------------------------------------------------------------
# App container log groups (EC2 docker-compose awslogs driver) and scheduled-job
# run history (every ECS task STOPPED event, success or failure, for the admin
# monitoring dashboard).
# ---------------------------------------------------------------------------

import {
  to = aws_cloudwatch_log_group.nginx
  id = "nginx-logs"
}

resource "aws_cloudwatch_log_group" "nginx" {
  name              = "nginx-logs"
  retention_in_days = var.log_retention_days
  tags              = merge({ Project = "FattoreStreet", Component = "nginx" }, var.tags)
}

resource "aws_cloudwatch_log_group" "django" {
  name              = "/fattorestreet/django"
  retention_in_days = var.log_retention_days
  tags              = merge({ Project = "FattoreStreet", Component = "django" }, var.tags)
}

resource "aws_cloudwatch_log_group" "springboot" {
  name              = "/fattorestreet/springboot"
  retention_in_days = var.log_retention_days
  tags              = merge({ Project = "FattoreStreet", Component = "springboot" }, var.tags)
}

resource "aws_cloudwatch_log_group" "job_runs" {
  name              = "/fattorestreet/job-runs"
  retention_in_days = 90
  tags              = merge({ Project = "FattoreStreet", Component = "job-runs" }, var.tags)
}

data "aws_iam_policy_document" "eventbridge_to_job_runs_logs" {
  statement {
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.job_runs.arn}:*"]

    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com", "delivery.logs.amazonaws.com"]
    }
  }
}

resource "aws_cloudwatch_log_resource_policy" "eventbridge_job_runs" {
  policy_name     = "${var.name_prefix}-eventbridge-job-runs"
  policy_document = data.aws_iam_policy_document.eventbridge_to_job_runs_logs.json
}

resource "aws_cloudwatch_event_rule" "job_runs" {
  name        = "${var.name_prefix}-job-runs"
  description = "Record every completed scheduled task run (success or failure) in CloudWatch Logs for the admin monitoring dashboard"

  event_pattern = jsonencode({
    source      = ["aws.ecs"]
    detail-type = ["ECS Task State Change"]
    detail = {
      clusterArn = [aws_ecs_cluster.this.arn]
      lastStatus = ["STOPPED"]
    }
  })

  tags = local.tags
}

resource "aws_cloudwatch_event_target" "job_runs_logs" {
  rule       = aws_cloudwatch_event_rule.job_runs.name
  arn        = aws_cloudwatch_log_group.job_runs.arn
  depends_on = [aws_cloudwatch_log_resource_policy.eventbridge_job_runs]
}
