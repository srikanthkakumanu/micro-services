# Identity platform

The platform root for the Keycloak-backed user and auth platform. `user-service` and `auth-service` are the front door for every routine identity and access operation; Keycloak sits behind them and is configured once, from code. This repository holds what the services share and what runs them together.

Development environment only.

| Here | What it is |
| --- | --- |
| `docker-compose.yml`, `Makefile` | The local stack |
| `keycloak/platform-realm.json` | Bootstrap realm: clients, seed roles and permissions, token mappers. No secrets. |
| `scripts/`, `bootstrap/`, `postgres/` | Secret generation, Vault and Keycloak bootstrap, database creation |
| `gradle/libs.versions.toml` | The one version catalog for every service |
| `platform-security-starter/` | Shared JWT validation, claim value objects and authority mapping |
| `e2e/` | End-to-end suite |
| `k8s/` | Kustomize base and dev overlay |
| `docs/` | ADRs, the token contract, the integration guide, progress |

The service repositories must be checked out next to this one: `user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`, `service-configs`.

## Run

Needs Docker, JDK 27 and `make`.

```bash
make up        # generate .env, build images, start everything, wait until healthy (dev)
make up ENV=qa # the same stack with the qa configuration; also ENV=prod
make ps        # status
make logs      # follow logs
make down      # stop, keep data and secrets
make reset     # stop and delete volumes and generated secrets
```

| Service | URL |
| --- | --- |
| Gateway (use this) | http://localhost:9211 |
| Swagger UI for both APIs | http://localhost:9211/swagger-ui.html |
| Mailpit (emails) | http://localhost:8025 |
| Keycloak console (debugging only) | http://localhost:8080 |
| Vault | http://localhost:8200 |
| user-service, auth-service directly | http://localhost:9121, http://localhost:9141 |
| Eureka, Config Server | http://localhost:9111, http://localhost:9311 |

Configuration is split by environment: `dev`, `qa` and `prod` ([ADR 0014](docs/adr/0014-environment-profiles.md)). Switching environment needs `make reset` first. The gateway answers 503 for the first seconds after start, until the services have registered with Eureka.

The bootstrap administrator is `platform-admin`; its password is `PLATFORM_ADMIN_PASSWORD` in the generated `.env`.

```bash
. ./.env
curl -s -X POST localhost:9211/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"platform-admin\",\"password\":\"$PLATFORM_ADMIN_PASSWORD\"}"
```

## Test

```bash
./gradlew build         # the security starter's tests
make test-e2e           # end-to-end suite against the running Compose stack
```

Each service has its own `./gradlew build`, which needs Docker for Testcontainers.

## Kubernetes

Targets Docker Desktop Kubernetes, namespace `identity-dev`. `k8s/overlays/qa` and `k8s/overlays/prod` are the same deployment with the qa and prod configuration; they carry placeholder host names and are not deployed by these targets.

```bash
make k8s-up             # build, apply, wait for rollout, bootstrap
make test-e2e-k8s       # end-to-end suite against the cluster
make k8s-down
```

Gateway at http://localhost:30211, Keycloak at http://localhost:30080, Mailpit at http://localhost:30025.

## Secrets

Nothing secret is in Git. `make up` generates `.env` with random dev passwords. The bootstrap job copies them into Vault, generates the client secrets and applies them to Keycloak. Services read from Vault with their own, policy-limited token. The one fixed value is the dev Vault root token in the `.env.<environment>.example` files, labelled as such. See [ADR 0008](docs/adr/0008-secrets-bootstrap.md).

## Documentation

- [Progress and open issues](docs/PROGRESS.md)
- [Access token contract](docs/jwt-contract.md)
- [Integrating a new service](docs/integrating-a-new-service.md)
- [Architecture decisions](docs/adr/)
- [Discovery report](docs/discovery-report.md)
