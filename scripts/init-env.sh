#!/bin/sh
# Creates .env from .env.example and appends random dev secrets. Does nothing if .env exists,
# so secrets stay stable across restarts. `make reset` removes .env together with the volumes.
set -eu
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  exit 0
fi

secret() {
  LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32
}

umask 077
{
  cat .env.example
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
echo "Created .env with generated dev secrets."
