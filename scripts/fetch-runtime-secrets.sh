#!/bin/sh
# Postgres and Keycloak cannot talk to Vault themselves. This job reads their database
# credentials from Vault and writes them to a volume only those two containers mount, each file
# readable only by the user its container runs as.
#
# Required: VAULT_ADDR, VAULT_TOKEN (a read-only token), SECRETS_DIR.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${SECRETS_DIR:?}"

MOUNT=secret
POSTGRES_UID=999
KEYCLOAK_UID=1000

umask 077
mkdir -p "$SECRETS_DIR"
chmod 755 "$SECRETS_DIR"

# Read by the Postgres image through POSTGRES_USER_FILE and POSTGRES_PASSWORD_FILE.
vault kv get -mount="$MOUNT" -field=username postgres > "$SECRETS_DIR/postgres-username"
vault kv get -mount="$MOUNT" -field=password postgres > "$SECRETS_DIR/postgres-password"
chown "$POSTGRES_UID" "$SECRETS_DIR/postgres-username" "$SECRETS_DIR/postgres-password"

# Sourced by Keycloak's start command. Values are single-quoted so the shell takes them literally.
quoted() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}
{
  echo "KC_DB_URL_DATABASE=$(quoted "$(vault kv get -mount="$MOUNT" -field=database keycloak-db)")"
  echo "KC_DB_USERNAME=$(quoted "$(vault kv get -mount="$MOUNT" -field=username keycloak-db)")"
  echo "KC_DB_PASSWORD=$(quoted "$(vault kv get -mount="$MOUNT" -field=password keycloak-db)")"
} > "$SECRETS_DIR/keycloak-db.env"
chown "$KEYCLOAK_UID" "$SECRETS_DIR/keycloak-db.env"

chmod 400 "$SECRETS_DIR"/postgres-username "$SECRETS_DIR"/postgres-password "$SECRETS_DIR"/keycloak-db.env
echo "Runtime secrets for Postgres and Keycloak fetched from Vault."
