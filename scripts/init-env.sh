#!/bin/sh
# Creates .env from .env.<environment>.example and appends random secrets. If .env already exists
# its values are kept, and only settings and secrets that were added since are appended, so
# secrets stay stable across restarts. `make reset` removes .env together with the volumes.
#
# The values in .env are read by one thing only: the job that seeds Vault. Everything else gets
# its credentials from Vault.
#
# Usage: scripts/init-env.sh [dev|qa|prod]   (default: dev)
set -eu
cd "$(dirname "$0")/.."

ENVIRONMENT=${1:-dev}
TEMPLATE=".env.$ENVIRONMENT.example"
if [ ! -f "$TEMPLATE" ]; then
  echo "Unknown environment '$ENVIRONMENT': there is no $TEMPLATE" >&2
  exit 1
fi

# Secrets that are generated unless the template fixes them (dev fixes some database passwords).
GENERATED="POSTGRES_PASSWORD KEYCLOAK_DB_PASSWORD USER_DB_PASSWORD AUTH_DB_PASSWORD \
BOOKS_DB_ADMIN_PASSWORD VIDEO_DB_ADMIN_PASSWORD APP_DB_PASSWORD \
KEYCLOAK_ADMIN_PASSWORD BOOTSTRAP_VAULT_TOKEN API_GATEWAY_VAULT_TOKEN USER_SERVICE_VAULT_TOKEN \
AUTH_SERVICE_VAULT_TOKEN BOOKS_SERVICE_VAULT_TOKEN VIDEO_SERVICE_VAULT_TOKEN CONFIG_SERVER_VAULT_TOKEN"

secret() {
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32
}

has() {
  grep -q "^$1=" "$2"
}

umask 077
if [ -f .env ]; then
  current=$(sed -n 's/^ENVIRONMENT=//p' .env)
  if [ "$current" != "$ENVIRONMENT" ]; then
    echo "The existing .env is for '$current', not '$ENVIRONMENT'. Run 'make reset' first." >&2
    exit 1
  fi
  added=""
  # Settings the template has gained since .env was created.
  for name in $(sed -n 's/^\([A-Z][A-Z0-9_]*\)=.*/\1/p' "$TEMPLATE"); do
    if ! has "$name" .env; then
      grep "^$name=" "$TEMPLATE" >> .env
      added="$added $name"
    fi
  done
  for name in $GENERATED; do
    if ! has "$name" .env; then
      echo "$name=$(secret)" >> .env
      added="$added $name"
    fi
  done
  # The dev Vault root token is a setting of the template, not a generated secret: follow it.
  wanted=$(sed -n 's/^VAULT_DEV_ROOT_TOKEN=//p' "$TEMPLATE")
  if [ "$(sed -n 's/^VAULT_DEV_ROOT_TOKEN=//p' .env)" != "$wanted" ]; then
    sed "s|^VAULT_DEV_ROOT_TOKEN=.*|VAULT_DEV_ROOT_TOKEN=$wanted|" .env > .env.tmp && mv .env.tmp .env
    added="$added VAULT_DEV_ROOT_TOKEN(changed)"
  fi
  [ -n "$added" ] && echo "Updated .env:$added"
  exit 0
fi

{
  cat "$TEMPLATE"
  echo
  echo "# Generated secrets. Never commit this file."
  for name in $GENERATED; do
    has "$name" "$TEMPLATE" || echo "$name=$(secret)"
  done
  # Must satisfy the realm password policy: upper, lower, digit, special, 12+ characters.
  echo "PLATFORM_ADMIN_PASSWORD=Pa1!$(secret)"
} > .env
echo "Created .env for $ENVIRONMENT with generated secrets."
