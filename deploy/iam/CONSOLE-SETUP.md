# Console setup: the `claude-code` identity

Everything in this file is done **by a human, in the AWS console**, signed in as
`Spike` (console password + MFA). Nothing here can be automated from the laptop,
because the laptop has no working credentials until step 6 finishes.

Full design rationale lives in `.claude/plans/fizzy-meandering-lark.md`; this is
just the click-path.

## What you are building

| Thing | Name | Gets |
|---|---|---|
| IAM user | `claude-code` | one inline policy: permission to assume the role below, nothing else |
| IAM role | `FattoreStreetDeveloper` | the three customer-managed policies below |

The key that lands on the Mac belongs to the user, which can do exactly one
thing: assume the role. Sessions expire hourly and refresh transparently.

## Step 0: substitute the placeholders (do this first, or nothing works)

The committed JSON uses the repo's placeholder convention, and **AWS will not
accept it as-is**. `<ACCOUNT_ID>` inside an ARN is not a valid account field, so
the console rejects the paste with:

> The policy failed legacy parsing

That message names no line and no file. It almost always means a placeholder
survived into an ARN.

`render.sh` writes substituted copies to a fresh temp directory outside the
repo, then refuses to finish if any placeholder survived or any file stopped
being valid JSON:

```sh
cd deploy/iam
ACCOUNT_ID=<12 digits from the console top-right menu> ./render.sh
```

It prints the directory. **Paste from there, not from the repo.** Every step
below that says "paste this file" means the rendered copy.

`ACCOUNT_ID` has to be passed by hand for this first run: the script otherwise
reads it from `aws sts get-caller-identity`, and during bootstrap there are no
working credentials, which is the reason this runbook exists at all. `VPC_ID`
is read from `springboot/deploy/terraform/terraform.tfvars` (gitignored);
override with `VPC_ID=vpc-…` if that file is absent.

Real values stay out of the repo on purpose. Neither is a credential, and the
account ID has in fact been public in this repo's history since 2024 (ECR image
URIs in an old `docker-compose.yml`), so the point is not that these two strings
are sensitive. It is that the habit generalizes to the values that are: secret
ARNs with their random suffix, instance IDs, subnet IDs.

Region is hardcoded to `us-east-1` throughout, matching the rest of the repo.

---

## Step 1: create the three role policies

**IAM → Policies → Create policy → JSON tab.** Paste the file, Next, name it
exactly as shown, Create. Repeat three times.

| Paste this file | Name the policy |
|---|---|
| `fattorestreet-developer-infra.json` | `FattoreStreetDeveloper-infra` |
| `fattorestreet-developer-ops.json` | `FattoreStreetDeveloper-ops` |
| `fattorestreet-developer-guardrails.json` | `FattoreStreetDeveloper-guardrails` |

The names matter only in that step 4 attaches them by name.

The console will flag `FattoreStreetDeveloper-infra` with a blue "this policy
grants `iam:PassRole`" note and may warn that `ecs:RegisterTaskDefinition` uses
`*`. Both are expected: neither action supports resource-level scoping, and
`iam:PassRole` is condition-locked to `role/fattorestreet-*` passed only to
`ecs-tasks` and `scheduler`.

## Step 2: create the user

**IAM → Users → Create user.**

1. User name `claude-code`.
2. **Do not** check "Provide user access to the AWS Management Console". This
   identity gets no password.
3. Permissions → **Attach policies directly** → skip, attach nothing. Next.
4. Tags: `Project=FattoreStreet`, `Purpose=claude-code-local`. Create user.

Then attach the inline policy: **open the user → Permissions tab → Add
permissions → Create inline policy → JSON tab.** Paste
**`claude-code-user-assume-role.json`**, name it `assume-developer-role`, Create.

Inline rather than managed on purpose: it is meaningless outside this one user,
and keeping it inline means it dies with the user.

Do not add the user to any group. Do not create an access key yet; that is step 6.

## Step 3: create the role

The user must already exist, or the console rejects the trust policy with
"Invalid principal in policy".

**IAM → Roles → Create role → Custom trust policy.** Paste
**`fattorestreet-developer-trust.json`** into the editor (replace what is
prefilled). Next.

Naming `user/Spike` alongside `user/claude-code` is what lets you assume the
same role by hand and reproduce anything Claude Code sees.

## Step 4: attach the three policies

Still in the create-role wizard, on the permissions page, search
`FattoreStreetDeveloper` and check all three: `-infra`, `-ops`, `-guardrails`.
Next.

Role name: **`FattoreStreetDeveloper`** (exact: the trust policy in step 2's
user policy and the local profile both reference it by name). Add tag
`Project=FattoreStreet`. Create role.

## Step 5: raise the max session duration

**IAM → Roles → FattoreStreetDeveloper → Summary → Edit → Maximum session
duration → 4 hours.** Save.

The local profile still requests 1 hour; this only stops a long
`terraform apply` from dying mid-run if a session is ever requested longer.

## Step 6: mint the one access key

**IAM → Users → claude-code → Security credentials → Access keys → Create
access key.**

- Use case: **Other** (there is no better fit; ignore the recommendation banner).
- Description tag: `laptop + claude code`.
- Download the .csv or copy both values now. **This is the only time the secret
  is shown.**

Move the two values to the Mac yourself. Do not paste them into a Claude Code
chat, into any file in this repo, or into a terminal Claude Code is reading.

## Step 7: local profile (you, in a terminal, by hand)

Claude Code will not read or write `~/.aws/*`.

`~/.aws/credentials`: add this block, then **delete the existing `[default]`
block** holding the dead admin key. A missing `[default]` makes an un-profiled
call fail loudly instead of silently reaching for a stale identity:

```ini
[fattorestreet-bootstrap]
aws_access_key_id = AKIA...
aws_secret_access_key = ...
```

`~/.aws/config`: add:

```ini
[profile fattorestreet]
role_arn = arn:aws:iam::<ACCOUNT_ID>:role/FattoreStreetDeveloper
source_profile = fattorestreet-bootstrap
role_session_name = claude-code
duration_seconds = 3600
region = us-east-1
output = json
```

## Step 8: one check before handing back

```sh
aws --profile fattorestreet sts get-caller-identity
```

Expected `Arn`: `arn:aws:sts::<ACCOUNT_ID>:assumed-role/FattoreStreetDeveloper/claude-code`,
with a `UserId` starting `AROA`.

Getting the `claude-code` **user** ARN back (starting `AIDA`) means `role_arn`
is not being picked up. Check that the `[profile fattorestreet]` header
includes the word `profile` and that `source_profile` matches the credentials
block name exactly.

Once that returns the assumed-role ARN, Claude Code can run the rest of the
verification (Terraform plan, ECR, logs, SSM, run-task, and the deny checks) in
`.claude/plans/fizzy-meandering-lark.md` → Verification.

## Not now: cleanup

After verification passes, two keys still need deleting: the deactivated
`Spike` key and the 2.5-year-old admin key on the Linux host. That is the
Cleanup section of the plan, and it is deliberately last, since CloudShell as
`Spike` is the escape hatch if anything above is wrong.
