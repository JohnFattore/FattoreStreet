# OIDC-authenticated dual-registry Docker publish + SSM-based EC2 deploy

_FattoreStreet @ [`b7d12439`](https://github.com/JohnFattore/FattoreStreet/tree/b7d12439fe7d4824d80a74e5dd788d7e50c00750) — 2026-07-30_

_Source: [#165](https://github.com/JohnFattore/FattoreStreet/issues/165)_

## Overview

`docker-build.yml` is the CI/CD pipeline that turns a merge to `main` into a running change on production. It's worth understanding closely because it stitches together several non-obvious mechanisms at once: GitHub's OIDC federation into AWS (no long-lived AWS keys stored in GitHub), a matrix build that publishes the same `springboot` image to *two* registries (GHCR and ECR) in one step so they can never drift apart, and a deploy job that pushes commands to a live EC2 instance over SSM RunCommand rather than SSH — polling for completion and treating "no instance matched the tag" as a hard failure rather than a silent no-op. Nothing here touches application code, but it's the part of the repo where "reviewed PR" actually becomes "bits running on a box people load in a browser," and its failure modes (a stale ECR image silently downgrading a one-shot Fargate task to a never-exiting `server` mode, a deploy racing an in-progress one) are the kind of thing you only appreciate by tracing the mechanism end to end.

## Files to read

- `.github/workflows/docker-build.yml` — the whole file, but pay attention to:
  - The matrix `strategy.matrix.include` block (lines ~40-53) — three images, only `springboot` sets `ecr:`
  - `permissions: id-token: write` (line ~35) and the two `configure-aws-credentials`/`amazon-ecr-login` steps — this is the OIDC handshake, no `AWS_ACCESS_KEY_ID` secret anywhere
  - The `tags:` block in the `build-push-action` step (~line 95-100) — note the `steps.ecr-login.outputs.registry && format(...) || ''` conditional expressions that make the ECR tag lines resolve to empty/dropped on PRs and for non-springboot images
  - The `deploy` job's `concurrency: group: deploy` (serializes deploys — a second merge queues rather than interleaves)
  - The SSM polling loop (`for i in $(seq 1 90); do sleep 10; ...`) and its two failure branches: no instance ever matched the tag vs. the command finished in a non-`Success` status
- `deploy/deploy.sh` — what the SSM command actually runs on the host: `docker compose pull` *before* touching any running container (so an unreachable registry is a safe no-op), then the `evict_unless_project` cleanup for name-squatting containers, then `docker compose up`
- `deploy/DEPLOY.md` — the one-time AWS/GitHub setup this workflow assumes already exists (OIDC provider, IAM role trust policy, SSM agent on the host)
- `springboot/deploy/terraform/` — skim for where the Fargate one-shot tasks reference the ECR image, to see the consumer side of the dual-publish guarantee

## Questions to answer while reading

1. Why does the `springboot` image need to exist in *both* GHCR and ECR, and why is publishing to both done in the same build step instead of, say, a separate promotion job that copies GHCR → ECR after the fact?
2. How does GitHub Actions OIDC actually work here — what is `id-token: write` granting, and what does AWS check on its side (`role-to-assume`, trust policy) before handing back temporary credentials? Why is this preferred over a static `AWS_SECRET_ACCESS_KEY` GitHub secret?
3. Why does the deploy job `docker compose pull` before stopping/evicting any container, and what does that ordering buy you if GHCR is unreachable or a package visibility setting is wrong?
4. What exactly does `aws ssm send-command` + `list-command-invocations`/`get-command-invocation` do that a plain SSH `ssh host 'bash deploy.sh'` wouldn't, and why does the script treat "no instance matched the tag" as a failure after 6 polls specifically, rather than immediately or never?
5. The comment says an ECR image older than the code "silently degrades a one-shot task to `APP_RUN_MODE=server`, which never exits and bills until someone notices" — trace why that's true: what decides `APP_RUN_MODE`, and what would actually happen if the nightly Fargate task picked up a stale image?

## Primer

**OIDC (OpenID Connect) federation** lets a CI system prove its identity to a cloud provider without either side storing a shared secret. GitHub Actions can request a short-lived, cryptographically-signed JSON Web Token (JWT) scoped to the specific repo/workflow/branch that's running (`id-token: write` is what authorizes a job to request one). AWS's IAM has an OIDC identity provider registered for `token.actions.githubusercontent.com`; an IAM role's trust policy says "I'll accept a token from that provider, but only if its `sub` claim matches `repo:johnfattore/fattorestreet:ref:refs/heads/main`" (or similar). When `configure-aws-credentials` runs, it hands AWS that JWT, AWS validates the signature and the trust-policy conditions, and returns temporary AWS credentials (`AssumeRoleWithWebIdentity`) that expire in an hour or so. Nothing durable is stored on either side — compromise the CI runner mid-job and the blast radius is one hour, not forever. This is the modern replacement for stashing long-lived AWS access keys as GitHub secrets.

**SSM RunCommand** is AWS Systems Manager's way of executing shell commands on an EC2 instance without SSH, an open inbound port, or a key pair: an agent running on the instance polls SSM for commands, and `aws ssm send-command --targets Key=tag:App,Values=fattorestreet` fans a command out to every instance carrying that tag. The caller gets back a `CommandId` and has to separately poll `get-command-invocation` for status, since the API is async by design (a command can target many instances). It's a good fit here because the deploy role only needs `ssm:SendCommand`-style IAM permissions, not network-level SSH access to the box.

## External docs

- GitHub Actions: [Configuring OpenID Connect in Amazon Web Services](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services)
- AWS: [AWS Systems Manager Run Command](https://docs.aws.amazon.com/systems-manager/latest/userguide/execute-remote-commands.html)

## Exercise (optional)

Find the IAM role referenced by `vars.AWS_DEPLOY_ROLE_ARN` (or its policy JSON under `deploy/iam/` if present) and write out, in your own words, the trust-policy condition that scopes it to this repo/branch — then check whether it's scoped to `ref:refs/heads/main` specifically or anything looser (any branch, any repo under the org). If it's looser than it needs to be, that's worth a follow-up, not a same-day fix.
