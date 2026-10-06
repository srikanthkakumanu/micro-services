#!/bin/sh
# Shows what is running, whether it is healthy, and whether the gateway can reach the services.
#
# Usage: scripts/status.sh
set -eu
cd "$(dirname "$0")/.."
. scripts/lib.sh
require_docker

echo "Environment: $(setting ENVIRONMENT)"
echo
printf '%-18s %s\n' SERVICE STATE
for service in $ALL_SERVICES; do
  state=$(docker compose ps --all --format '{{.Status}}' "$service" 2>/dev/null | head -1)
  printf '%-18s %s\n' "$service" "${state:-not created}"
done

gateway="http://localhost:$(setting GATEWAY_PORT)"
code() {
  curl -s -o /dev/null -m 5 -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' "$gateway$1" || true
}
echo
case "$(code /api/v1/auth/login)/$(code /api/v1/users/password-reset-requests)" in
  400/400) echo "Gateway routing: ok (auth-service and user-service reachable)" ;;
  000/*|*/000) echo "Gateway routing: the gateway is not reachable" ;;
  *) echo "Gateway routing: not ready (the gateway cannot reach a service yet)" ;;
esac
for service in $BUSINESS_SERVICES; do
  case "$(business_routing "$service")" in
    200|404) echo "$service routing: ok (using it requires a login and one of its roles)" ;;
    000) ;;
    *) echo "$service routing: not ready (the gateway cannot reach it)" ;;
  esac
done
print_urls
