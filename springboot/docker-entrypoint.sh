#!/bin/sh
set -e

# docker-entrypoint.sh -- load app config from AWS Secrets Manager at container
# start, export it into the environment, then exec the real command (CMD).
#
# Contract: `docker run` passes ONE app env var, SECRETS_ARN -- the ARN (or name)
# of a secret whose SecretString is a JSON object of KEY -> value pairs. Every
# key in that object becomes an environment variable for the app. Multiple
# secrets may be given comma-separated; later secrets win on key collisions.
#
# Credentials: the AWS CLI resolves credentials from the environment. On EC2 that
# is the instance profile, reachable over IMDS. Bridge-networked containers add a
# network hop, so the instance must allow hop limit >= 2 (IMDSv2):
#   aws ec2 modify-instance-metadata-options \
#     --instance-id <id> --http-put-response-hop-limit 2 --http-tokens required
#
# If SECRETS_ARN is unset (e.g. on ECS/Fargate, where the platform injects
# secrets natively), this script does nothing but exec the command -- so the same
# image works in both places.

if [ -n "${SECRETS_ARN:-}" ]; then
  : "${AWS_REGION:=us-east-1}"
  export AWS_REGION
  export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"

  old_ifs=$IFS
  IFS=','
  for arn in $SECRETS_ARN; do
    IFS=$old_ifs
    [ -z "$arn" ] && continue
    echo "[entrypoint] loading secrets from $arn"
    secret_json=$(aws secretsmanager get-secret-value \
      --secret-id "$arn" --query SecretString --output text)
    # Export every key in the JSON object. tostring coerces non-string values
    # (numbers/booleans); @sh quotes safely so special chars survive eval.
    eval "$(printf '%s' "$secret_json" \
      | jq -r 'to_entries[] | "export \(.key)=\(.value | tostring | @sh)"')"
    IFS=','
  done
  IFS=$old_ifs
fi

exec "$@"
