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
