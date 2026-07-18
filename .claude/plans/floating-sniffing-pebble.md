# pgadmin4 hardening round 2: network segmentation, postfix off, limits, pinned tag

## Context

PR #102 (branch `pgadmin-least-privilege`) already gave pgadmin a read-only DB role and minimal container privileges (no host port, read-only rootfs, cap_drop ALL, no-new-privileges). This round applies the remaining privilege cleanups agreed with the user (items 2–5 of the follow-up list), added to the same open PR:

2. **Network segmentation** — pgadmin currently shares `dockerNet` with every container, so a compromised pgadmin could reach unauthenticated redis and the app containers. Also, postgres (5432) and redis (6379) are published on `0.0.0.0`, protected only by the EC2 security group.
3. **Disable Postfix** — the pgadmin image starts a root-owned Postfix for password-reset emails nobody uses.
4. **Resource limits** — cap memory and PIDs so the container can't starve the host (which also runs the DB).
5. **Pin image tag** — `dpage/pgadmin4` floats on `latest`; Docker Hub has a rolling major tag `9` (verified), matching the repo's postgres:17 / redis:8 pinning convention.

User decision: postgres host port becomes `127.0.0.1:5432:5432`; redis host port is removed entirely (apps use dockerNet DNS, unaffected).

## Changes

### `deploy/docker-compose.infra.yml`
- Top-level `networks:`: add `pgadminNet` (external), keep `dockerNet`.
- `postgres`: `ports:` → `"127.0.0.1:5432:5432"`; `networks: [dockerNet, pgadminNet]`.
- `redis`: remove `ports:` block.
- `pgadmin4`:
  - `image: dpage/pgadmin4:9`
  - `networks: [pgadminNet]` (off dockerNet — can only reach postgres + be reached by nginx)
  - env: add `PGADMIN_DISABLE_POSTFIX: "True"`
  - `mem_limit: 512m`, `pids_limit: 100`
  - update the least-privilege comment block accordingly

### `deploy/docker-compose.yml`
- `nginx`: `networks: [dockerNet, pgadminNet]` so the `/pgadmin4/` upstream still resolves.
- Top-level `networks:`: add `pgadminNet` (external).

### `deploy/run.sh` (manual docker-run mirror)
- Add `sudo docker network create --driver bridge pgadminNet` next to the dockerNet creation.
- postgres run: `-p 127.0.0.1:5432:5432`.
- redis run: drop `-p 6379:6379`.
- pgadmin run: `dpage/pgadmin4:9`, `--network pgadminNet`, `-e PGADMIN_DISABLE_POSTFIX=True`, `--memory 512m --pids-limit 100`.
- nginx run: add `--network dockerNet` note — docker run only accepts one `--network` at create; add a `sudo docker network connect pgadminNet nginx` line after the nginx run.
- postgres run: needs both networks too → `docker network connect pgadminNet postgres` line after it.

### `deploy/compose.sh`
- Step for one-time host setup: add `sudo docker network create --driver bridge pgadminNet` beside the existing network creation reference.

### `docs/DEPLOYMENT.md`
- Extend the "pgadmin4 (least privilege)" section: isolated `pgadminNet` (nginx + postgres + pgadmin only), postfix disabled, memory/PID limits, pinned `:9` tag; note postgres now binds 127.0.0.1 and redis has no host port (admin via `docker exec`, laptop access via SSM port forward).

## Rollout ordering (goes in PR body)

`pgadminNet` must exist on the EC2 host **before** the merge deploy runs, or `deploy.sh`'s `compose up` fails:

```bash
sudo docker network create --driver bridge pgadminNet
```

Everything else converges automatically on deploy (deploy.sh runs `up -d` on both compose files, recreating postgres/redis/pgadmin/nginx with the new settings). Brief postgres recreate blip is expected. pgadmin UI connection (`pgadmin_ro`) is unaffected — host `postgres` resolves over pgadminNet.

## Verification (local Docker)

1. `docker network create testDockerNet && docker network create testPgadminNet`.
2. Run postgres:17 on both nets, a redis:8 on testDockerNet only, pgadmin with the full new flag set (`:9` tag, read-only, tmpfs, cap-drop, no-new-privileges, postfix disabled, mem/pids limits) on testPgadminNet only.
3. Assert: login page HTTP 200 on the mapped port; from inside the pgadmin container Python, TCP connect to `postgres:5432` succeeds, and name resolution/connect to `redis` fails (no lateral movement).
4. `docker stats --no-stream` shows the 512MiB limit; `docker inspect` shows PidsLimit 100.
5. Clean up all test containers/networks.
6. `docker compose -f deploy/docker-compose.infra.yml config` and `docker compose -f deploy/docker-compose.yml config` to validate syntax (external networks may need stubs or `docker network create` locally first).

Then commit to `pgadmin-least-privilege`, push (updates PR #102), and update the PR body with the new scope + the pre-merge network-creation step.
