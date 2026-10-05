# ADR 0013: Local Kubernetes

- Status: accepted
- Date: 2026-10-05

## Decision

- **Cluster:** Docker Desktop Kubernetes. It was the only cluster on the development machine, and it can use locally built images without a registry.
- **Layout:** Kustomize, `k8s/base` (workloads and ClusterIP services) and one overlay per environment. `k8s/overlays/dev` (namespace `identity-dev`, generated ConfigMaps, external access) is the one deployed locally; `qa` and `prod` were added with ADR 0014. Every workload has one replica. Postgres is a StatefulSet with a PVC; everything else is a Deployment; the Vault and Keycloak bootstrap is a Job.
- **External access:** `LoadBalancer` services on ports 30xxx, which Docker Desktop publishes on localhost: gateway 30211, Keycloak 30080, Mailpit 30025, and for debugging and the direct-validation tests user-service 30121, auth-service 30141, Vault 30200, Config Server 30311. NodePorts were tried first; this cluster does not publish them on localhost.
- **Issuer:** Keycloak's hostname is `http://localhost:30080` with dynamic backchannel, and the overlay passes the same URL to the services as `KEYCLOAK_PUBLIC_URL`, which `application-dev.yml` uses as the issuer. Tokens obtained from the host and from inside the cluster carry the same `iss`.
- **Vault auth:** token auth, as in Compose. The Kubernetes auth method would need a service-account reviewer and per-service roles, which is disproportionate for a dev-mode, in-memory Vault.
- **Secrets:** nothing secret is committed. `scripts/k8s-up.sh` creates the Secret `platform-bootstrap` from the local, git-ignored `.env`. It holds what must exist before Vault does (database passwords for Postgres and Keycloak, the Keycloak console password, the dev Vault root token) and each service's Vault token. Everything a service needs at runtime it reads from Vault.
- **Service links are off** (`enableServiceLinks: false`). Kubernetes otherwise injects variables such as `VAULT_PORT=tcp://...`, which shadowed the Config Server's own `VAULT_PORT` setting.
- **Image tags:** `scripts/k8s-up.sh` gives every deployment a fresh image tag. With `:latest` the node kept running the image it already had for that tag, so a rebuilt service was not picked up.
- **Vault probes:** HTTP probes with a generous liveness timeout. Dev-mode Vault is in memory, and an exec probe with the default one-second timeout once restarted it on a busy node, which silently invalidated every secret and token.
- **Start order:** init containers wait for the Config Server and for the bootstrap Job to have seeded Vault, instead of letting pods crash-loop.

## Consequences

- The dev overlay reads the realm file and `service-configs` from where they live, so it must be built with `--load-restrictor LoadRestrictionsNone`. `scripts/k8s-up.sh` does that.
- Vault is in memory: if the Vault pod restarts, re-run `scripts/k8s-up.sh` to bootstrap it again.
- Hardening would replace the bootstrap Secret with an external secret store or Vault's Kubernetes auth, and the LoadBalancer services with an Ingress.
