#!/bin/sh
# Deploys the platform to the local Kubernetes cluster (Docker Desktop): builds the images,
# creates the bootstrap Secret from the generated .env, applies the dev overlay and waits for
# everything to be ready. The Vault bootstrap runs as a Job inside the cluster.
#
# Usage: scripts/k8s-up.sh            deploy
#        scripts/k8s-up.sh --delete   remove the namespace and everything in it
set -eu
cd "$(dirname "$0")/.."

NAMESPACE=identity-dev
OVERLAY=k8s/overlays/dev

if [ "${1:-}" = "--delete" ]; then
  kubectl delete namespace "$NAMESPACE" --ignore-not-found
  exit 0
fi

./scripts/init-env.sh

echo "==> Building images"
docker compose build bootstrap eureka-discovery config-server user-service auth-service api-gateway

# Docker Desktop's cluster may run its own container runtime (kind mode); load the images if so.
if command -v kind >/dev/null 2>&1 && kind get clusters 2>/dev/null | grep -q .; then
  for image in bootstrap eureka-discovery config-server user-service auth-service api-gateway; do
    kind load docker-image "identity-platform-$image:latest" --name "$(kind get clusters | head -1)"
  done
fi

echo "==> Applying $OVERLAY"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
# Secrets are never committed: this one is generated from the local .env. It holds the database
# passwords Postgres and Keycloak need at first start and the Vault tokens of the services.
# Everything else the services need comes from Vault.
grep -E '^[A-Z_]+=' .env | grep -E 'PASSWORD|TOKEN' > .env.k8s-secret
kubectl -n "$NAMESPACE" create secret generic platform-bootstrap --from-env-file=.env.k8s-secret \
  --dry-run=client -o yaml | kubectl apply -f -
rm -f .env.k8s-secret
# A Job cannot be changed in place; replace it so the bootstrap runs again.
kubectl -n "$NAMESPACE" delete job bootstrap --ignore-not-found
kubectl kustomize --load-restrictor LoadRestrictionsNone "$OVERLAY" | kubectl apply -f -

echo "==> Waiting for rollout"
kubectl -n "$NAMESPACE" rollout status statefulset/postgres --timeout=300s
for workload in vault mailpit keycloak eureka-discovery config-server; do
  kubectl -n "$NAMESPACE" rollout status "deployment/$workload" --timeout=600s
done
kubectl -n "$NAMESPACE" wait --for=condition=complete job/bootstrap --timeout=600s
for workload in user-service auth-service api-gateway; do
  kubectl -n "$NAMESPACE" rollout status "deployment/$workload" --timeout=600s
done

cat <<INFO

Deployed to namespace $NAMESPACE.
  Gateway   http://localhost:30211
  Keycloak  http://localhost:30080  (console, for debugging only)
  Mailpit   http://localhost:30025

Run the end-to-end suite against it with: make test-e2e-k8s
INFO
