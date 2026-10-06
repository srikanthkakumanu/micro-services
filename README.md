# micro-services

The platform root of the Keycloak-backed identity platform. This repository does not contain a service of its own. It holds what the services share and everything needed to run them together: the start and stop scripts, the Docker Compose stack, the Keycloak realm, the secret bootstrap, the onboarding of business services, the shared build files, the end-to-end tests, the Kubernetes manifests and the documentation.

`user-service` and `auth-service` are the front door for every routine identity and access operation. Keycloak sits behind them, is configured once from code, and is never called directly by people or by other services. `books-service` and `video-service` are business services on the platform: their users, logins and roles all come from those two services.

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
- [Dev users and passwords](#dev-users-and-passwords)
- [Environments](#environments)
- [Secrets](#secrets)
- [Business services](#business-services)
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
                        /       |       \
        +--------------+ +--------------+ +---------------+    each validates the
        | user-service | | auth-service | | books-service |    token again
        +--------------+ +--------------+ +---------------+
           |      \        /     |    ^        |    |
        user_db    \      /   auth_db  \-------+  booksdb
                 +----------+           asks about users,
                 | Keycloak |           gets its service token
                 +----------+
              the only issuer of tokens

   Vault              every credential: database users and passwords, client secrets
   eureka-discovery   registry the gateway routes through
   cloud-config       serves service-configs (+ Vault secrets) to the services
   Mailpit            catches the emails Keycloak sends
```

| Service | Role |
| --- | --- |
| `api-gateway` | Single entry point. Checks the access token, routes by path, relays the token. |
| `user-service` | Identity: who someone is. Users, profiles, account actions, credential administration. |
| `auth-service` | Access: how people and services authenticate, and what they may do. Login, sessions, roles, groups, permissions, decisions, service clients, audit, token settings and keys. |
| `books-service` | The book catalog. Requires a platform login and a catalog role; keeps no users or roles of its own. |
| `video-service` | Videos, their completion state and owners. Requires a platform login and a video role; built the same way. |
| `eureka-discovery` | Service registry. |
| `cloud-config-service` | Config Server over `service-configs` and Vault. |
| Keycloak | Identity provider, behind the two services. |
| Postgres | One instance, a database per owner: `keycloak`, `user_db`, `auth_db`, `booksdb`, `videodb`. |
| Vault | Every credential, including all database users and passwords (dev mode, in memory). |
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
├── api-gateway/
├── books-service/
└── video-service/
```

## What is in this repository

| Path | What it is |
| --- | --- |
| `scripts/start.sh`, `stop.sh`, `restart.sh`, `status.sh` | Start and stop the platform in order ([details](#start-and-stop-scripts)) |
| `scripts/run-from-source.sh` | Run one service from its source tree against the running stack |
| `scripts/init-env.sh` | Generates `.env` for an environment, and adds what is new to an existing one |
| `scripts/vault-bootstrap.sh` | Job `vault-seed`: puts every credential into Vault. The only reader of `.env` secrets. |
| `scripts/fetch-runtime-secrets.sh` | Job `secrets-fetch`: hands Postgres and Keycloak their database credentials from Vault |
| `scripts/db-init.sh` | Job `db-init`: creates the databases and users with credentials read from Vault |
| `scripts/keycloak-bootstrap.sh` | Job `keycloak-bootstrap`: applies client secrets and the administrator's password to the realm |
| `scripts/onboard-services.sh`, `onboarding/*.json` | Job `onboard`: registers business services through the platform APIs |
| `scripts/k8s-up.sh` | Build, deploy and wait on Kubernetes |
| `scripts/lib.sh` | Shared by the scripts above; holds the start order |
| `Makefile` | Short names for the scripts |
| `docker-compose.yml` | The stack |
| `.env.dev.example`, `.env.qa.example`, `.env.prod.example` | Settings per environment. The dev one also holds the fixed dev-only passwords. |
| `keycloak/platform-realm.json` | Bootstrap realm: clients, seed roles and permissions, token mappers. No secrets. |
| `bootstrap/Dockerfile` | Image of the one-shot jobs |
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
- About 6 GB of memory for Docker (the Compose stack uses a little over 5 GB). With 8 GB, run either the Compose stack or the local cluster, not both.

The stack uses these host ports: 5432, 8025, 8080, 8200, 9111, 9121, 9141, 9151, 9161, 9211, 9311. Change them in `.env.<environment>.example` if they are taken.

## Quick start

```bash
make up          # builds the images and starts everything in order
make status      # what is running, and whether the gateway can reach the services
make test-e2e    # optional: the end-to-end suite
make stop        # graceful stop; data is kept
```

The first `make up` generates `.env`, builds eight images and imports the realm. It takes a few minutes; later starts take about a minute.

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
| 1 | Vault, then job `vault-seed` | Everything else gets its credentials from Vault, the database included |
| 2 | Job `secrets-fetch`, then Postgres and Mailpit | Postgres takes its superuser name and password from Vault |
| 3 | Job `db-init` | Creates every database and user with credentials read from Vault |
| 4 | Keycloak, then job `keycloak-bootstrap` | Keycloak takes its database credentials from Vault and imports the realm on first start; the job applies client secrets and the administrator's password |
| 5 | Eureka, Config Server | Services need the registry and their configuration |
| 6 | user-service, auth-service | Need configuration, secrets, their database and Keycloak |
| 7 | api-gateway, then wait | Until the gateway has fetched the registry and can reach both services |
| 8 | Job `onboard` | Registers the business services with the platform through its APIs: clients, permissions, roles |
| 9 | books-service, video-service, then wait | They must exist on the platform before they start; then until the gateway can reach them |

It is safe to run again: running containers are left alone, the jobs keep what already exists, and `db-init` creates databases that were added since (no reset needed). If a stage does not become healthy the script stops there and names the service to look at.

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
| 2 | books-service, video-service | Finish requests in flight, deregister from Eureka, close database connections |
| 3 | user-service, auth-service | The same |
| 4 | Config Server, Eureka | |
| 5 | Keycloak | |
| 6 | Mailpit, Postgres | The database goes after everything that writes to it |
| 7 | Vault | Last, as it was first |

Each container gets `STOP_TIMEOUT` seconds (default 40) to shut down before it is killed. The services use Spring's graceful shutdown with a 30-second limit for requests in flight.

Vault runs in memory in this setup, so it is empty after a stop. `start.sh` seeds it again every time; the services then pick up the new client secrets because they start after the jobs.

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

Stops that service's container and runs `./gradlew bootRun` in its repository with the settings and Vault token of the running stack. Stop it with Ctrl-C, then `scripts/start.sh auth-service` puts the container back. Works for `user-service`, `auth-service`, `books-service`, `video-service` and `api-gateway`.

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
| Swagger UI for all four APIs | http://localhost:9211/swagger-ui.html |
| Mailpit | http://localhost:8025 |
| Keycloak console (debugging only) | http://localhost:8080 |
| Vault | http://localhost:8200 |
| Eureka | http://localhost:9111 |
| Config Server | http://localhost:9311 |
| user-service, auth-service directly | http://localhost:9121, http://localhost:9141 |
| books-service, video-service directly | http://localhost:9151, http://localhost:9161 |
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
curl -s "localhost:9211/api/v1/books?size=5" -H "Authorization: Bearer $TOKEN"  # the book catalog
curl -s "localhost:9211/api/v1/videos?size=5" -H "Authorization: Bearer $TOKEN" # the videos

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

## Dev users and passwords

For the development environment (`ENV=dev`) only. The fixed ones were chosen by the owner so they are easy to remember; the rest are generated per machine into the git-ignored `.env`, so only the variable that holds them can be named here. All of them are also in Vault, which is where everything reads them from.

| Account | User | Password | In Vault at |
| --- | --- | --- | --- |
| Vault dev root token | | `srikanth` | |
| `booksdb` schema admin | `booksadmin` | `booksadmin` | `secret/books-service` |
| `booksdb` and `videodb` runtime (used by books-service and video-service) | `theuser` | `theuser` | `secret/books-service`, `secret/video-service` |
| `videodb` schema admin | `videoadmin` | `videoadmin` | `secret/video-service` |
| Postgres superuser | `postgres` | `.env`: `POSTGRES_PASSWORD` | `secret/postgres` |
| `keycloak` database | `keycloak` | `.env`: `KEYCLOAK_DB_PASSWORD` | `secret/keycloak-db` |
| `user_db` | `user_service` | `.env`: `USER_DB_PASSWORD` | `secret/user-service` |
| `auth_db` | `auth_service` | `.env`: `AUTH_DB_PASSWORD` | `secret/auth-service` |
| Platform administrator (sign in to the APIs) | `platform-admin` | `.env`: `PLATFORM_ADMIN_PASSWORD` | `secret/keycloak` |
| Keycloak console (debugging only) | `admin` | `.env`: `KEYCLOAK_ADMIN_PASSWORD` | `secret/keycloak` |
| Vault token of each service | | `.env`: `<SERVICE>_VAULT_TOKEN` | |

```bash
grep PLATFORM_ADMIN_PASSWORD .env                                   # a generated password
docker compose exec -e VAULT_TOKEN=srikanth vault vault kv get secret/books-service
docker compose exec -e PGPASSWORD=theuser postgres psql -h localhost -U theuser -d booksdb -c '\dt'
```

Application users (people who sign in) are not in this table: they are created through `user-service`, by registering or by an administrator. In `qa` and `prod` nothing is fixed: the `booksdb` and `videodb` passwords are generated too.

## Secrets

**Every database user name and password is stored in Vault and read from there**, by the services, by Keycloak and by Postgres itself ([ADR 0015](docs/adr/0015-books-service-integration.md)). The same goes for client secrets.

1. `scripts/init-env.sh` writes `.env` (git-ignored): the settings of the environment plus generated passwords and one Vault token per reader.
2. Vault starts first. The `vault-seed` job copies the credentials from `.env` into Vault, generates the platform client secrets, and creates a read-only policy and token per reader. **It is the only thing that reads credentials from `.env`.**
3. The `secrets-fetch` job reads the Postgres superuser's and Keycloak's database credentials from Vault and hands them to those two containers as files. They cannot talk to Vault themselves.
4. The `db-init` job reads every database credential from Vault and creates the databases and users. Run again, it sets each password to the one Vault holds.
5. Services read their secrets from Vault with their own token: `secret/user-service`, `secret/auth-service`, `secret/books-service`, `secret/video-service`, `secret/api-gateway`. `auth-service` writes the secrets of registered service clients to `secret/clients/<id>`.

| Vault path | Holds |
| --- | --- |
| `secret/postgres` | Postgres superuser name and password |
| `secret/keycloak-db` | Keycloak's database, user and password |
| `secret/user-service`, `secret/auth-service` | Datasource user and password, Keycloak client secret |
| `secret/books-service`, `secret/video-service` | Runtime account (`spring.datasource.*`) and schema admin (`spring.flyway.*`) |
| `secret/api-gateway` | Keycloak client secret |
| `secret/keycloak` | Console admin and platform administrator passwords |
| `secret/clients/<id>` | Client secret of each registered service |

Two honest limits of the dev setup:

- The dev Vault runs in memory and starts empty, so the values have to exist outside it once to seed it. That is `.env`. A persistent or external Vault removes this.
- The fixed dev passwords above and the dev Vault root token are committed in `.env.dev.example`, marked dev-only. That is a deliberate exception to "no secrets in Git", for the dev environment only. `qa` and `prod` templates carry no password.

See [ADR 0008](docs/adr/0008-secrets-bootstrap.md) and [ADR 0015](docs/adr/0015-books-service-integration.md).

## Business services

A business service joins the platform through its APIs, never by editing the realm file. Each has a file in `onboarding/` that declares its client, its permissions and the roles that grant them. The `onboard` job applies every file on each start by calling `/api/v1/clients`, `/api/v1/permissions` and `/api/v1/roles` as the platform administrator; what exists is kept.

| Service | Paths | Roles | Permissions |
| --- | --- | --- | --- |
| `books-service` | `/api/v1/books`, `/api/v1/authors` | `CATALOG_READER`, `CATALOG_EDITOR`, `CATALOG_MANAGER` | `books:read`, `books:write`, `books:manage`, `authors:manage` |
| `video-service` | `/api/v1/videos` | `VIDEO_READER`, `VIDEO_EDITOR`, `VIDEO_MANAGER` | `videos:read`, `videos:write`, `videos:manage` |

- A reader reads; an editor also adds entries and changes their own; a manager changes any entry and transfers ownership. `PLATFORM_ADMIN` holds every permission.
- The roles of one service give nothing in the other. A user with only `USER` gets `403` from both.
- A user gets access when a role is assigned to them in auth-service, and sees it in their next token:

```bash
curl -s -X POST localhost:9211/api/v1/users/$USER_ID/roles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"roles":[{"name":"VIDEO_READER"}]}'
```

To add another service, see [Integrating a new service](docs/integrating-a-new-service.md).

## Tests

| What | How | Needs |
| --- | --- | --- |
| Security starter (35 tests) | `./gradlew build` here | JDK 27 |
| Each service | `./gradlew build` in its repository | JDK 27, Docker (Testcontainers) |
| End to end (29 tests) | `make test-e2e` | The stack running with `dev` |
| End to end on Kubernetes | `make test-e2e-k8s` | `make k8s-up` |

The end-to-end suite creates all its data through the APIs. It covers self-service, user and access administration, decisions, service onboarding and secret rotation, guards, audit, JWT validation at the gateway and at each service, the token contract, key rotation, lifetimes, token exchange and issuer consistency, and the two business services: a login and one of the service's roles are required, a role of one service does not open the other, editors keep only their own entries, managers hand entries to users that user-service knows.

## Kubernetes

Targets Docker Desktop's cluster.

```bash
make k8s-up         # build, deploy the dev overlay, run the jobs, wait until the gateway can route
make test-e2e-k8s
make k8s-down
```

| Overlay | Namespace | Reached at |
| --- | --- | --- |
| `k8s/overlays/dev` | `identity-dev` | Gateway http://localhost:30211, Keycloak http://localhost:30080, Mailpit http://localhost:30025 |
| `k8s/overlays/qa` | `identity-qa` | Ingress with placeholder hosts |
| `k8s/overlays/prod` | `identity-prod` | Ingress with placeholder hosts |

Every workload has one replica. The order is the same as in Compose: the `vault-seed`, `db-init`, `keycloak-bootstrap` and `onboard` steps run as Jobs, and Postgres and Keycloak fetch their database credentials from Vault in an init container, into memory. No Secret is committed: `scripts/k8s-up.sh` creates `platform-bootstrap` from the local `.env`; it holds what seeds Vault and the Vault tokens of the workloads. The `qa` and `prod` overlays render and validate but have not been deployed; set their host names and Git remote before using them. See [ADR 0013](docs/adr/0013-local-kubernetes.md).

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `start.sh` stops with "Not healthy: ..." | Read `docker compose logs <service>`. A service that cannot find its database URL or secrets usually means an earlier stage did not complete. |
| "The existing .env is for 'dev', not 'qa'" | An environment switch needs `make reset`. |
| Gateway answers 503 | It has not fetched the registry yet. `start.sh` waits for this; `make status` shows it. |
| A valid-looking token gets 401 | Check `iss`: it must equal `KEYCLOAK_PUBLIC_URL` + `/realms/platform` exactly. Check `aud`: it must contain the service's client ID. |
| Pods or containers are killed and restart (`OOMKilled`) | Docker has too little memory for the Compose stack and the local cluster together. Run one: `make stop` or `make k8s-down`. |
| books-service or video-service answers 403 to a logged-in user | The user has none of that service's roles (`CATALOG_*` for books, `VIDEO_*` for videos). Assign one in auth-service and log in again. |
| Services fail after only Vault was restarted | Vault is in memory and lost its secrets. Run `scripts/restart.sh` (everything). |
| A port is already in use | Change it in `.env.<environment>.example`, then `make reset && make up`. |
| Image build cannot download Gradle | Expected on some networks; the Dockerfiles take Gradle from the official `gradle` image instead of the wrapper. |

## Documentation

- [Access token contract](docs/jwt-contract.md)
- [Integrating a new service](docs/integrating-a-new-service.md)
- [Architecture decisions](docs/adr/) (0001 to 0016)
- [Build record, verification results and open issues](docs/PROGRESS.md)
- [Discovery report](docs/discovery-report.md) (the state before the rebuild)
