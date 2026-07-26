# Scoped AWS identity for local + Claude Code work

> **Status 2026-07-26: the identity is built and verified.** Sections below are
> rewritten around what is left. The original design rationale is unchanged and
> kept in "Identity model"; the console click-path lives in
> `deploy/iam/CONSOLE-SETUP.md`.

## Where things stand

All local AWS access used to run through IAM user `Spike`, a member of group
`Admin` (`AdministratorAccess`), via a long-lived static key in `~/.aws/credentials`
`[default]`. That key was deactivated, which broke the local CLI, Terraform, SSM,
and ECR push. GitHub Actions was never affected: it uses OIDC role
`github-deploy-fattorestreet` and stores no key.

**Done:**

- Five policy documents committed to `deploy/iam/`, with `<ACCOUNT_ID>` / `<VPC_ID>`
  placeholders, plus `render.sh` to substitute them into a temp dir and
  `CONSOLE-SETUP.md` for the browser click-path
- IAM user `claude-code` (no password, no group) with one inline policy:
  `sts:AssumeRole` on `FattoreStreetDeveloper`
- IAM role `FattoreStreetDeveloper` with the three managed policies attached,
  4h max session duration, trusted by `user/claude-code` and `user/Spike`
- One access key, on the Mac only, `[fattorestreet-bootstrap]` +
  `[profile fattorestreet]` in `~/.aws/`
- `deploy/DEPLOY.md` §5 and the `aws-inspect` skill's identity row updated

**Verified 2026-07-26** under `AWS_PROFILE=fattorestreet`:

- `sts:GetCallerIdentity` → `assumed-role/FattoreStreetDeveloper/claude-code`, `AROA…`
- Reads: ECR (3 images), ECS cluster `ACTIVE` / 0 tasks, both schedules `ENABLED`,
  both log groups at 30-day retention
- All 8 guardrails deny: self-minting a key, attaching `AdministratorAccess`,
  role chaining (all three roles), `GetSecretValue`, `us-west-2`, S3, RDS, Lambda
- `terraform plan` reaches AWS and refreshes every resource

## Identity model (unchanged)

The user holds one permission and nothing else. The role carries the grants, so
the key on disk is worthless alone, sessions expire hourly and refresh
transparently, and CloudTrail attributes every call to
`assumed-role/FattoreStreetDeveloper/claude-code`. No `mfa_serial`, deliberately:
an MFA prompt would block every non-interactive call. The role has **no IAM
write** and **no `GetSecretValue`**; IAM changes are applied by a human from
CloudShell.

---

# What is left

## 1. SNS failure alerts are not being delivered (found during verification)

`terraform plan` returns `1 to add`, not "No changes":

```
+ aws_sns_topic_subscription.task_failures_email[0]
```

This is **real drift, not a missing permission**. The read succeeds and returns a
real answer: `sns list-subscriptions` → `0`, and `get-topic-attributes` →
`SubscriptionsConfirmed 0 / Pending 0 / Deleted 0`, while Terraform state holds
the subscription with `pending_confirmation = true`. The old admin key would have
shown the same thing.

Cause: the confirmation email to `johnefattore@gmail.com` was never clicked, and
AWS deletes unconfirmed email subscriptions after 3 days. The topic dates from
2026-07-19, so **nightly load failures have alerted nobody since ~2026-07-22**.
The EventBridge rule and topic are fine; there is simply no subscriber, and a
failed nightly run currently fails silently.

Fix: `terraform apply`, then **click the confirmation link in the email**, then
re-plan to confirm clean. Skipping the click puts it straight back into this
state. Worth also confirming the address is right, since a typo'd endpoint
reproduces this exact symptom forever.

## 2. Repo changes (done 2026-07-26)

Two items from the original step 4 needed no work: `.claude/settings.json`
already had the read-only AWS allowlist and a `deny` on `get-secret-value`, and
`.claude/rules/infrastructure.md` already had correct `paths:` frontmatter. Both
landed in `bd37eda5`, after this plan was written.

Applied:

- **`.claude/settings.json`**: `env` block with `AWS_PROFILE=fattorestreet`,
  `AWS_REGION`/`AWS_DEFAULT_REGION=us-east-1`, so no call needs `--profile`. The
  allowlist is unchanged; writes (`run-task`, `send-command`, `get-login-password`,
  `terraform apply`) still prompt on purpose.
- **`.claude/rules/infrastructure.md`**: new **AWS credentials** section, including
  the rule that `AccessDenied` means widen the policy in `deploy/iam/` and have a
  human apply it, never reach for another credential.
- **`springboot/deploy/terraform/README.md`**: **Credentials** section above
  `## Deploy`, noting that IAM changes in `main.tf` fail on `apply` and belong in
  CloudShell.
- **`docs/DEPLOYMENT.md`**: bullet in the AWS hosting list.
- **`CLAUDE.md`**: Infra bullet extended with the scoped-profile clause.

## 3. Verification (done 2026-07-26)

