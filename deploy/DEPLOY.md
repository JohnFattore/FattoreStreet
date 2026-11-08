# Automated deploys: push-to-main → EC2 via SSM

Every merge to `main` runs `.github/workflows/docker-build.yml`, which:

1. Builds the three production images and pushes them to GHCR
   (`ghcr.io/johnfattore/{nginx,django,springboot}`) tagged `latest` **and**
   the commit SHA, authenticated with the workflow's built-in `GITHUB_TOKEN`
   (no registry credentials stored anywhere).
2. Assumes an IAM role via GitHub **OIDC** (no AWS keys stored in GitHub) and
   sends an **SSM RunCommand** to the EC2 instance tagged `App=fattorestreet`
   (no SSH key, no inbound port).
3. On the host, the command resets the repo clone to the deployed commit and
   runs the versioned [`deploy.sh`](deploy.sh), which self-heals container
   state, converges the compose stack to the SHA tag, migrates, prunes old
   images, and health-checks nginx. The script's output is echoed back into
   the Action log, and a failure fails the workflow.

Rollback = re-run the deploy with an older commit's SHA, or on the host:
`sudo ./deploy.sh <old-sha>`.

---

## One-time setup

Everything below is already done for the current instance; it's recorded here
for a rebuild. Placeholders follow the repo convention (`<ACCOUNT_ID>` etc.);
run with real values.

### 1. EC2 instance → SSM (managed instance)

The instance role (`EC2FattoreStreetRole`, which already reads the
`fattorestreet/env` secret) needs the SSM agent policy:

```sh
aws iam attach-role-policy \
  --role-name EC2FattoreStreetRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
```

Tag the instance (this is how the workflow targets it — rebuilding the
instance never requires a repo change, just re-tag):

```sh
aws ec2 create-tags --region us-east-1 \
  --resources <INSTANCE_ID> --tags Key=App,Value=fattorestreet
```

On the instance, restart the agent so it picks up the new role permissions
immediately (it ships preinstalled and running on Amazon Linux 2023; it logs
to journald: `journalctl -u amazon-ssm-agent`):

```sh
sudo systemctl restart amazon-ssm-agent
```

Verify from admin credentials (empty output = not registered; also the classic
symptom of querying the wrong region):

```sh
aws ssm describe-instance-information --region us-east-1 \
  --query 'InstanceInformationList[].[InstanceId,PingStatus]' --output text
```

### 2. GitHub OIDC provider + deploy role

Create the OIDC identity provider (once per AWS account; skip if it exists):

```sh
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

(AWS no longer validates GitHub's thumbprint, but the CLI requires one.)

Trust policy — only workflows on `main` of this repo can assume the role:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": {
        "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
        "token.actions.githubusercontent.com:sub": "repo:JohnFattore/FattoreStreet:ref:refs/heads/main"
      }
    }
  }]
}
```

Permissions policy — send the stock shell-script document to instances tagged
`App=fattorestreet`, and read results back:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendToTaggedInstances",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": "arn:aws:ec2:us-east-1:<ACCOUNT_ID>:instance/*",
      "Condition": { "StringEquals": { "ssm:resourceTag/App": "fattorestreet" } }
    },
    {
      "Sid": "SendRunShellScript",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": "arn:aws:ssm:us-east-1::document/AWS-RunShellScript"
    },
    {
      "Sid": "ReadResults",
      "Effect": "Allow",
      "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"],
      "Resource": "*"
    }
  ]
}
```

Create the role (with the two JSON files saved locally):

```sh
aws iam create-role --role-name github-deploy-fattorestreet \
  --assume-role-policy-document file://trust.json
aws iam put-role-policy --role-name github-deploy-fattorestreet \
  --policy-name ssm-deploy --policy-document file://permissions.json
```

The same role also pushes the springboot image to ECR for the nightly Fargate
loads, which needs a second inline policy (`ecr-push.json`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrAuthToken",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "PushHistLoadImage",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "arn:aws:ecr:us-east-1:<ACCOUNT_ID>:repository/fattorestreet-hist-load"
    }
  ]
}
```

```sh
aws iam put-role-policy --role-name github-deploy-fattorestreet \
  --policy-name ecr-push --policy-document file://ecr-push.json
```

Point the workflow at the role (repo Actions **variable**, not a secret):

```sh
gh variable set AWS_DEPLOY_ROLE_ARN \
  --body arn:aws:iam::<ACCOUNT_ID>:role/github-deploy-fattorestreet
```

### 3. GHCR package visibility

The EC2 host pulls anonymously. In practice this needed no setup: packages
first pushed by a workflow's `GITHUB_TOKEN` are linked to the repo and
inherit its (public) visibility. If a package is ever created some other way
(e.g. a laptop push with a PAT) and comes out private, flip it manually:
`github.com/JohnFattore?tab=packages` → package → Package settings →
Change visibility → Public.

### 4. Host prerequisites

- Repo clone at `/home/ec2-user/FattoreStreet`, owned by `ec2-user`, `origin`
  over HTTPS (public repo, no credentials). The workflow's `CLONE_PATH` env
  var is the one line to change if this moves.
- `deploy/.env` filled in from `.env.example` (the real `SECRETS_ARN`).
- Git on the host runs as `ec2-user` via `sudo -u` (root-run git refuses the
  ec2-user-owned clone with "dubious ownership"); `deploy.sh` runs as root.
