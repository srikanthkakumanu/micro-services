# micro-services

The platform root of the Keycloak-backed identity platform. This repository does not contain a service of its own. It holds what the services share and everything needed to run them together: the start and stop scripts, the Docker Compose stack, the Keycloak realm, the secret bootstrap, the shared build files, the end-to-end tests, the Kubernetes manifests and the documentation.

`user-service` and `auth-service` are the front door for every routine identity and access operation. Keycloak sits behind them, is configured once from code, and is never called directly by people or by other services.

This is a development setup. `qa` and `prod` exist as configuration profiles, not as hardened environments.

## Contents

- [The platform at a glance](#the-platform-at-a-glance)
- [Repositories](#repositories)
- [What is in this repository](#what-is-in-this-repository)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Start and stop scripts](#start-and-stop-scripts)
- [Make targets](#make-targets)
- [Addresses](#addresses)
- [First calls](#first-calls)
- [Environments](#environments)
- [Secrets](#secrets)
- [Tests](#tests)
- [Kubernetes](#kubernetes)
- [Troubleshooting](#troubleshooting)
- [Documentation](#documentation)

## The platform at a glance

```
                         client
                           |
                    +-------------+        validates the token, routes,
                    | api-gateway |        adds a correlation ID
                    +-------------+
                     /           \
          +--------------+   +--------------+     each validates the
          | user-service |   | auth-service |     token again
          +--------------+   +--------------+
            |       \          /    |     \
        user_db      \        /   auth_db   Vault (client secrets)
                    +----------+
                    | Keycloak |   the only issuer of tokens
                    +----------+

   eureka-discovery   registry the gateway routes through
   cloud-config       serves service-configs (+ Vault secrets) to the services
   Mailpit            catches the emails Keycloak sends
```

| Service | Role |
| --- | --- |
| `api-gateway` | Single entry point. Checks the access token, routes by path, relays the token. |
| `user-service` | Identity: who someone is. Users, profiles, account actions, credential administration. |
| `auth-service` | Access: how people and services authenticate, and what they may do. Login, sessions, roles, groups, permissions, decisions, service clients, audit, token settings and keys. |
| `eureka-discovery` | Service registry. |
| `cloud-config-service` | Config Server over `service-configs` and Vault. |
| Keycloak | Identity provider, behind the two services. |
| Postgres | One instance, three databases with their own users: `keycloak`, `user_db`, `auth_db`. |
| Vault | Secrets (dev mode, in memory). |
| Mailpit | Local mail inbox. |

## Repositories

All of them must be checked out next to each other, because the services read the version catalog and the shared security starter from this repository by relative path.

```
practice/
├── micro-services/         this repository
├── service-configs/        configuration files served by the Config Server
├── eureka-discovery/
├── cloud-config-service/
├── user-service/
├── auth-service/
└── api-gateway/
```

## What is in this repository

| Path | What it is |
| --- | --- |
| `scripts/start.sh`, `stop.sh`, `restart.sh`, `status.sh` | Start and stop the platform in order ([details](#start-and-stop-scripts)) |
| `scripts/run-from-source.sh` | Run one service from its source tree against the running stack |
| `scripts/init-env.sh` | Generates `.env` with random secrets for an environment |
| `scripts/vault-bootstrap.sh`, `keycloak-bootstrap.sh` | Run inside the `bootstrap` container: seed Vault, apply secrets to Keycloak |
| `scripts/k8s-up.sh` | Build, deploy and wait on Kubernetes |
| `scripts/lib.sh` | Shared by the scripts above; holds the start order |
| `Makefile` | Short names for the scripts |
| `docker-compose.yml` | The stack |
| `.env.dev.example`, `.env.qa.example`, `.env.prod.example` | Non-secret settings per environment |
| `keycloak/platform-realm.json` | Bootstrap realm: clients, seed roles and permissions, token mappers. No secrets. |
| `postgres/init/` | Creates the three databases and their users on first start |
| `bootstrap/Dockerfile` | Image of the one-shot bootstrap job |
| `gradle/libs.versions.toml` | The one version catalog every service imports |
| `platform-security-starter/` | Shared library: JWT validation, claim value objects, authority mapping |
| `e2e/` | End-to-end test suite (REST Assured) |
| `k8s/base`, `k8s/overlays/{dev,qa,prod}` | Kustomize manifests |
| `docs/` | ADRs, the token contract, the integration guide, the build record |

## Prerequisites

- Docker with Compose v2
- JDK 27 (only for building and testing from source; the images build with their own JDK)
- `make`, `curl`
- For Kubernetes: `kubectl` and Docker Desktop's cluster

The stack uses these host ports: 5432, 8025, 8080, 8200, 9111, 9121, 9141, 9211, 9311. Change them in `.env.<environment>.example` if they are taken.

## Quick start

```bash
make up          # builds the images and starts everything in order
make status      # what is running, and whether the gateway can reach the services
make test-e2e    # optional: the end-to-end suite
make stop        # graceful stop; data is kept
```

The first `make up` generates `.env` with random secrets, builds five images and imports the realm. It takes a few minutes; later starts take about a minute.

## Start and stop scripts

Everything lives in `scripts/`. `start.sh`, `stop.sh` and `restart.sh` print their usage with `--help`.

### `start.sh` — start in order

```bash
scripts/start.sh                    # start everything (dev, or whatever .env already is)
scripts/start.sh qa                 # with the qa configuration; also prod
scripts/start.sh --build            # rebuild the service images first
scripts/start.sh auth-service       # start only this service and wait for it
```

It starts one stage at a time and waits until that stage reports healthy before the next one begins:

| Stage | What starts | Why here |
| --- | --- | --- |
| 1 | Postgres, Vault, Mailpit | Nothing depends on anything else yet |
| 2 | Keycloak | Needs its database; imports the realm on first start |
| 3 | bootstrap (one-shot) | Seeds Vault and applies client secrets to Keycloak; needs both |
| 4 | Eureka, Config Server | Services need the registry and their configuration |
| 5 | user-service, auth-service | Need configuration, secrets, their database and Keycloak |
| 6 | api-gateway | Routes to the services |
| 7 | (wait) | Until the gateway has fetched the registry and can reach both services |

It is safe to run again: running containers are left alone, and the bootstrap keeps secrets that already exist. If a stage does not become healthy the script stops there and names the service to look at.

### `stop.sh` — stop gracefully

```bash
scripts/stop.sh                     # stop everything, remove the containers, keep data and .env
scripts/stop.sh --keep              # stop but keep the containers
scripts/stop.sh auth-service        # stop only this service
scripts/stop.sh --reset             # also delete the data volumes and .env (asks first)
```

It goes through the start order backwards, so nothing loses a dependency while it is still working:

| Step | What stops | Effect |
| --- | --- | --- |
| 1 | api-gateway | No new requests come in |
| 2 | user-service, auth-service | Finish requests in flight, deregister from Eureka, close database connections |
| 3 | Config Server, Eureka | |
| 4 | Keycloak | |
| 5 | Mailpit, Vault, Postgres | The database goes last, after everything that writes to it |

Each container gets `STOP_TIMEOUT` seconds (default 40) to shut down before it is killed. The services use Spring's graceful shutdown with a 30-second limit for requests in flight.

Vault runs in memory in this setup, so it is empty after a stop. `start.sh` seeds it again every time; the services then pick up the new client secrets because they start after the bootstrap.

### `restart.sh` — graceful restart

```bash
scripts/restart.sh                        # stop everything in order, start it again in order
scripts/restart.sh auth-service           # only this service
scripts/restart.sh --build user-service   # rebuild its image, then restart it
```

Restarting `vault` on its own is refused, because that would empty it underneath running services.

### `status.sh` — what is running

Lists every service with its health, says whether the gateway can route to both services, and prints the addresses.

### `run-from-source.sh` — one service from source

```bash
scripts/run-from-source.sh auth-service
```

Stops that service's container and runs `./gradlew bootRun` in its repository with the settings and Vault token of the running stack. Stop it with Ctrl-C, then `scripts/start.sh auth-service` puts the container back. Works for `user-service`, `auth-service` and `api-gateway`.

## Make targets

| Target | Does |
| --- | --- |
| `make up` | `start.sh --build` for `ENV` (default `dev`) |
| `make start` | `start.sh` without rebuilding. `make start S="auth-service"` for some only. |
| `make stop`, `make down` | `stop.sh`. `make stop S="auth-service"` for some only. |
| `make restart` | `restart.sh`. `S="..."` for some only, `BUILD=1` to rebuild. |
| `make status` | `status.sh` |
| `make reset` | Stop and delete data volumes and `.env`, without asking |
| `make logs` | Follow logs. `S="..."` for some only. |
| `make ps` | Container status |
| `make test-e2e` | End-to-end suite against the running stack |
| `make k8s-up`, `make k8s-down`, `make test-e2e-k8s` | The same on Kubernetes |

## Addresses

| What | URL |
| --- | --- |
| Gateway (use this) | http://localhost:9211 |
| Swagger UI for both APIs | http://localhost:9211/swagger-ui.html |
| Mailpit | http://localhost:8025 |
| Keycloak console (debugging only) | http://localhost:8080 |
| Vault | http://localhost:8200 |
| Eureka | http://localhost:9111 |
| Config Server | http://localhost:9311 |
| user-service, auth-service directly | http://localhost:9121, http://localhost:9141 |
| Postgres | localhost:5432 |

## First calls

The bootstrap administrator is `platform-admin`. Its password is `PLATFORM_ADMIN_PASSWORD` in the generated `.env`.

```bash
. ./.env
TOKEN=$(curl -s -X POST localhost:9211/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"platform-admin\",\"password\":\"$PLATFORM_ADMIN_PASSWORD\"}" | jq -r .accessToken)

curl -s localhost:9211/api/v1/auth/me -H "Authorization: Bearer $TOKEN"        # who am I
curl -s "localhost:9211/api/v1/users?size=5" -H "Authorization: Bearer $TOKEN"  # list users
curl -s localhost:9211/api/v1/roles -H "Authorization: Bearer $TOKEN"           # list roles

# Register a new user; the verification email lands in Mailpit.
curl -s -X POST localhost:9211/api/v1/users/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","firstName":"Alice","lastName":"Doe","password":"S3cret!Passw0rd"}'
```

The full API of each service is in its README and in Swagger UI.

## Environments

Configuration is split into `dev`, `qa` and `prod` ([ADR 0014](docs/adr/0014-environment-profiles.md)). `ENV` picks the `.env.<environment>.example` file, which sets the Spring profile of every service.

```bash
make up ENV=qa
```

- Switching environment needs `make reset` first, because the generated secrets belong to the data volumes.
- Locally, `qa` and `prod` run on the same infrastructure as `dev` (dev-mode Vault, Mailpit, one Keycloak). They select configuration; they do not harden anything.
- What differs: in `qa` and `prod` every address must be supplied (no defaults), the signing-key cache is 5 minutes instead of 15 seconds, and in `prod` the API docs are off.

## Secrets

Nothing secret is in Git.

1. `scripts/init-env.sh` writes `.env` (git-ignored) with random database passwords, the Keycloak console password, the `platform-admin` password and one Vault token per service.
2. Postgres creates the three databases with those passwords on first start.
3. The `bootstrap` job writes them to Vault, generates the three platform client secrets, applies those to Keycloak, and creates a read-only Vault policy and token per service.
4. Services read their secrets from Vault with their own token: `secret/user-service`, `secret/auth-service`, `secret/api-gateway`. `auth-service` writes the secrets of registered service clients to `secret/clients/<id>`.

The one fixed value is the dev Vault root token in the `.env.<environment>.example` files, labelled as such and used only by the bootstrap. See [ADR 0008](docs/adr/0008-secrets-bootstrap.md).

## Tests

| What | How | Needs |
| --- | --- | --- |
| Security starter (35 tests) | `./gradlew build` here | JDK 27 |
| Each service | `./gradlew build` in its repository | JDK 27, Docker (Testcontainers) |
| End to end (21 tests) | `make test-e2e` | The stack running with `dev` |
| End to end on Kubernetes | `make test-e2e-k8s` | `make k8s-up` |

The end-to-end suite creates all its data through the APIs. It covers self-service, user and access administration, decisions, service onboarding and secret rotation, guards, audit, JWT validation at the gateway and at each service, the token contract, key rotation, lifetimes, token exchange and issuer consistency.

## Kubernetes

Targets Docker Desktop's cluster.

```bash
make k8s-up         # build, deploy the dev overlay, wait for rollout, run the bootstrap job
make test-e2e-k8s
make k8s-down
```

| Overlay | Namespace | Reached at |
| --- | --- | --- |
| `k8s/overlays/dev` | `identity-dev` | Gateway http://localhost:30211, Keycloak http://localhost:30080, Mailpit http://localhost:30025 |
| `k8s/overlays/qa` | `identity-qa` | Ingress with placeholder hosts |
| `k8s/overlays/prod` | `identity-prod` | Ingress with placeholder hosts |

Every workload has one replica. No Secret is committed: `scripts/k8s-up.sh` creates `platform-bootstrap` from the local `.env`. The `qa` and `prod` overlays render and validate but have not been deployed; set their host names and Git remote before using them. See [ADR 0013](docs/adr/0013-local-kubernetes.md).

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `start.sh` stops with "Not healthy: ..." | Read `docker compose logs <service>`. A service that cannot find its database URL or secrets usually means an earlier stage did not complete. |
| "The existing .env is for 'dev', not 'qa'" | An environment switch needs `make reset`. |
| Gateway answers 503 | It has not fetched the registry yet. `start.sh` waits for this; `make status` shows it. |
| A valid-looking token gets 401 | Check `iss`: it must equal `KEYCLOAK_PUBLIC_URL` + `/realms/platform` exactly. Check `aud`: it must contain the service's client ID. |
| Services fail after only Vault was restarted | Vault is in memory and lost its secrets. Run `scripts/restart.sh` (everything). |
| A port is already in use | Change it in `.env.<environment>.example`, then `make reset && make up`. |
| Image build cannot download Gradle | Expected on some networks; the Dockerfiles take Gradle from the official `gradle` image instead of the wrapper. |

## Documentation

- [Access token contract](docs/jwt-contract.md)
- [Integrating a new service](docs/integrating-a-new-service.md)
- [Architecture decisions](docs/adr/) (0001 to 0014)
- [Build record, verification results and open issues](docs/PROGRESS.md)
- [Discovery report](docs/discovery-report.md) (the state before the rebuild)