- **SSM send-command**: passed. `docker ps` against `Key=tag:App,Values=fattorestreet`
  returned `Success` / `ResponseCode 0`, all six containers healthy. Exercises the
  `ops` policy's tag-gated `SendCommand`.
- **ECS run-task**: the grant passed. `RunTask` was accepted (so `PassRole` is scoped
  right), and the task pulled from ECR, resolved both secrets, connected to Postgres,
  and wrote logs. It exited 1 for application reasons, see below.

Still outstanding:

- **Session Manager** (interactive, so yours): `aws ssm start-session --target i-09b3fa349b473e596`
- **ECR push**: only meaningfully tested by an actual push, which CI already does.

### Found by the run-task: two production bugs

**a. Stale ECR image, silently degrading tasks to `server` mode.** Already fixed by
`bd37eda5` in this PR, and confirmed. The 07-25 index-load ran for **23 hours**
(09:30:28 → 08:38:30 next day) and `IndexLoadRunner` never fired; 07-24 and 07-20
look identical, 58 startup events each and then Tomcat idling on 8080. ECR was
serving images from 2026-07-01 and 2026-06-20. This is exactly the failure
`.claude/rules/infrastructure.md` warns about, and it was billing Fargate the
whole time. The 08:42 push today was the first image containing the runner, and
the 09:30 run was the first to execute it.

**b. `SEC_CONTACT_EMAIL` missing from both task definitions.** Fixed in this PR.
`application.properties:44` reads `sec.contact-email=${SEC_CONTACT_EMAIL:}`,
defaulting to empty, and `WebService.java:83` sends it as the User-Agent that SEC
requires. Neither task definition passed it, so every ticker got
`403 Undeclared Automated Tool` and the run exited non-zero having processed
nothing. Added to the `secrets` block of both, sourced from the same
`fattorestreet/env` JSON as `POSTGRES_PASSWORD` / `SECRET_KEY` (verified present
in the live secret), so no new variable and no IAM change.

Neither is verified end to end yet: that needs `terraform apply` plus a re-run.

Note the two bugs compound. (a) hid (b): while the image lacked the runner, the
SEC call never happened, so the missing env var could not surface. And the SNS
gap in §1 hid both, since these runs have been exiting non-zero all along.

## 4. Key cleanup (only after the above passes)

This identity cannot inspect IAM users: `iam:ListAccessKeys` on `user/Spike`
returns `AccessDenied`, since `IamReadOnly` is scoped to `role/fattorestreet-*`.
That is by design, so all of this is console or CloudShell work.

Key IDs are written truncated on purpose. They are identifiers, not credentials,
but this repo is public and one of them is an active admin key; the last four
characters are enough to pick the right row in the console, and publishing the
full ID only helps someone enumerate. This is also why they are truncated rather
than pragma-allowlisted: `.claude/rules/secrets-check.md` reserves pragmas for
provable non-secrets, and an active admin key ID is not that.

1. Delete the deactivated key ending `RH32` (created 2026-06-20 to run one
   `terraform apply`), and remove its `[default]` block from `~/.aws/credentials`
   if still present.
2. The 2023 key ending `DRO3` is the larger exposure: 2.5 years old,
   admin, on a Linux host that is not this Mac, last used 2026-06-21 for
   `ecr:ListImages`. Its CloudTrail history is entirely describe/list plus
   `secretsmanager:DescribeSecret`, exactly what `EC2FattoreStreetRole` already
   grants that host. So: confirm the host is the EC2 instance, delete
   `~/.aws/credentials` on it (the CLI then falls through to IMDS), then delete
   the key. If it is a non-AWS box, give it its own scoped user rather than
   leaving admin on it.
3. End state: `Spike` keeps console password + MFA + `Admin` as break-glass and
   holds **zero** access keys. Every programmatic identity is then OIDC (CI), an
   instance/task role, or an assumed scoped role (laptop).
4. The role denies `iam:CreateAccessKey`, so the `claude-code` key cannot rotate
   itself. Rotation is a deliberate console action; worth a 90-day reminder.

## 5. Ship it

One branch, one PR, combining the ECR-publish/aws-inspect commit (`bd37eda5`,
unpushed) with the scoped-identity work. They are the same story: CI stopped
drifting between registries, and local access stopped being an admin key.

Note `bd37eda5`'s `aws-inspect` skill advertised `IAM user Spike, keys in
~/.aws/credentials, Admin group (AdministratorAccess)` on a public repo. That
line is rewritten to describe the scoped profile, so it never reaches public
history. Keep it that way.

## Non-secrets, for the record

`<ACCOUNT_ID>` and `<VPC_ID>` are placeholders out of habit, not because either
is sensitive. The account ID has been public in this repo's history since
January 2024 (ECR image URIs in an old `docker-compose.yml`) and is
unretractable; it buys reconnaissance, never access. A VPC ID is inert outside
the account. The convention matters because it generalizes to values that *are*
sensitive: secret ARNs with their random suffix, instance IDs, subnet IDs.
