#!/bin/sh
# Renew the Let's Encrypt certificate for fattorestreet.com and reload nginx.
#
# Run as root on the EC2 host by the fattorestreet-cert-renew systemd timer
# (deploy/systemd/, installed by deploy.sh) twice a day. Safe to run by hand:
#   sudo ./renew-cert.sh
#
# Every run is a no-op until the certificate is within 30 days of expiry
# (certbot's --keep-until-expiring), so running it often costs nothing.
# Requesting both names with --expand also converges the certificate to cover
# www.fattorestreet.com: the first run after www was added expands the existing
# fattorestreet.com lineage in place, so nginx's ssl_certificate paths under
# /etc/letsencrypt/live/fattorestreet.com/ never change.
#
# Exits non-zero if certbot fails or if the certificate nginx is serving
# expires within MIN_VALID_DAYS; the failure shows up in
#   systemctl status fattorestreet-cert-renew
#   journalctl -u fattorestreet-cert-renew

set -eu

# Pinned rather than :latest so a certbot release cannot change renewal
# behavior unannounced. Bump deliberately.
CERTBOT_IMAGE=certbot/certbot:v5.8.0
CERTBOT_DIR=/mnt/ebs/certbot
DOMAIN=fattorestreet.com
WWW_DOMAIN=www.fattorestreet.com
MIN_VALID_DAYS=14

# certbot runs in its own container and cannot reach docker, so its deploy
# hook only drops this marker on the shared volume; the host side of this
# script reloads nginx when it finds it.
RENEWED_MARKER="$CERTBOT_DIR/letsencrypt/.renewed"

certbot() {
  docker run --rm \
    -v "$CERTBOT_DIR/letsencrypt:/etc/letsencrypt" \
    -v "$CERTBOT_DIR/www:/var/www/certbot" \
    "$CERTBOT_IMAGE" "$@" \
    --non-interactive \
    --deploy-hook 'touch /etc/letsencrypt/.renewed'
}

status=0

echo "=== certbot: ensure $DOMAIN + $WWW_DOMAIN, renew if due ==="
if ! certbot certonly \
  --webroot -w /var/www/certbot \
  -d "$DOMAIN" -d "$WWW_DOMAIN" \
  --expand --keep-until-expiring; then
  # Most likely www validation failing (no DNS record pointing at this host).
  # Don't let that block renewal of the name the site actually runs on:
  # renew the existing lineage as-is, but still fail the run so it's visible.
  echo "certonly for both names failed; falling back to renewing the existing certificate" >&2
  status=1
  certbot renew --webroot -w /var/www/certbot || status=1
fi

if [ -f "$RENEWED_MARKER" ]; then
  echo "=== certificate changed: reloading nginx ==="
  docker exec nginx nginx -t
  docker exec nginx nginx -s reload
  rm -f "$RENEWED_MARKER"
fi

# Check what nginx is actually serving, not the files on disk: this catches a
# failed renewal, a failed reload, and a wrong certificate path alike.
echo "=== checking served certificate ==="
served=$(openssl s_client -connect 127.0.0.1:443 -servername "$DOMAIN" </dev/null 2>/dev/null \
  | openssl x509) || {
  echo "could not read the certificate nginx is serving on 127.0.0.1:443" >&2
  exit 1
}
printf '%s\n' "$served" | openssl x509 -noout -enddate -ext subjectAltName
if ! printf '%s\n' "$served" | openssl x509 -noout -checkend $((MIN_VALID_DAYS * 86400)) >/dev/null; then
  echo "served certificate expires within $MIN_VALID_DAYS days" >&2
  exit 1
fi

exit "$status"
