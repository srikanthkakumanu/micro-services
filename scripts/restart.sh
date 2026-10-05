#!/bin/sh
# Restarts services gracefully: each one is stopped cleanly, started again, and waited for.
# With no arguments the whole platform is stopped and started in order.
#
# Usage: scripts/restart.sh [--build] [service ...]
#
#   --build       rebuild the image of what is restarted
#   service ...   restart only these, for example: scripts/restart.sh --build auth-service
#
# Restarting vault empties it (it runs in memory here); restart everything instead.
set -eu
cd "$(dirname "$0")/.."
. scripts/lib.sh

BUILD=""
ONLY=""
for argument in "$@"; do
  case "$argument" in
    --build) BUILD=--build ;;
    -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) is_known_service "$argument" || fail "Unknown service or option: $argument"; ONLY="$ONLY $argument" ;;
  esac
done
require_docker

if [ -z "$ONLY" ]; then
  ./scripts/stop.sh --keep
  # shellcheck disable=SC2086
  exec ./scripts/start.sh $BUILD
fi

in_list vault $ONLY && fail "Restarting vault alone loses its secrets. Run scripts/restart.sh without arguments."
# shellcheck disable=SC2086
./scripts/stop.sh $ONLY
# shellcheck disable=SC2086
./scripts/start.sh $BUILD $ONLY
