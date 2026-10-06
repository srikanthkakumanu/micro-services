#!/bin/sh
# Deploys the platform to the current Kubernetes cluster (Docker Desktop locally): builds the images,
# creates the bootstrap Secret from the generated .env, applies the dev overlay and waits for
# everything to be ready, in the same order as scripts/start.sh. The one-shot steps (seeding
# Vault, creating databases, the Keycloak bootstrap, onboarding) run as Jobs inside the cluster.
#
# Usage: scripts/k8s-up.sh [dev|qa|prod]            deploy that overlay (default: dev)
#        scripts/k8s-up.sh [dev|qa|prod] --delete   remove its namespace and everything in it
#
# Only dev is meant for the local cluster. The qa and prod overlays carry placeholder host names
# and a placeholder Git remote for the configuration; set those before deploying them.
set -eu
cd "$(dirname "$0")/.."

ENVIRONMENT=dev
case "${1:-}" in
  dev|qa|prod) ENVIRONMENT=$1; shift ;;
esac
NAMESPACE="identity-$ENVIRONMENT"
OVERLAY="k8s/overlays/$ENVIRONMENT"

if [ "${1:-}" = "--delete" ]; then
  kubectl delete namespace "$NAMESPACE" --ignore-not-found
  exit 0
fi

# Only secrets are taken from .env. An existing one is reused and brought up to date.
if [ -f .env ]; then ./scripts/init-env.sh "$(sed -n 's/^ENVIRONMENT=//p' .env)"; else ./scripts/init-env.sh "$ENVIRONMENT"; fi

echo "==> Building images"
IMAGES="bootstrap eureka-discovery config-server user-service auth-service api-gateway books-service"
# The bootstrap image is shared by every one-shot job; building one of them builds it.
docker compose build vault-seed eureka-discovery config-server user-service auth-service api-gateway books-service

# Each deployment gets its own image tag. A node keeps the image it already has for a tag, so
# re-using :latest would leave the previous build running.
TAG=$(date +%Y%m%d%H%M%S)
for image in $IMAGES; do
  docker tag "identity-platform-$image:latest" "identity-platform-$image:$TAG"
done

echo "==> Applying $OVERLAY"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
# Secrets are never committed: this one is generated from the local .env. It holds what the
# vault-seed job puts into Vault, and the Vault tokens of the workloads. Every database
# credential is then read from Vault, by Postgres and Keycloak too.
grep -E '^[A-Z_]+=' .env | grep -E 'PASSWORD|TOKEN|_USERNAME' > .env.k8s-secret
kubectl -n "$NAMESPACE" create secret generic platform-bootstrap --from-env-file=.env.k8s-secret \
  --dry-run=client -o yaml | kubectl apply -f -
rm -f .env.k8s-secret
# A Job cannot be changed in place; replace them so they run again. They keep what exists.
kubectl -n "$NAMESPACE" delete job vault-seed db-init keycloak-bootstrap onboard bootstrap --ignore-not-found
kubectl kustomize --load-restrictor LoadRestrictionsNone "$OVERLAY" \
  | sed "s#\\(image: identity-platform-[a-z-]*\\):latest#\\1:$TAG#" | kubectl apply -f -

echo "==> Waiting for rollout"
rolled_out() {
  for workload in "$@"; do
    kubectl -n "$NAMESPACE" rollout status "$workload" --timeout=600s
  done
}
completed() {
  kubectl -n "$NAMESPACE" wait --for=condition=complete "job/$1" --timeout=600s
}
rolled_out deployment/vault deployment/mailpit
completed vault-seed
rolled_out statefulset/postgres
completed db-init
rolled_out deployment/keycloak
completed keycloak-bootstrap
rolled_out deployment/eureka-discovery deployment/config-server
rolled_out deployment/user-service deployment/auth-service deployment/api-gateway
completed onboard
rolled_out deployment/books-service

if [ "$ENVIRONMENT" != dev ]; then
  echo "Deployed to namespace $NAMESPACE. It is reached through the Ingress in $OVERLAY."
  exit 0
fi

# The gateway is ready a little before it has fetched the registry and can route. Wait until it
# reaches every service, as scripts/start.sh does: an empty login is a 400 from auth-service, an
# empty password-reset request a 400 from user-service, and books-service serves its API description.
echo "==> Waiting until the gateway can route to the services"
GATEWAY=http://localhost:30211
attempts=0
while :; do
  auth=$(curl -s -o /dev/null -m 5 -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' "$GATEWAY/api/v1/auth/login" || true)
  users=$(curl -s -o /dev/null -m 5 -w '%{http_code}' -X POST -H 'Content-Type: application/json' -d '{}' "$GATEWAY/api/v1/users/password-reset-requests" || true)
  books=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$GATEWAY/docs/books-service/v3/api-docs" || true)
  [ "$auth" = 400 ] && [ "$users" = 400 ] && [ "$books" = 200 ] && break
  attempts=$((attempts + 1))
  if [ "$attempts" -ge 90 ]; then
    echo "The gateway cannot reach the services (auth-service: $auth, user-service: $users, books-service: $books)." >&2
    exit 1
  fi
  sleep 2
done

cat <<INFO

Deployed to namespace $NAMESPACE.
  Gateway   http://localhost:30211
  Keycloak  http://localhost:30080  (console, for debugging only)
  Mailpit   http://localhost:30025

The local cluster and the Compose stack share the memory of Docker Desktop. With about 8 GB,
run one of them at a time (scripts/stop.sh stops Compose and keeps its data).

Run the end-to-end suite against it with: make test-e2e-k8s
INFO
