#!/bin/sh
# Starts the platform in dependency order and waits at every step, so nothing starts before what
# it needs is ready:
#
#   1. Vault, then the job that seeds it (every credential of the platform lives in Vault)
#   2. Postgres and Mailpit; Postgres gets its superuser credentials from Vault
#   3. the job that creates the databases and their users, with credentials from Vault
#   4. Keycloak (imports the realm on first start), then the job that applies its secrets
#   5. Eureka, Config Server
#   6. user-service, auth-service
#   7. api-gateway, then wait until it can route to both services
#   8. the onboarding job (registers the business services with the platform through its APIs)
#   9. books-service and video-service, then wait until the gateway can route to them
#
# Usage: scripts/start.sh [dev|qa|prod] [--build] [service ...]
#
#   dev|qa|prod   which configuration to run with (default: dev, or whatever .env already is)
#   --build       rebuild the service images first
#   service ...   start only these (and wait for them); what they depend on must be running
#
# Safe to run again: running containers are left alone and the jobs keep what already exists.
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
    -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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

# A one-shot job; skipped when only some services were asked for.
job() {
  [ -z "$ONLY" ] || return 0
  say "$1"
  run_job "$2"
}

say "Starting the platform with the $ENVIRONMENT configuration"
stage "Secret store" $SECRET_STORE
# Vault runs in memory in this setup, so it is empty after every restart and is seeded on every
# start. Client secrets it still holds are kept.
job "Seeding Vault" vault-seed
job "Fetching the database credentials of Postgres and Keycloak from Vault" secrets-fetch
stage "Infrastructure" $INFRASTRUCTURE
job "Creating databases and their users with the credentials in Vault" db-init
stage "Identity provider" $IDENTITY_PROVIDER
job "Applying the secrets in Vault to Keycloak" keycloak-bootstrap

stage "Registry and configuration" $PLATFORM_SUPPORT
stage "Services" $PLATFORM_SERVICES
stage "Gateway" $EDGE

if [ -z "$ONLY" ] || in_list api-gateway $ONLY; then
  say "Waiting until the gateway can route to the services"
  wait_for_routing
fi

job "Onboarding business services through the platform APIs" onboard
stage "Business services" $BUSINESS_SERVICES
started=""
for service in $BUSINESS_SERVICES; do
  if [ -z "$ONLY" ] || in_list "$service" $ONLY; then
    started="$started $service"
  fi
done
if [ -n "$started" ]; then
  say "Waiting until the gateway can route to:$started"
  # shellcheck disable=SC2086
  wait_for_business_routing $started
fi

say "The platform is up ($ENVIRONMENT)"
print_urls
