---
name: aws-inspect
description: Answer ad-hoc questions about the live FattoreStreet AWS account (what is running, what it costs, why a scheduled task failed) by pulling data with read-only AWS CLI calls. Use when the user asks what is running in AWS, why a Fargate task or schedule misbehaved, what something is costing, or to check logs, images, or infrastructure state.
---

# AWS Inspect

Answer questions about the live AWS account by querying it, not by reading Terraform.
Terraform in `springboot/deploy/terraform/` describes what *should* exist; this skill
exists because the two drift. Every real incident so far has been drift.

## Hard rule: read-only

Only run AWS CLI commands that read. Never run a mutating command (`run-task`,
`stop-task`, `delete-*`, `put-*`, `update-*`, `create-*`, `terraform apply`) without
stating what it will change and getting explicit approval first. Report the finding,
propose the fix, let the user decide.

`aws secretsmanager get-secret-value` is also off limits: it prints live credentials
into the transcript. Use `describe-secret` to inspect metadata instead.

## Account facts

| Thing | Value |
|---|---|
| Account | `196185437114` |
| Region | `us-east-1` (everything; no other region is in use) |
| Local identity | IAM user `Spike`, keys in `~/.aws/credentials`, `Admin` group (AdministratorAccess) |
| ECS cluster | `fattorestreet-hist-load` (the only one) |
| ECS services | none, ever. Only one-shot scheduled tasks |
| Task families | `fattorestreet-hist-load`, `fattorestreet-index-load` |
| Schedules | EventBridge Scheduler `fattorestreet-hist-load`, `fattorestreet-index-load` |
| Log groups | `/ecs/fattorestreet-hist-load`, `/ecs/fattorestreet-index-load` (30-day retention) |
| ECR repo | `fattorestreet-hist-load` (one image, shared by both task families) |
| Secret | `fattorestreet/env`, one flat JSON shared by every service |
| EC2 | one Graviton instance, tag `App=fattorestreet`, runs the compose stack |

If `AWS_PROFILE` is set in the environment, respect it. Otherwise the default profile
is correct.

## Mental model that makes findings obvious

The cluster runs **one-shot tasks only**. A healthy steady state is **zero running
tasks**. The container image runs three modes off `APP_RUN_MODE` (`server`,
`hist-load`, `index-load`); the load modes are `ApplicationRunner` beans that call
`System.exit` when done, and `server` mode boots Tomcat and stays up forever.

So: **any task alive for more than an hour or two is a bug, not activity.** The usual
cause is the image not containing the runner the task definition asked for, which
silently degrades to `server` mode and pins the task forever. One orphan accumulates
per schedule firing, at Fargate rates, until someone looks.

## Recipes

**What is running right now**

```bash
aws ecs list-tasks --cluster fattorestreet-hist-load
aws ecs describe-tasks --cluster fattorestreet-hist-load \
  --tasks $(aws ecs list-tasks --cluster fattorestreet-hist-load --query 'taskArns[]' --output text) \
  --query 'tasks[].{group:group,last:lastStatus,started:startedAt,cpu:cpu,mem:memory,by:startedBy}' \
  --output table
```

Read `startedAt` first. Age is the signal.

**Why did a task not do its job**

```bash
aws logs tail /ecs/fattorestreet-index-load --since 24h --format short | tail -40
```

Look for the runner's own first line (`Starting one-shot index load`,
`Starting one-shot IEX HIST load`). If the app logged `Started SecApiApplication` and
that runner line never appears, the runner bean was never created: the deployed image
predates the runner, or `APP_RUN_MODE` is wrong. Confirm with the two commands below.

**Is the deployed image current**

```bash
aws ecr describe-images --repository-name fattorestreet-hist-load \
  --query 'sort_by(imageDetails,&imagePushedAt)[].{pushed:imagePushedAt,tags:imageTags}' --output table
aws ecs describe-task-definition --task-definition fattorestreet-index-load \
  --query 'taskDefinition.containerDefinitions[].{image:image,env:environment}'
```

Compare the `latest` push date against `git log -1 --format=%ad -- <path/to/Runner.java>`.
The task definitions pin `:latest`, so a task definition can reference a runner that
the image does not contain. This is the single most common failure here.

**Schedules**

```bash
aws scheduler list-schedules --query 'Schedules[].{name:Name,state:State}' --output table
aws scheduler get-schedule --name fattorestreet-index-load \
  --query '{cron:ScheduleExpression,tz:ScheduleExpressionTimezone,state:State}'
```

**Cost**

```bash
aws ce get-cost-and-usage --time-period Start=$(date -v-30d +%F),End=$(date +%F) \
  --granularity MONTHLY --metrics UnblendedCost --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[].Groups[].{svc:Keys[0],amt:Metrics.UnblendedCost.Amount}' --output table
```

Cost Explorer lags roughly a day, and Fargate shows up under `Amazon Elastic Container
Service`. For a quick estimate of one orphan task instead, Fargate ARM is about
`$0.03238/vCPU-hr` + `$0.00356/GB-hr`, so a 0.5 vCPU / 2 GB task is roughly `$0.024/hr`,
about `$17/month` each.

**EC2 and everything else**

```bash
aws ec2 describe-instances --filters Name=tag:App,Values=fattorestreet \
  --query 'Reservations[].Instances[].{id:InstanceId,type:InstanceType,state:State.Name,ip:PrivateIpAddress}' --output table
aws secretsmanager describe-secret --secret-id fattorestreet/env --query '{name:Name,changed:LastChangedDate}'
```

## Answering style

- Lead with the answer to the question asked, then the evidence.
- Quote real values pulled from the account (task ages, dates, counts). Do not
  generalize from Terraform.
- When you find drift, say what the fix is and what it costs to leave alone, then stop
  and let the user choose. Do not fix it silently.
