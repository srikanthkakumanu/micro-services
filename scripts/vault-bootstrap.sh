#!/bin/sh
# Seeds the dev Vault, which runs in memory and so starts empty: KV v2, every secret of the
# platform, a read-only policy per reader and a policy-scoped token per reader. It runs first,
# before Postgres and Keycloak, because they get their credentials from Vault too.
#
# This is the only place that reads credentials from the environment (.env). Everything else
# reads them from Vault. Safe to run again; existing client secrets are kept.
#
# Required: VAULT_ADDR, VAULT_TOKEN (dev root token) and the variables checked below.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}"
: "${POSTGRES_PASSWORD:?}" "${KEYCLOAK_DB_PASSWORD:?}" "${USER_DB_PASSWORD:?}" "${AUTH_DB_PASSWORD:?}"
: "${BOOKS_DB_ADMIN_USERNAME:?}" "${BOOKS_DB_ADMIN_PASSWORD:?}" "${VIDEO_DB_ADMIN_USERNAME:?}" "${VIDEO_DB_ADMIN_PASSWORD:?}"
: "${APP_DB_USERNAME:?}" "${APP_DB_PASSWORD:?}"
: "${KEYCLOAK_ADMIN_PASSWORD:?}" "${PLATFORM_ADMIN_PASSWORD:?}"
: "${BOOTSTRAP_VAULT_TOKEN:?}" "${API_GATEWAY_VAULT_TOKEN:?}" "${USER_SERVICE_VAULT_TOKEN:?}"
: "${AUTH_SERVICE_VAULT_TOKEN:?}" "${BOOKS_SERVICE_VAULT_TOKEN:?}" "${CONFIG_SERVER_VAULT_TOKEN:?}"

MOUNT=secret

if ! vault secrets list -format=json | jq -e --arg m "$MOUNT/" '.[$m].options.version == "2"' > /dev/null; then
  vault secrets enable -path="$MOUNT" -version=2 kv
fi

# Keeps a client secret that already exists so a re-run does not invalidate running services.
client_secret() {
  existing=$(vault kv get -mount="$MOUNT" -field=platform.keycloak.client-secret "$1" 2>/dev/null || true)
  if [ -n "$existing" ]; then
    echo "$existing"
  else
    LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 40
  fi
}

gateway_secret=$(client_secret api-gateway)
user_secret=$(client_secret user-service)
auth_secret=$(client_secret auth-service)

vault kv put -mount="$MOUNT" application \
  platform.bootstrap=dev > /dev/null

# --- Database credentials. Every database user name and password lives here and nowhere else.
# Infrastructure: the Postgres superuser and Keycloak's own database.
vault kv put -mount="$MOUNT" postgres \
  username=postgres \
  password="$POSTGRES_PASSWORD" > /dev/null
vault kv put -mount="$MOUNT" keycloak-db \
  database=keycloak \
  username=keycloak \
  password="$KEYCLOAK_DB_PASSWORD" > /dev/null
# Services: keys are Spring property names, so Spring Cloud Vault binds them without mapping.
vault kv put -mount="$MOUNT" user-service \
  spring.datasource.username=user_service \
  spring.datasource.password="$USER_DB_PASSWORD" \
  platform.keycloak.client-secret="$user_secret" > /dev/null
vault kv put -mount="$MOUNT" auth-service \
  spring.datasource.username=auth_service \
  spring.datasource.password="$AUTH_DB_PASSWORD" \
  platform.keycloak.client-secret="$auth_secret" > /dev/null
# booksdb and videodb: the admin owns the schema and runs migrations, the runtime account only
# reads and writes rows.
vault kv put -mount="$MOUNT" books-service \
  spring.datasource.username="$APP_DB_USERNAME" \
  spring.datasource.password="$APP_DB_PASSWORD" \
  spring.flyway.user="$BOOKS_DB_ADMIN_USERNAME" \
  spring.flyway.password="$BOOKS_DB_ADMIN_PASSWORD" > /dev/null
vault kv put -mount="$MOUNT" video-service \
  spring.datasource.username="$APP_DB_USERNAME" \
  spring.datasource.password="$APP_DB_PASSWORD" \
  spring.flyway.user="$VIDEO_DB_ADMIN_USERNAME" \
  spring.flyway.password="$VIDEO_DB_ADMIN_PASSWORD" > /dev/null

# --- Other secrets.
vault kv put -mount="$MOUNT" api-gateway \
  platform.keycloak.client-secret="$gateway_secret" > /dev/null
# Bootstrap credentials: read by the bootstrap jobs and by people debugging, never by services.
vault kv put -mount="$MOUNT" keycloak \
  admin-username=admin \
  admin-password="$KEYCLOAK_ADMIN_PASSWORD" \
  platform-admin-username=platform-admin \
  platform-admin-password="$PLATFORM_ADMIN_PASSWORD" > /dev/null

read_only_policy() {
  cat <<POLICY
path "$MOUNT/data/$1" { capabilities = ["read"] }
path "$MOUNT/data/$1/*" { capabilities = ["read"] }
path "$MOUNT/data/application" { capabilities = ["read"] }
path "$MOUNT/data/application/*" { capabilities = ["read"] }
POLICY
}

for service in api-gateway user-service cloud-config-service; do
  read_only_policy "$service" | vault policy write "$service" - > /dev/null
done

# auth-service also stores the secrets of the service clients it registers and rotates.
{
  read_only_policy auth-service
  cat <<POLICY
path "$MOUNT/data/clients/*" { capabilities = ["create", "read", "update", "delete"] }
path "$MOUNT/metadata/clients/*" { capabilities = ["read", "list", "delete"] }
POLICY
} | vault policy write auth-service - > /dev/null

# books-service also reads its own client secret, which auth-service writes when the client is
# registered or its secret rotated.
{
  read_only_policy books-service
  cat <<POLICY
path "$MOUNT/data/clients/books-service" { capabilities = ["read"] }
POLICY
} | vault policy write books-service - > /dev/null

# The one-shot jobs that run after this one (fetching runtime secrets for Postgres and Keycloak,
# creating databases, the Keycloak bootstrap, onboarding) read what they need, and write nothing.
vault policy write platform-bootstrap - > /dev/null <<POLICY
path "$MOUNT/data/*" { capabilities = ["read"] }
POLICY

service_token() {
  if ! VAULT_TOKEN="$2" vault token lookup > /dev/null 2>&1; then
    vault token create -id="$2" -policy="$1" -orphan -display-name="$1" > /dev/null
  fi
}

service_token platform-bootstrap "$BOOTSTRAP_VAULT_TOKEN"
service_token api-gateway "$API_GATEWAY_VAULT_TOKEN"
service_token user-service "$USER_SERVICE_VAULT_TOKEN"
service_token auth-service "$AUTH_SERVICE_VAULT_TOKEN"
service_token books-service "$BOOKS_SERVICE_VAULT_TOKEN"
service_token cloud-config-service "$CONFIG_SERVER_VAULT_TOKEN"

echo "Vault seeded."
