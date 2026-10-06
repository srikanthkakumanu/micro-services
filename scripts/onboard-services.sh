#!/bin/sh
# Onboards business services onto the platform, entirely through the platform's own APIs (never
# by editing the realm file): for every onboarding/*.json it registers the service's client,
# defines its permissions, creates the roles and attaches the permissions to them, and gives the
# service account what it needs to call other services.
#
# Safe to run again: whatever already exists is left as it is. The dev Vault runs in memory and
# has lost the client secret after a restart; when it is missing a new one is issued, which the
# service reads from Vault when it starts. A secret Vault still holds is kept, so a service that
# is running keeps working.
#
# Required: GATEWAY_URL, VAULT_ADDR, VAULT_TOKEN (read-only; for the administrator's password).
# Optional: ONBOARDING_DIR (default /bootstrap/onboarding).
set -eu

: "${GATEWAY_URL:?}" "${VAULT_ADDR:?}" "${VAULT_TOKEN:?}"
ONBOARDING_DIR=${ONBOARDING_DIR:-/bootstrap/onboarding}
MOUNT=secret

username=$(vault kv get -mount="$MOUNT" -field=platform-admin-username keycloak)
password=$(vault kv get -mount="$MOUNT" -field=platform-admin-password keycloak)

# The gateway reports healthy a little before it can route; wait until a login succeeds.
token=""
attempts=0
while [ -z "$token" ]; do
  token=$(jq -n --arg u "$username" --arg p "$password" '{username: $u, password: $p}' \
    | curl -s -X POST "$GATEWAY_URL/api/v1/auth/login" -H 'Content-Type: application/json' --data-binary @- \
    | jq -r '.accessToken // empty' 2>/dev/null || true)
  if [ -z "$token" ]; then
    attempts=$((attempts + 1))
    [ "$attempts" -ge 60 ] && { echo "Could not log in to the platform through $GATEWAY_URL." >&2; exit 1; }
    sleep 2
  fi
done

# Calls the API and succeeds on any 2xx, or on 409 (already there) when that is acceptable.
api() {
  method=$1
  path=$2
  body=${3:-}
  already_ok=${4:-}
  if [ -n "$body" ]; then
    status=$(printf '%s' "$body" | curl -s -o /tmp/response -w '%{http_code}' -X "$method" "$GATEWAY_URL$path" \
      -H "Authorization: Bearer $token" -H 'Content-Type: application/json' --data-binary @-)
  else
    status=$(curl -s -o /tmp/response -w '%{http_code}' -X "$method" "$GATEWAY_URL$path" \
      -H "Authorization: Bearer $token")
  fi
  case "$status" in
    2*) return 0 ;;
    409) [ -n "$already_ok" ] && return 0 ;;
  esac
  echo "$method $path failed with $status: $(cat /tmp/response)" >&2
  exit 1
}

for file in "$ONBOARDING_DIR"/*.json; do
  [ -f "$file" ] || continue
  client=$(jq -r .client.clientId "$file")
  echo "Onboarding $client"

  api POST /api/v1/clients "$(jq -c .client "$file")" exists

  jq -c --arg service "$client" '.permissions[] | . + {service: $service}' "$file" | while read -r permission; do
    api POST /api/v1/permissions "$permission" exists
  done

  jq -c '.roles[]' "$file" | while read -r role; do
    name=$(printf '%s' "$role" | jq -r .name)
    api POST /api/v1/roles "$(printf '%s' "$role" | jq -c '{name, description}')" exists
    printf '%s' "$role" | jq -r '.permissions[]' | while read -r permission; do
      api PUT "/api/v1/roles/$name/permissions/$client/$permission"
    done
  done

  roles=$(jq -c '{roles: (.serviceAccountRoles // [])}' "$file")
  if [ "$(printf '%s' "$roles" | jq '.roles | length')" -gt 0 ]; then
    api POST "/api/v1/clients/$client/roles" "$roles"
  fi

  if vault kv get -mount="$MOUNT" -field=client-secret "clients/$client" > /dev/null 2>&1; then
    secret_note="its client secret is still in Vault"
  else
    api POST "/api/v1/clients/$client/rotate-secret"
    secret_note="a new client secret was written to $(jq -r .secretLocation /tmp/response)"
  fi
  echo "Onboarded $client: client, $(jq '.permissions | length' "$file") permissions, $(jq '.roles | length' "$file") roles; $secret_note."
done
