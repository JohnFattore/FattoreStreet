# Push-to-main → EC2 deploy via SSM

**Status (2026-07-05): ✅ COMPLETE AND LIVE.** Implemented in PR [#68](https://github.com/JohnFattore/FattoreStreet/pull/68) + arm64 fix in PR [#69](https://github.com/JohnFattore/FattoreStreet/pull/69); first fully green automated deploy shipped #69's merge commit to production. Only optional items remain (port 22 removal, rollback drill).

## Context

Deploys today are manual: `deploy/build.sh` builds/pushes images from the laptop, and watchtower (being retired) or hand-run compose commands update the EC2 host. Goal: merging to main automatically builds + pushes images and deploys them to the EC2 instance via **SSM RunCommand** (no SSH key, no inbound port), running a **versioned, idempotent deploy script** that self-heals common pre-existing container state (leftover docker-run containers, watchtower, name squatters).

Decisions already made with the user:
- The EC2 host's existing repo clone is the delivery mechanism: SSM does `git fetch` + `git reset --hard <sha>`, then runs `deploy/deploy.sh <sha>` from that commit. `deploy/.env` is gitignored/untracked so it survives resets (it already exists on the host). No `git clean`.
- **GitHub Actions builds the images and pushes to GHCR** (`ghcr.io/johnfattore/*`) using the built-in `GITHUB_TOKEN` — no Docker Hub, no registry credentials stored anywhere. Packages are made public once so the EC2 host pulls without auth (also sidesteps Docker Hub's anonymous pull limits). `deploy/build.sh` becomes a legacy/emergency tool.
- Images tagged `latest` + commit SHA; deploy pins the SHA (rollback = re-run with an older SHA).
- **Celery stays manual** — deploy.sh never touches the `celery` profile services.
- SSM targets the instance by **EC2 tag** (`App=fattorestreet`), not instance ID.
- GitHub → AWS auth via **OIDC** (no stored AWS keys). With GHCR, the design has **zero stored secrets in GitHub**.

## Part 1 — Repo changes (Claude implements) — ✅ ALL DONE in PR #68

Implementation notes vs. the original plan:
- Eviction uses graceful `docker stop` + `rm` (not `rm -f`) and also covers `postgres`/`redis`/`pgadmin4` (label ≠ `fattorestreet-infra`) — the live ones were docker-run containers and would have name-collided on the first infra `compose up`.
- `.gitignore` had a blanket `deploy.sh` rule; added a `!deploy/deploy.sh` negation so the script is actually versioned.
- `CLONE_PATH=/home/ec2-user/FattoreStreet` confirmed live via SSM; the root-git "dubious ownership" failure was reproduced on the box, validating the `sudo -u ec2-user` approach.
- `docs/DEPLOYMENT.md` Build & Deploy section also updated (auto-update-docs rule).

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
- [x] Create the GitHub OIDC identity provider in IAM (`token.actions.githubusercontent.com`).
- [x] Create IAM role `github-deploy-fattorestreet`: trust policy pinned to `repo:JohnFattore/FattoreStreet:ref:refs/heads/main`; permissions policy allowing `ssm:SendCommand` on the `AWS-RunShellScript` document + instances with tag `App=fattorestreet`, and `ssm:GetCommandInvocation`/`ListCommandInvocations`. Exact JSON: `deploy/DEPLOY.md` §2.
- [x] Attach `AmazonSSMManagedInstanceCore` to the existing EC2 instance role (`EC2CloudWatchLoggingRole`, the one that already reads `fattorestreet/env`).

**EC2 instance**
- [x] Tag the instance `App=fattorestreet`.
- [x] Verify SSM agent is registered: instance shows **Online** (needed an agent restart after the policy attach — it was holding stale no-permission credentials). Tag-targeted RunCommand proven end-to-end.
- [x] Confirm the repo clone path — `/home/ec2-user/FattoreStreet`, verified live via SSM.
- [x] Confirm the clone's `deploy/.env` has the real `SECRETS_ARN`.
- [x] Nothing else — the first automated deploy performs the watchtower/docker-run cutover itself, and the compose file's new `ghcr.io/...` image names pull fresh on that deploy. (Watchtower turned out to be already gone from the box.)
- [ ] (Optional) Remove port 22 from the security group; `aws ssm start-session` replaces SSH. Deliberately deferred until a few more deploys have gone through.
- [x] `docker image rm` the old `johnfattore/*` Docker Hub-named images (done via SSM after the first green deploy; freed ~1.5 GB).

**GitHub repo settings**
- [x] Variable: `AWS_DEPLOY_ROLE_ARN` = `arn:aws:iam::<ACCOUNT_ID>:role/github-deploy-fattorestreet`.
- [x] GHCR package visibility: turned out to be **automatic** — packages first pushed by a workflow's `GITHUB_TOKEN` are repo-linked and inherit the repo's public visibility. No manual flip was needed (the first failed pull was an arm64 manifest issue, not authorization).
- [x] No registry secrets needed — `GITHUB_TOKEN` handles the push.

## Verification

- [x] Repo-side before merge: `docker compose config` on both compose files, `sh -n` on `deploy.sh`, workflow YAML parse + job structure, jq SSM-payload generation tested.
- [x] SSM transport proven: tag-targeted `AWS-RunShellScript` ran on the instance and returned output (root, no sudo needed for docker).
- [x] End-to-end: PR #68 merged. First run's deploy failed at the pull — `no matching manifest for linux/arm64/v8`: the host is a t4g (Graviton), CI built amd64. The pull-before-evict ordering (added in `f6a2273c`) kept the site up. Fixed by PR #69 (build on `ubuntu-24.04-arm` runners); its main run went fully green: OIDC assume → SSM → git reset → pull → up → migrate → prune → health check.
- [x] On the host: `docker ps` shows django/springboot/nginx running `ghcr.io/johnfattore/*:<merge-sha>`; `curl -f https://fattorestreet.com/` → 200. (Infra had already been compose-cutover manually on 2026-07-03; deploy correctly left postgres/redis untouched. Pre-existing, unrelated: pgadmin4 has been crash-looping since that manual cutover because `PGADMIN_DEFAULT_PASSWORD` wasn't set in that shell.)
- [ ] Rollback drill (optional): re-run the deploy job from an older commit and confirm the older tag comes up.

## Outcome

Pipeline is live as of 2026-07-05: merge to main → arm64 images to GHCR → SSM deploy → health-checked. Zero stored secrets in GitHub; no SSH involved.
