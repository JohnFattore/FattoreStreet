#!/usr/bin/env sh
# Render the policy JSON in this directory with real values, into a fresh temp
# directory outside the repo. Prints the directory path; paste from there.
#
#   ./render.sh                      # resolve both values automatically
#   ACCOUNT_ID=123456789012 ./render.sh
#
# During the initial bootstrap there are no working local credentials, which is
# the whole reason the console runbook exists. So ACCOUNT_ID falls back to STS
# rather than depending on it; pass it explicitly when the CLI cannot call AWS.
set -eu

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
TFVARS="$DIR/../../springboot/deploy/terraform/terraform.tfvars"

ACCOUNT_ID="${ACCOUNT_ID:-}"
VPC_ID="${VPC_ID:-}"

if [ -z "$ACCOUNT_ID" ]; then
  ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || true)
fi
if [ -z "$VPC_ID" ] && [ -f "$TFVARS" ]; then
  VPC_ID=$(sed -n 's/^[[:space:]]*vpc_id[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$TFVARS")
fi

case "$ACCOUNT_ID" in
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
  *)
    echo "ACCOUNT_ID must be 12 digits; got '${ACCOUNT_ID}'." >&2
    echo "No local credentials during bootstrap: read it from the console" >&2
    echo "top-right account menu and pass ACCOUNT_ID=... explicitly." >&2
    exit 1 ;;
esac

case "$VPC_ID" in
  vpc-*) ;;
  *)
    echo "VPC_ID must look like vpc-...; got '${VPC_ID}'." >&2
    echo "Expected it in $TFVARS (gitignored), or pass VPC_ID=... explicitly." >&2
    exit 1 ;;
esac

OUT=$(mktemp -d)
for f in "$DIR"/*.json; do
  sed -e "s/<ACCOUNT_ID>/$ACCOUNT_ID/g" -e "s|<VPC_ID>|$VPC_ID|g" \
    "$f" > "$OUT/$(basename "$f")"
done

# A surviving placeholder produces an ARN AWS rejects as "The policy failed
# legacy parsing", which names neither the file nor the line. Catch it here.
if grep -l '<ACCOUNT_ID>\|<VPC_ID>' "$OUT"/*.json 2>/dev/null; then
  echo "Unsubstituted placeholders remain in the files above." >&2
  exit 1
fi

for f in "$OUT"/*.json; do
  python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$f" || {
    echo "Invalid JSON: $f" >&2; exit 1; }
done

echo "Rendered for account $ACCOUNT_ID, vpc $VPC_ID:"
echo "$OUT"
