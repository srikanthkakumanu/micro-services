#!/bin/sh
# Starts the platform in dependency order and waits at every step, so nothing starts before what
# it needs is ready:
#
#   1. Postgres, Vault, Mailpit
#   2. Keycloak (imports the realm on first start)
#   3. bootstrap (seeds Vault, applies client secrets to Keycloak)
#   4. Eureka, Config Server
#   5. user-service, auth-service
#   6. api-gateway, then wait until it can route to both services
#
# Usage: scripts/start.sh [dev|qa|prod] [--build] [service ...]
#
#   dev|qa|prod   which configuration to run with (default: dev, or whatever .env already is)
#   --build       rebuild the service images first
#   service ...   start only these (and wait for them); what they depend on must be running
#
# Safe to run again: running containers are left alone and the bootstrap keeps existing secrets.
set -eu
cd "$(dirname "$0")/.."
. scripts/lib.sh

ENVIRONMENT=""
BUILD=""
ONLY=""
for argument in "$@"; do
  case "$argument" in
    dev|qa|prod) ENVIRONMENT=$argument ;;
    --build) BUILD=--build ;;
    -h|--help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) is_known_service "$argument" || fail "Unknown service or option: $argument"; ONLY="$ONLY $argument" ;;
  esac
done

docker info >/dev/null 2>&1 || fail "Docker is not running."
if [ -z "$ENVIRONMENT" ]; then
  ENVIRONMENT=dev
  [ -f .env ] && ENVIRONMENT=$(setting ENVIRONMENT)
fi
./scripts/init-env.sh "$ENVIRONMENT"

# Brings up the given services and returns once they report healthy.
stage() {
  title=$1
  shift
  wanted=""
  for service in "$@"; do
    if [ -z "$ONLY" ] || in_list "$service" $ONLY; then
      wanted="$wanted $service"
    fi
  done
  [ -n "$wanted" ] || return 0
  say "$title:$wanted"
  # shellcheck disable=SC2086
  docker compose up --detach --wait --no-deps $BUILD $wanted \
    || fail "Not healthy:$wanted. See: docker compose logs$wanted"
}

say "Starting the platform with the $ENVIRONMENT configuration"
stage "Infrastructure" $INFRASTRUCTURE
stage "Identity provider" $IDENTITY_PROVIDER

if [ -z "$ONLY" ]; then
  # Vault runs in memory in this setup, so it is empty after every restart. The bootstrap fills
  # it again; if it still has its secrets they are kept.
  say "Bootstrap: seeding Vault and applying secrets to Keycloak"
  docker compose up --no-deps --force-recreate $BUILD --exit-code-from bootstrap bootstrap >/dev/null \
    || fail "The bootstrap failed. See: docker compose logs bootstrap"
fi

stage "Registry and configuration" $PLATFORM_SUPPORT
stage "Services" $PLATFORM_SERVICES
stage "Gateway" $EDGE

if [ -z "$ONLY" ] || in_list api-gateway $ONLY; then
  say "Waiting until the gateway can route to the services"
  wait_for_routing
fi

say "The platform is up ($ENVIRONMENT)"
print_urls
