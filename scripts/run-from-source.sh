#!/bin/sh
# Runs one service from its source tree with `./gradlew bootRun`, against the running stack.
# Its container is stopped first so the port is free; everything else keeps running.
# Stop it with Ctrl-C, then `scripts/start.sh <service>` puts the container back.
#
# Usage: scripts/run-from-source.sh user-service|auth-service|api-gateway
set -eu
cd "$(dirname "$0")/.."
. scripts/lib.sh
require_docker

SERVICE=${1:-}
case "$SERVICE" in
  user-service) TOKEN=$(setting USER_SERVICE_VAULT_TOKEN) ;;
  auth-service) TOKEN=$(setting AUTH_SERVICE_VAULT_TOKEN) ;;
  api-gateway) TOKEN="" ;;
  *) fail "Usage: scripts/run-from-source.sh user-service|auth-service|api-gateway" ;;
esac
[ -d "../$SERVICE" ] || fail "../$SERVICE is not checked out next to this repository."

./scripts/stop.sh "$SERVICE"
say "Running $SERVICE from ../$SERVICE with the $(setting ENVIRONMENT) configuration (Ctrl-C to stop)"
cd "../$SERVICE"
# On the host the stack is reached through its published ports.
SPRING_PROFILES_ACTIVE=$(setting ENVIRONMENT) \
CONFIG_SERVER_URL="http://localhost:$(setting CONFIG_SERVER_PORT)" \
KEYCLOAK_URL="http://localhost:$(setting KEYCLOAK_PORT)" \
KEYCLOAK_PUBLIC_URL="$(setting KEYCLOAK_PUBLIC_URL)" \
EUREKA_URL="http://localhost:$(setting EUREKA_PORT)/eureka/" \
VAULT_URI="http://localhost:$(setting VAULT_PORT)" \
VAULT_TOKEN="$TOKEN" \
CORS_ALLOWED_ORIGINS="$(setting CORS_ALLOWED_ORIGINS)" \
exec ./gradlew bootRun
