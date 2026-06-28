# Secrets from ARN

Convert a container so it stops receiving config as a long list of `-e KEY=value`
flags on `docker run`, and instead receives **one** env var -- `SECRETS_ARN` --
and fetches the rest from AWS Secrets Manager at container start.

Use this when the user wants to move a service's secrets/config out of
`kubernetes/run.sh` and into Secrets Manager. The springboot service is the
reference implementation; mirror it for any other container.

## The pattern

1. **An entrypoint wrapper** baked into the image reads `SECRETS_ARN`, pulls the
   secret JSON from Secrets Manager, exports each key as an env var, then `exec`s
   the real command. If `SECRETS_ARN` is unset it just execs the command, so the
   same image still works on ECS/Fargate (which injects secrets natively) and in
   local dev (where you pass `-e` directly).
2. **The Dockerfile** installs the AWS CLI v2 + `jq`, copies the entrypoint, and
   moves the old startup command from `ENTRYPOINT` into `CMD` (the entrypoint
   `exec "$@"`s the CMD).
3. **`run.sh`** drops the per-key `-e` flags and passes only `SECRETS_ARN` (+
   `AWS_REGION`).
4. **AWS setup** (one-time, outside the repo): create the secret, grant the EC2
   instance role read access, and raise the IMDSv2 hop limit.

## Reference implementation (springboot)

- Entrypoint: `springboot/docker-entrypoint.sh`
- Dockerfile runtime stage: `springboot/Dockerfile`
- Run command: the `# springboot` block in `kubernetes/run.sh`

## Steps to apply to a container `<svc>`

1. **Inventory the env.** Find the `# <svc>` block in `kubernetes/run.sh` and
   list every `-e KEY=value`. These become the keys of the secret's JSON object.
   Split out anything that must stay an explicit flag (e.g. `AWS_REGION`, run-mode
   selectors read before secrets load) -- only app config moves into the secret.

2. **Add the entrypoint.** Copy `springboot/docker-entrypoint.sh` into the
   service dir verbatim -- it is service-agnostic. (Shell base images: it uses
   POSIX `sh`, fine for Debian/Ubuntu/Alpine.)

3. **Edit the Dockerfile.**
   - Install AWS CLI v2 + `jq` in the *runtime* stage. Pick the install method
     for the base image:
     - Debian/Ubuntu (e.g. `eclipse-temurin`, `python:slim`):
       ```dockerfile
       RUN apt-get update \
           && apt-get install -y --no-install-recommends curl unzip jq ca-certificates \
           && curl -sSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscliv2.zip \
           && unzip -q /tmp/awscliv2.zip -d /tmp && /tmp/aws/install \
           && rm -rf /tmp/aws /tmp/awscliv2.zip /var/lib/apt/lists/*
       ```
     - Alpine: `RUN apk add --no-cache aws-cli jq` (AWS CLI v2 is in `community`).
   - Copy and chmod the entrypoint:
     ```dockerfile
     COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
     RUN chmod +x /usr/local/bin/docker-entrypoint.sh
     ```
   - Set `ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]` and move the old
     start command into `CMD [...]`. (If the old line was `CMD ["gunicorn", ...]`
     as in django, keep that CMD and just add the ENTRYPOINT.)

4. **Edit `kubernetes/run.sh`.** Replace the `-e KEY=value` lines in the `# <svc>`
   block with `-e SECRETS_ARN=<arn>` and `-e AWS_REGION=us-east-1`. Leave a
   comment with the `aws secretsmanager create-secret` JSON so the source of
   truth is discoverable. Don't commit real secret *values* if avoidable -- but
   note `run.sh` already contains plaintext secrets today, so flag that to the
   user as the thing this change is meant to fix.

5. **Document the AWS one-time setup** (commands the user runs, not you):
   ```bash
   # a) create the secret (JSON object of KEY -> value)
   aws secretsmanager create-secret --name fattorestreet/<svc> \
     --secret-string '{"KEY1":"val1","KEY2":"val2"}'

   # b) let the EC2 instance role read it
   aws iam put-role-policy --role-name <ec2-instance-role> \
     --policy-name read-<svc>-secret \
     --policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow",
       "Action":"secretsmanager:GetSecretValue","Resource":"<secret-arn>"}]}'

   # c) allow bridge-networked containers to reach IMDS (extra hop)
   aws ec2 modify-instance-metadata-options --instance-id <id> \
     --http-put-response-hop-limit 2 --http-tokens required
   ```
   If the secret uses a customer-managed KMS key, also grant the role
   `kms:Decrypt` on that key.

6. **Verify.**
   - `docker build` the image (or `docker buildx build --platform linux/arm64`
     to match the Graviton runtime, per `kubernetes/build.sh`).
   - Run with a real ARN and watch for `[entrypoint] loading secrets from ...`
     and a clean app start: `docker logs <svc>`.
   - A missing/forbidden secret should make the container exit non-zero (the
     entrypoint runs under `set -e`) -- fail loud, never start half-configured.

## Notes / gotchas

- **Secret shape is a flat JSON object** of string keys to values. Non-string
  values are coerced via `tostring`. Don't nest objects.
- **One shared secret**: this project keeps ALL services' config in a single
  secret, `fattorestreet/env` (a flat JSON of every key). Each container is
  pointed at the same ARN via the `SECRETS_ARN` shell var set at the top of
  `kubernetes/run.sh`; extra keys a service doesn't read are harmless. When
  onboarding a new service, add its keys to that one secret rather than creating
  a new one.
- **Multiple secrets**: `SECRETS_ARN` also accepts a comma-separated list of
  ARNs; later secrets win on key collision (not used here, but available).
- **ECS/Fargate compatibility**: the Fargate task def in
  `springboot/deploy/terraform/main.tf` injects secrets via its own `secrets`
  block and never sets `SECRETS_ARN`, so the entrypoint no-ops there -- the image
  is shared between EC2 `docker run` and Fargate unchanged.
- **Region**: defaults to `us-east-1` in the entrypoint; override with
  `-e AWS_REGION=...`.