- Old `johnfattore/*` Docker Hub images are tagged, so `docker image prune`
  won't reclaim them — `docker image rm` them once after the GHCR cutover.
- (Optional, once deploys work) remove port 22 from the security group; SSM
  Session Manager replaces interactive SSH:
  `aws ssm start-session --target <INSTANCE_ID>`.

### 5. Local developer credentials (laptop + Claude Code)

Local `aws` / `terraform` / SSM work runs as IAM user `claude-code`, whose only
permission is to assume role `FattoreStreetDeveloper`; that role carries the
grants. No admin key on the laptop, sessions expire hourly, and revoking is a
one-line trust-policy edit.

The five policy documents are versioned in [`iam/`](iam/), and the console
click-path that creates the user, the role, and the key is
[`iam/CONSOLE-SETUP.md`](iam/CONSOLE-SETUP.md). All commands then run under
`AWS_PROFILE=fattorestreet`.

The role has **no IAM write** and cannot read secret values, so IAM changes in
`springboot/deploy/terraform/` must be applied by an admin from CloudShell.

### 6. Bedrock access for the advisor chatbot

Django calls AWS Bedrock (Converse API) with the instance role, so there is no
API key in `fattorestreet/env`. Two admin steps, once per model:

1. **Model access.** Bedrock console → *Model access* → enable the model named
   by `ADVISOR_MODEL_ID` (default Meta Llama 3.3 70B Instruct, called through
   the `us.` cross-region inference profile).
2. **IAM.** Render [`iam/ec2-fattorestreet-role-bedrock.json`](iam/ec2-fattorestreet-role-bedrock.json)
   with `iam/render.sh` and add it to `EC2FattoreStreetRole` as an inline
   policy named `bedrock-advisor`:

   ```sh
   aws iam put-role-policy --role-name EC2FattoreStreetRole \
     --policy-name bedrock-advisor \
     --policy-document file://<RENDER_DIR>/ec2-fattorestreet-role-bedrock.json
   ```

   An inference profile is invoked on the profile ARN, but Bedrock also checks
   the foundation model in every region the profile routes to, hence both
   statements.

Changing models means a new `ADVISOR_MODEL_ID` in `fattorestreet/env`, model
access for it, and the same IAM edit with the new IDs. Until both steps are
done the chatbot answers `503` and the rest of the site is unaffected.

## TLS certificate renewal

The Let's Encrypt certificate (90-day lifetime) is renewed by
[`renew-cert.sh`](renew-cert.sh), run twice a day by the systemd timer
`fattorestreet-cert-renew.timer` ([`systemd/`](systemd/)). `deploy.sh`
installs and enables the timer on every deploy, so there is nothing to set up
by hand and changes to either file ship with the next merge.

Each run:

1. Runs certbot (webroot challenge through nginx's
   `/.well-known/acme-challenge/` location) for `fattorestreet.com` and
   `www.fattorestreet.com`. It's a no-op until the certificate is within 30 days
   of expiry. `--expand` grows the existing `fattorestreet.com` lineage in
   place, so nginx's certificate paths never change. If the two-name request
   fails, the script falls back to renewing the existing certificate unchanged
   and marks the run failed.
2. Reloads nginx (`nginx -t`, then `nginx -s reload`) only when a certificate
   was actually issued. nginx reads certificates only at start/reload.
3. Fails the run if the certificate nginx is serving expires within 14 days.

There is no alerting on failure yet (it needs `sns:Publish` on the instance
role), so check it occasionally:

```sh
systemctl list-timers fattorestreet-cert-renew.timer   # next/last run
systemctl status fattorestreet-cert-renew              # last result
journalctl -u fattorestreet-cert-renew                 # certbot output
sudo /home/ec2-user/FattoreStreet/deploy/renew-cert.sh # run now
```

`www.fattorestreet.com` needs a DNS record pointing at this host, or its
challenge fails. Until it does, every run renews the apex-only certificate and
reports failure.

**First issuance on a new host** is still manual: nginx won't start while its
443 block references certificate files that don't exist yet, and the webroot
challenge needs nginx running. Bootstrap with a temporary port-80-only config
(or a throwaway self-signed certificate at the `live/` paths), run the
`certbot certonly` command from `run.sh` (with an extra
`-d www.fattorestreet.com`), then restore the real config.

## Notes & limits

- Images are **linux/arm64 only** — the EC2 host is a t4g (Graviton), and CI
  builds natively on GitHub's arm64 runners. An amd64 host (or local
  `docker run` on an Intel machine) can't run them without adding a
  multi-arch build.
- SSM returns at most ~24 KB of command output to `get-command-invocation`;
  `deploy.sh` keeps its logging terse so failures fit. For full logs:
  `journalctl` / `docker compose logs` on the host.
- A tag target that matches no registered instance "succeeds" with zero
  invocations — the workflow treats that as a hard failure rather than a
  silent green.
- Deploys are serialized by the workflow's `concurrency: deploy` group; a
  second merge queues until the first finishes.
- CI is unaffected by the local-credential setup in §5: it authenticates through
  OIDC role `github-deploy-fattorestreet` and stores no AWS key.
