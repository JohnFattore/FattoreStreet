# Temporary IAM grants

Policies in this directory are **not** part of the steady state. They widen
`FattoreStreetDeveloper` for one apply and are meant to be revoked immediately
after. `render.sh` in the parent directory globs `*.json` non-recursively, so
nothing here is picked up by normal bootstrap.

The steady-state documents are the four in `deploy/iam/`. If a file here is
attached and you are not mid-apply, that is drift: revoke it.

---

## Grant: write the scheduler role's inline policy

### Why it is needed

`terraform apply` in `springboot/deploy/terraform/` currently plans six resource
actions. Five are already inside the role. Exactly one is not:

| Resource | Action | API call | Covered by |
|---|---|---|---|
| `aws_ecs_task_definition.this` | replace | `ecs:RegisterTaskDefinition`, `ecs:DeregisterTaskDefinition`, `iam:PassRole` | `EcsRegisterTaskDef`, `EcsTaskDefs`, `PassRole` |
| `aws_ecs_task_definition.index_load` | replace | same | same |
| `aws_scheduler_schedule.this` | update | `scheduler:GetSchedule`, `scheduler:UpdateSchedule`, `iam:PassRole` | `GlobalReads`, `Scheduler`, `PassRole` |
| `aws_scheduler_schedule.index_load` | update | same | same |
| `aws_sns_topic_subscription.task_failures_email[0]` | create | `sns:GetTopicAttributes`, `sns:Subscribe` | `GlobalReads`, `Sns` |
| `aws_iam_role_policy.scheduler_run_task` | update | **`iam:PutRolePolicy`** | **nothing, and explicitly denied** |

So the whole grant is one action on one role.

### Why an allow policy alone does not work

`FattoreStreetDeveloper-guardrails` carries `DenyAllIamWrite`, an explicit `Deny`
on `iam:Put*`. An explicit deny beats every allow, so attaching a permissions
policy changes nothing on its own: the call still fails with `AccessDenied`.

The deny itself has to stop covering this one role, which is what the carve-out
file does. Both halves are required:

- `fattorestreet-developer-guardrails-iam-carveout.json` adds
  `role/fattorestreet-hist-load-sched-*` to `DenyAllIamWrite`'s `NotResource`, so
  the deny no longer reaches that role.
- `fattorestreet-developer-temp-scheduler-policy-write.json` then allows
  `iam:PutRolePolicy` on it. Without this, removing the deny grants nothing,
  since no allow exists.

The carve-out exempts that role from *all* the IAM-write denies, but the allow is
the limiting factor, so the effective grant stays `iam:PutRolePolicy` on one role.

### Apply

Run as an admin (console or CloudShell), not as `FattoreStreetDeveloper`.

```sh
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
GUARDRAILS="arn:aws:iam::${ACCOUNT_ID}:policy/FattoreStreetDeveloper-guardrails"

sed "s/<ACCOUNT_ID>/${ACCOUNT_ID}/g" \
  fattorestreet-developer-temp-scheduler-policy-write.json > /tmp/temp-grant.json

# 1. Carve the scheduler role out of the deny, as a new default version.
aws iam create-policy-version \
  --policy-arn "$GUARDRAILS" \
  --policy-document file://fattorestreet-developer-guardrails-iam-carveout.json \
  --set-as-default

# 2. Add the allow.
TEMP_ARN=$(aws iam create-policy \
  --policy-name FattoreStreetDeveloper-temp-scheduler-policy-write \
  --policy-document file:///tmp/temp-grant.json \
  --query Policy.Arn --output text)

aws iam attach-role-policy \
  --role-name FattoreStreetDeveloper --policy-arn "$TEMP_ARN"
```

Note the version id `create-policy-version` prints. Rolling back needs the
*previous* one (`v1` unless the guardrails have been revised since).

Policy changes take effect on the next API call, within seconds. There is no need
to re-assume the role or refresh credentials.

### Then, from `springboot/deploy/terraform/`

```sh
terraform plan     # confirm still 3 to add, 3 to change, 2 to destroy
terraform apply
```

### Revoke, immediately after

```sh
aws iam detach-role-policy \
  --role-name FattoreStreetDeveloper --policy-arn "$TEMP_ARN"
aws iam delete-policy --policy-arn "$TEMP_ARN"

# Restore the guardrails to the version from before step 1.
aws iam set-default-policy-version --policy-arn "$GUARDRAILS" --version-id v1
aws iam delete-policy-version --policy-arn "$GUARDRAILS" --version-id v2
```

Verify the deny is back. This must fail with `AccessDenied`:

```sh
AWS_PROFILE=fattorestreet aws iam put-role-policy \
  --role-name "$(aws iam list-roles \
    --query 'Roles[?starts_with(RoleName,`fattorestreet-hist-load-sched-`)].RoleName' \
    --output text)" \
  --policy-name run-task --policy-document file:///tmp/temp-grant.json
```

A managed policy holds at most five versions. If `create-policy-version` ever
fails on that limit, delete an old version rather than skipping the rollback.

### What the grant is worth while it is live

`iam:PutRolePolicy` on the scheduler role means the developer role could rewrite
that role's permissions, and the scheduler role can `iam:PassRole` the task and
execution roles. Combined with `scheduler:CreateSchedule`, which the developer
role already has, that is a real if bounded escalation path: it ends at what a
Fargate task in this cluster can do, and `DenyRoleChaining` still blocks assuming
the role directly. It is scoped to one role, held for one apply, and revoked by
the two commands above. Do not leave it attached.

### The alternative that needs no grant

Pasting the rendered policy into the console by hand also works and changes no
IAM configuration at all. See the Credentials section of
`springboot/deploy/terraform/README.md`. It is fewer moving parts for a one-off;
this grant is better if you would rather `terraform apply` do the whole thing in
one run with nothing hand-copied.
