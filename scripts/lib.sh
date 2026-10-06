#!/bin/sh
# Shared by the start, stop, restart and status scripts. Not meant to be run on its own.

# The stack in the order it has to come up. Stopping goes through the same list backwards.
# Vault comes first: everything else, the database included, gets its credentials from it.
SECRET_STORE="vault"
INFRASTRUCTURE="postgres mailpit"
IDENTITY_PROVIDER="keycloak"
PLATFORM_SUPPORT="eureka-discovery config-server"
PLATFORM_SERVICES="user-service auth-service"
EDGE="api-gateway"
# Services that use the platform. They start after the gateway, once they have been onboarded.
BUSINESS_SERVICES="books-service video-service"
ALL_SERVICES="$SECRET_STORE $INFRASTRUCTURE $IDENTITY_PROVIDER $PLATFORM_SUPPORT $PLATFORM_SERVICES $EDGE $BUSINESS_SERVICES"

# How long a container gets to finish what it is doing before it is killed.
STOP_TIMEOUT=${STOP_TIMEOUT:-40}

say() {
  printf '==> %s\n' "$*"
}

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_docker() {
  docker info >/dev/null 2>&1 || fail "Docker is not running."
  [ -f .env ] || fail "There is no .env yet. Run scripts/start.sh first."
}

# Reads one setting from .env without sourcing the file into the shell.
setting() {
  sed -n "s/^$1=//p" .env | head -1
}

is_known_service() {
  for known in $ALL_SERVICES; do
    [ "$known" = "$1" ] && return 0
  done
  return 1
}

# Is the service one of the given, space-separated list?
in_list() {
  needle=$1
  shift
  for item in $*; do
    [ "$item" = "$needle" ] && return 0
  done
  return 1
}

# Waits until the gateway can actually reach both services. It reports healthy a few seconds
# before it has fetched the registry from Eureka, and answers 503 for routed calls until then.
wait_for_routing() {
  gateway="http://localhost:$(setting GATEWAY_PORT)"
  attempts=0
  while :; do
    # An empty body is a 400 from the service itself; 503 means the gateway cannot route yet.
    auth=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' \
      "$gateway/api/v1/auth/login" || true)
    users=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' \
      "$gateway/api/v1/users/password-reset-requests" || true)
    if [ "$auth" = 400 ] && [ "$users" = 400 ]; then
      return 0
    fi
    attempts=$((attempts + 1))
    [ "$attempts" -ge 60 ] && fail "The gateway cannot reach the services (auth-service: $auth, user-service: $users)."
    sleep 2
  done
}

# Asks the gateway for a business service's API description, which needs no login: 200 means the
# service answered (404 where the description is switched off, as in prod); 503 means the gateway
# cannot reach it yet.
business_routing() {
  curl -s -o /dev/null -m 5 -w '%{http_code}' \
    "http://localhost:$(setting GATEWAY_PORT)/docs/$1/v3/api-docs" || true
}

# Waits until the gateway routes to the given business services.
wait_for_business_routing() {
  for business_service in "$@"; do
    attempts=0
    while :; do
      answer=$(business_routing "$business_service")
      case "$answer" in 200|404) break ;; esac
      attempts=$((attempts + 1))
      [ "$attempts" -ge 60 ] && fail "The gateway cannot reach $business_service (answer: $answer)."
      sleep 2
    done
  done
}

# Runs a one-shot job to completion and fails with its output if it does not succeed.
run_job() {
  job=$1
  # shellcheck disable=SC2086
  if ! output=$(docker compose up --no-deps --force-recreate ${BUILD:-} --exit-code-from "$job" "$job" 2>&1); then
    printf '%s\n' "$output" | tail -20 >&2
    fail "The $job job failed. See: docker compose logs $job"
  fi
}

print_urls() {
  cat <<INFO

  Gateway (use this)   http://localhost:$(setting GATEWAY_PORT)
  Swagger UI           http://localhost:$(setting GATEWAY_PORT)/swagger-ui.html
  Mailpit (emails)     http://localhost:$(setting MAILPIT_UI_PORT)
  Keycloak (debugging) http://localhost:$(setting KEYCLOAK_PORT)
  Vault                http://localhost:$(setting VAULT_PORT)
  Eureka               http://localhost:$(setting EUREKA_PORT)
  Config Server        http://localhost:$(setting CONFIG_SERVER_PORT)
  user-service         http://localhost:$(setting USER_SERVICE_PORT)
  auth-service         http://localhost:$(setting AUTH_SERVICE_PORT)
  books-service        http://localhost:$(setting BOOKS_SERVICE_PORT)
  video-service        http://localhost:$(setting VIDEO_SERVICE_PORT)

  Administrator: platform-admin, password PLATFORM_ADMIN_PASSWORD in .env
INFO
}
