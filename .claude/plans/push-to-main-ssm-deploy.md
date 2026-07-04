# Push-to-main → EC2 deploy via SSM

## Context

Deploys today are manual: `deploy/build.sh` builds/pushes images from the laptop, and watchtower (being retired) or hand-run compose commands update the EC2 host. Goal: merging to main automatically builds + pushes images and deploys them to the EC2 instance via **SSM RunCommand** (no SSH key, no inbound port), running a **versioned, idempotent deploy script** that self-heals common pre-existing container state (leftover docker-run containers, watchtower, name squatters).

Decisions already made with the user:
- The EC2 host's existing repo clone is the delivery mechanism: SSM does `git fetch` + `git reset --hard <sha>`, then runs `deploy/deploy.sh <sha>` from that commit. `deploy/.env` is gitignored/untracked so it survives resets (it already exists on the host). No `git clean`.
- **GitHub Actions builds the images and pushes to GHCR** (`ghcr.io/johnfattore/*`) using the built-in `GITHUB_TOKEN` — no Docker Hub, no registry credentials stored anywhere. Packages are made public once so the EC2 host pulls without auth (also sidesteps Docker Hub's anonymous pull limits). `deploy/build.sh` becomes a legacy/emergency tool.
- Images tagged `latest` + commit SHA; deploy pins the SHA (rollback = re-run with an older SHA).
- **Celery stays manual** — deploy.sh never touches the `celery` profile services.
- SSM targets the instance by **EC2 tag** (`App=fattorestreet`), not instance ID.
- GitHub → AWS auth via **OIDC** (no stored AWS keys). With GHCR, the design has **zero stored secrets in GitHub**.

## Part 1 — Repo changes (Claude implements)

### 1. `deploy/deploy.sh` (new, executable)
Idempotent converge script, run as root on the host from `deploy/`:
1. Ensure `dockerNet` bridge network exists.
2. Evict name-squatting containers: for `django`, `springboot`, `nginx`, `celery`, `watchtower` — if a container exists whose `com.docker.compose.project` label ≠ `fattorestreet`, `docker rm -f` it. (This makes the *first* automated deploy perform the one-time cutover from the old docker-run/watchtower setup described in `deploy/compose.sh`.) Celery compose services (`celery-worker`/`celery-beat`) are left alone.
3. `docker compose -f docker-compose.infra.yml up -d` (safe no-op unless the pinned infra changed).
4. `export TAG=<sha>` → `docker compose pull` → `docker compose up -d --remove-orphans`.
5. `docker compose run --rm django python manage.py migrate` (Spring Boot Flyway self-migrates on start).
6. `docker image prune -f` (replaces watchtower's cleanup).
7. Health check: retry `curl -fsk https://localhost/` for ~30s; non-zero exit fails the SSM command → fails the Action.

### 2. `.github/workflows/docker-build.yml` — extend
- Build job: add `permissions: packages: write`; on push to main, `docker/login-action` against `ghcr.io` with the built-in `GITHUB_TOKEN`, then `push: true` with tags `ghcr.io/johnfattore/<image>:latest` + `:${{ github.sha }}`. PRs remain build-only (no push, no packages permission needed for the PR path). Keep the gha cache config. Add `workflow_dispatch` for manual runs.

### 3. Switch image references to GHCR
- `deploy/docker-compose.yml`: `johnfattore/<image>` → `ghcr.io/johnfattore/<image>` for django, celery-worker, celery-beat, springboot, nginx.
- `deploy/build.sh`: retag to the `ghcr.io/...` names and add a header comment marking it legacy/emergency-only (CI is the deploy path; a manual push would need a PAT with `write:packages`).
- `deploy/run.sh` stays untouched (kept as reference for the old live setup, per its own header).
- New `deploy` job (`needs: build`, main pushes only, `concurrency: group: deploy, cancel-in-progress: false`):
  - `permissions: id-token: write`; `aws-actions/configure-aws-credentials` assuming role from repo variable `AWS_DEPLOY_ROLE_ARN`, region `us-east-1`.
  - `aws ssm send-command --document-name AWS-RunShellScript --targets Key=tag:App,Values=fattorestreet` running the fixed three lines (as the clone owner for git, root for deploy.sh):
    ```
    sudo -u ec2-user git -C <CLONE_PATH> fetch origin
    sudo -u ec2-user git -C <CLONE_PATH> reset --hard ${{ github.sha }}
    <CLONE_PATH>/deploy/deploy.sh ${{ github.sha }}
    ```
    `CLONE_PATH` assumed `/home/ec2-user/FattoreStreet` — **user confirms** (checklist below); kept as a workflow env var so it's one line to change.
  - Poll `aws ssm get-command-invocation` until terminal state; print stdout/stderr into the Action log; fail on non-Success.

### 4. Docs
- `deploy/compose.sh`: update the runbook header — routine deploys are now automated on merge to main; manual celery update command stays documented.
- `deploy/DEPLOY.md` (new): the one-time AWS/GitHub setup runbook with exact CLI/console steps for everything in Part 2 (OIDC provider, IAM role JSON trust + permissions policies, instance-role attachment, tags, GitHub variable, GHCR package visibility), plus rollback (`re-run deploy with old SHA` / manual `TAG=<sha> docker compose ...`).
- `CLAUDE.md`: no changes needed (deploy specifics live in `deploy/`).

## Part 2 — Manual setup (user does; DEPLOY.md will contain exact commands)

**AWS IAM**
- [ ] Create the GitHub OIDC identity provider in IAM (`token.actions.githubusercontent.com`) if it doesn't exist yet.
- [ ] Create IAM role (e.g. `github-deploy-fattorestreet`): trust policy pinned to `repo:JohnFattore/FattoreStreet:ref:refs/heads/main`; permissions policy allowing `ssm:SendCommand` on the `AWS-RunShellScript` document + instances with tag `App=fattorestreet`, and `ssm:GetCommandInvocation`.
- [ ] Attach `AmazonSSMManagedInstanceCore` to the existing EC2 instance role (the one that already reads `fattorestreet/env`).

**EC2 instance**
- [ ] Tag the instance `App=fattorestreet`.
- [ ] Verify SSM agent is registered: `aws ssm describe-instance-information` shows the instance Online.
- [ ] Confirm the repo clone path (plan assumes `/home/ec2-user/FattoreStreet`) and that it's owned by `ec2-user` with `origin` pointing at GitHub over HTTPS (public repo, no creds needed).
- [ ] Confirm the clone's `deploy/.env` has the real `SECRETS_ARN` (already exists per user).
- [ ] Nothing else — the first automated deploy performs the watchtower/docker-run cutover itself, and the compose file's new `ghcr.io/...` image names pull fresh on that deploy.
- [ ] (Optional, after first successful deploy) Remove port 22 from the security group.
- [ ] (Optional) `docker image rm` the old `johnfattore/*` Docker Hub-named images — they're tagged, so `docker image prune` won't reclaim them.

**GitHub repo settings**
- [ ] Variable: `AWS_DEPLOY_ROLE_ARN` = the new role's ARN.
- [ ] After the first main-branch push builds the images: make the three GHCR packages (`django`, `springboot`, `nginx`) **public** in package settings, and link them to the repo if not auto-linked. Until they're public, the EC2 host's anonymous pull will 401 — so do this before the first deploy attempt, or expect one red run.
- [ ] No registry secrets needed — `GITHUB_TOKEN` handles the push.

## Verification

1. Repo-side before merge: `docker compose config` against the modified files; shellcheck-style review of `deploy.sh`; workflow YAML sanity (actionlint if available).
2. End-to-end after the user finishes Part 2: trigger the workflow via `workflow_dispatch` (or merge this PR to main). Watch the Action: build+push succeeds, SSM invocation output shows eviction/pull/up/migrate/health-check, job goes green.
3. On the host (Session Manager or the Action output): `docker compose ps` shows django/springboot/nginx running with the SHA-tagged images, watchtower gone; `curl -f https://fattorestreet.com/` returns 200.
4. Rollback drill (optional): re-run the deploy job from an older commit and confirm the older tag comes up.
