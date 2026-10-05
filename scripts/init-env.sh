#!/bin/sh
# Creates .env from .env.<environment>.example and appends random secrets. Does nothing if .env exists,
# so secrets stay stable across restarts. `make reset` removes .env together with the volumes.
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

if [ -f .env ]; then
  current=$(sed -n 's/^ENVIRONMENT=//p' .env)
  if [ "$current" != "$ENVIRONMENT" ]; then
    echo "The existing .env is for '$current', not '$ENVIRONMENT'. Run 'make reset' first." >&2
    exit 1
  fi
  exit 0
fi

secret() {
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32
}

umask 077
{
  cat "$TEMPLATE"
  echo
  echo "# Generated dev secrets. Never commit this file."
  for name in POSTGRES_PASSWORD KEYCLOAK_DB_PASSWORD USER_DB_PASSWORD AUTH_DB_PASSWORD \
      KEYCLOAK_ADMIN_PASSWORD API_GATEWAY_VAULT_TOKEN USER_SERVICE_VAULT_TOKEN \
      AUTH_SERVICE_VAULT_TOKEN CONFIG_SERVER_VAULT_TOKEN; do
    echo "$name=$(secret)"
  done
  # Must satisfy the realm password policy: upper, lower, digit, special, 12+ characters.
  echo "PLATFORM_ADMIN_PASSWORD=Pa1!$(secret)"
} > .env
echo "Created .env for $ENVIRONMENT with generated secrets."
