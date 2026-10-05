#!/bin/sh
# Applies the secrets held in Vault to the imported realm through the Admin API: the platform
# client secrets and the bootstrap PLATFORM_ADMIN password. The realm file itself has no secrets.
#
# Required: VAULT_ADDR, VAULT_TOKEN, KEYCLOAK_URL, KEYCLOAK_REALM.
set -eu

: "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}" "${KEYCLOAK_URL:?}" "${KEYCLOAK_REALM:?}"

MOUNT=secret
admin_api="$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM"

admin_username=$(vault kv get -mount="$MOUNT" -field=admin-username keycloak)
admin_password=$(vault kv get -mount="$MOUNT" -field=admin-password keycloak)

token=$(curl -fsS "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$admin_username" --data-urlencode "password=$admin_password" | jq -r .access_token)

kc() {
  curl -fsS -H "Authorization: Bearer $token" -H "Content-Type: application/json" "$@"
}

for client in api-gateway user-service auth-service; do
  secret=$(vault kv get -mount="$MOUNT" -field=platform.keycloak.client-secret "$client")
  id=$(kc "$admin_api/clients?clientId=$client" | jq -r '.[0].id')
  kc "$admin_api/clients/$id" | jq --arg secret "$secret" '.secret = $secret' \
    | kc -X PUT "$admin_api/clients/$id" --data-binary @- > /dev/null
  echo "Set client secret for $client."
done

platform_admin=$(vault kv get -mount="$MOUNT" -field=platform-admin-username keycloak)
platform_admin_password=$(vault kv get -mount="$MOUNT" -field=platform-admin-password keycloak)
user_id=$(kc "$admin_api/users?username=$platform_admin&exact=true" | jq -r '.[0].id')
jq -n --arg value "$platform_admin_password" '{type: "password", value: $value, temporary: false}' \
  | kc -X PUT "$admin_api/users/$user_id/reset-password" --data-binary @- > /dev/null
echo "Set password for $platform_admin."

echo "Keycloak bootstrap complete."
