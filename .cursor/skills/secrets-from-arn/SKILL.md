---
name: secrets-from-arn
description: Convert a container so docker run passes only SECRETS_ARN and the image fetches its config from AWS Secrets Manager at start (replacing the long -e KEY=value list in kubernetes/run.sh). Use when moving a service's secrets out of run.sh into Secrets Manager.
---

@.claude/commands/secrets-from-arn.md
