output "ecr_repository_url" {
  description = "Push the image here (used by the buildx command in README)."
  value       = aws_ecr_repository.this.repository_url
}

output "ecs_cluster_name" {
  description = "ECS cluster name (for manual `aws ecs run-task` test runs)."
  value       = aws_ecs_cluster.this.name
}

output "task_definition_family" {
  description = "Task definition family."
  value       = aws_ecs_task_definition.this.family
}

output "task_security_group_id" {
  description = "Security group attached to the task."
  value       = aws_security_group.task.id
}

output "log_group_name" {
  description = "CloudWatch log group for task output."
  value       = aws_cloudwatch_log_group.this.name
}

output "schedule_name" {
  description = "EventBridge schedule name."
  value       = aws_scheduler_schedule.this.name
}

output "index_load_task_definition_family" {
  description = "Index-load task definition family."
  value       = aws_ecs_task_definition.index_load.family
}

output "index_load_log_group_name" {
  description = "CloudWatch log group for index-load task output."
  value       = aws_cloudwatch_log_group.index_load.name
}

output "index_load_schedule_name" {
  description = "EventBridge schedule name for the index load."
  value       = aws_scheduler_schedule.index_load.name
}

output "fundamentals_load_task_definition_family" {
  description = "Fundamentals-load task definition family."
  value       = aws_ecs_task_definition.fundamentals_load.family
}

output "fundamentals_load_log_group_name" {
  description = "CloudWatch log group for fundamentals-load task output."
  value       = aws_cloudwatch_log_group.fundamentals_load.name
}

output "fundamentals_load_schedule_name" {
  description = "EventBridge schedule name for the fundamentals load."
  value       = aws_scheduler_schedule.fundamentals_load.name
}

output "task_failures_sns_topic_arn" {
  description = "SNS topic notified on task failures (empty when notification_email is unset)."
  value       = var.notification_email != "" ? aws_sns_topic.task_failures[0].arn : ""
}

output "validate_prices_task_definition_family" {
  description = "Task definition family for the weekly adjusted-price validation."
  value       = aws_ecs_task_definition.validate_prices.family
}

output "validate_prices_log_group_name" {
  description = "CloudWatch log group for the validate-prices task."
  value       = aws_cloudwatch_log_group.validate_prices.name
}

output "validate_prices_schedule_name" {
  description = "EventBridge schedule name for the validate-prices task."
  value       = aws_scheduler_schedule.validate_prices.name
}

output "validation_reports_sns_topic_arn" {
  description = "SNS topic the weekly validation report is published to (empty when notification_email is unset)."
  value       = try(aws_sns_topic.validation_reports[0].arn, "")
}

output "asset_load_task_definition_family" {
  description = "Task definition family for the monthly SEC ticker universe load."
  value       = aws_ecs_task_definition.asset_load.family
}

output "asset_load_log_group_name" {
  description = "CloudWatch log group for the asset-load task."
  value       = aws_cloudwatch_log_group.asset_load.name
}

output "asset_load_schedule_name" {
  description = "EventBridge schedule name for the asset-load task."
  value       = aws_scheduler_schedule.asset_load.name
}
