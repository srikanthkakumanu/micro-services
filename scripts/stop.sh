#!/bin/sh
# Stops the platform gracefully, in the reverse of the start order:
#
#   1. api-gateway              no new requests come in
#   2. books-service            finishes requests in flight, deregisters from Eureka
#   3. user-service, auth-service   the same
#   4. Config Server, Eureka
#   5. Keycloak
#   6. Mailpit, Postgres        the database goes after everything that writes to it
#   7. Vault                    last, as it was first
#
# Each container gets STOP_TIMEOUT seconds (default 40) to shut down cleanly before it is killed.
#
# Usage: scripts/stop.sh [--keep] [--reset] [service ...]
#
#   (no option)   stop everything and remove the containers; data volumes and .env are kept
#   --keep        stop the containers but do not remove them
#   --reset       also delete the data volumes and the generated .env (asks first; --yes skips it)
#   service ...   stop only these, gracefully, and leave the rest running
#
# Vault runs in memory here, so its contents are gone after a stop. scripts/start.sh seeds it again.
set -eu
cd "$(dirname "$0")/.."
. scripts/lib.sh

KEEP=""
RESET=""
ASSUME_YES=""
ONLY=""
for argument in "$@"; do
  case "$argument" in
    --keep) KEEP=yes ;;
    --reset) RESET=yes ;;
    --yes) ASSUME_YES=yes ;;
    -h|--help) sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) is_known_service "$argument" || fail "Unknown service or option: $argument"; ONLY="$ONLY $argument" ;;
  esac
done
[ -n "$ONLY" ] && [ -n "$RESET" ] && fail "--reset stops everything; it cannot be combined with service names."

require_docker

if [ -n "$RESET" ] && [ -z "$ASSUME_YES" ]; then
  printf 'This deletes the database, every user and all generated secrets. Continue? [y/N] '
  read -r answer
  case "$answer" in
    y|Y|yes) ;;
    *) echo "Nothing was changed."; exit 0 ;;
  esac
fi

# Stops the running services of one stage and waits for them to exit.
stage() {
  title=$1
  shift
  running=$(docker compose ps --services --status running)
  wanted=""
  for service in "$@"; do
    if [ -z "$ONLY" ] || in_list "$service" $ONLY; then
      in_list "$service" $running && wanted="$wanted $service"
    fi
  done
  [ -n "$wanted" ] || return 0
  say "$title:$wanted"
  # shellcheck disable=SC2086
  docker compose stop --timeout "$STOP_TIMEOUT" $wanted
}

stage "Gateway" $EDGE
stage "Business services" $BUSINESS_SERVICES
stage "Services" $PLATFORM_SERVICES
stage "Registry and configuration" config-server eureka-discovery
stage "Identity provider" $IDENTITY_PROVIDER
stage "Infrastructure" mailpit postgres
stage "Secret store" $SECRET_STORE

if [ -n "$ONLY" ]; then
  say "Stopped:$ONLY"
  exit 0
fi

if [ -n "$RESET" ]; then
  say "Removing containers, data volumes and generated secrets"
  docker compose down --volumes --remove-orphans
  rm -f .env
elif [ -z "$KEEP" ]; then
  say "Removing containers (data volumes and .env are kept)"
  docker compose down --remove-orphans
fi
say "The platform is stopped"
