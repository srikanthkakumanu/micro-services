#!/bin/sh
# Seeds the dev Vault: KV v2, every dev secret, a read-only policy per service and a
# policy-scoped token per service. Safe to run again; existing client secrets are kept.
#
# Required: VAULT_ADDR, VAULT_TOKEN (dev root token), the *_DB_PASSWORD, *_VAULT_TOKEN,
# KEYCLOAK_ADMIN_PASSWORD and PLATFORM_ADMIN_PASSWORD variables from .env.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}"
: "${USER_DB_PASSWORD:?}" "${AUTH_DB_PASSWORD:?}" "${KEYCLOAK_ADMIN_PASSWORD:?}" "${PLATFORM_ADMIN_PASSWORD:?}"
: "${API_GATEWAY_VAULT_TOKEN:?}" "${USER_SERVICE_VAULT_TOKEN:?}" "${AUTH_SERVICE_VAULT_TOKEN:?}" "${CONFIG_SERVER_VAULT_TOKEN:?}"

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

# Keys are Spring property names, so Spring Cloud Vault binds them without mapping.
vault kv put -mount="$MOUNT" application \
  platform.bootstrap=dev > /dev/null
vault kv put -mount="$MOUNT" api-gateway \
  platform.keycloak.client-secret="$gateway_secret" > /dev/null
vault kv put -mount="$MOUNT" user-service \
  spring.datasource.username=user_service \
  spring.datasource.password="$USER_DB_PASSWORD" \
  platform.keycloak.client-secret="$user_secret" > /dev/null
vault kv put -mount="$MOUNT" auth-service \
  spring.datasource.username=auth_service \
  spring.datasource.password="$AUTH_DB_PASSWORD" \
  platform.keycloak.client-secret="$auth_secret" > /dev/null
# Bootstrap credentials: read by the Keycloak bootstrap and by people debugging, never by services.
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

service_token() {
  if ! VAULT_TOKEN="$2" vault token lookup > /dev/null 2>&1; then
    vault token create -id="$2" -policy="$1" -orphan -display-name="$1" > /dev/null
  fi
}

service_token api-gateway "$API_GATEWAY_VAULT_TOKEN"
service_token user-service "$USER_SERVICE_VAULT_TOKEN"
service_token auth-service "$AUTH_SERVICE_VAULT_TOKEN"
service_token cloud-config-service "$CONFIG_SERVER_VAULT_TOKEN"

echo "Vault bootstrap complete."
