# Microservices Platform

This repository orchestrates the local Keycloak-backed user-management and catalog platform. It owns Compose deployment, image build coordination, PostgreSQL initialization, and Vault startup wiring. Application code lives in independent sibling Git repositories.

The complete platform is still being migrated. Port/configuration validation has passed, but a clean end-to-end container startup is not yet verified; known blockers are listed below.

## Services And Responsibilities

| Repository / service | Responsibility | HTTP port |
| --- | --- | --- |
| [eureka-discovery](../eureka-discovery/README.md) | Registry; application name eureka-discovery-service | 9111 |
| [user-service](../user-service/README.md) | User/profile data and Keycloak lifecycle coordination | 9121 |
| [auth-service](../auth-service/README.md) | Roles/permissions catalog; future policy evaluation | 9141 |
| [books-service](../books-service/README.md) | Book/author metadata and ownership | 9151 |
| [video-service](../video-service/README.md) | Video metadata/completion and ownership | 9161 |
| [reviews-service](../reviews-service/README.md) | Book/video reviews and ratings | 9171 |
| [api-gateway](../api-gateway/README.md) | Reactive secured HTTP routing | 9211 |
| [cloud-config-service](../cloud-config-service/README.md) | Central configuration server | 9311 |
| [service-configs](../service-configs/README.md) | Shared YAML; not an executable service | None |

## Architecture

Clients authenticate with Keycloak and send access tokens through the gateway. Downstream services validate those tokens independently and enforce their own domain rules. User Service calls Keycloak Admin APIs for identity changes; Auth Service stores fine-grained authorization metadata separately.

Each business context owns its PostgreSQL database. Flyway uses a context-specific migration role, while runtime access uses the configured shared runtime role. Vault supplies secrets, Config Server supplies non-secret application/profile configuration, and Eureka supplies instance discovery when enabled.

Domain-driven design and clean architecture are the target: pure domain rules, application orchestration/ports, and infrastructure adapters. Books, video, and the new authorization catalog have migrated core boundaries; User Service still needs further refactoring; Reviews Service uses pure-domain ports/adapters. Infrastructure services should remain focused infrastructure components.

## Workspace And Prerequisites

All repositories must be siblings because Bake and Compose use relative paths:

```text
practice/
  micro-services/
  service-configs/
  eureka-discovery/
  cloud-config-service/
  api-gateway/
  user-service/
  auth-service/
  books-service/
  video-service/
  reviews-service/
```

Requirements: Docker with Compose and Buildx, Java 27 for migrated application toolchains, a supported Gradle launcher JVM (Java 21 is used in the existing verification workflow), and Git. Each Java service has its own `gradlew` and `gradle/` directory. Do not introduce a shared wrapper.

Auth Service's GitHub repository is private, so fresh cloning requires authorized access. Existing clone helper scripts are outdated/incomplete and do not establish this sibling layout reliably; clone the listed repositories into the workspace parent instead.

## Current Build Baselines

User, auth, gateway, books, and video use Java 27 / Boot 4.1.1. Reviews uses Java 27 / Boot 4.1.1 with Gradle 9.8.0; Config Server and Eureka still build with Java 21 / Boot 3.4.x. Spring Cloud versions differ until those migrations are completed. Docker runtime updates alone do not change build baselines.

The agreed target is Java 27, compatible current Spring Boot/modules, Groovy Gradle DSL, independent wrappers, MapStruct where mappings are needed, and supported modern Java/fluent APIs where appropriate.

## Ports And Deployment Modes

| Shared infrastructure | Published port |
| --- | --- |
| PostgreSQL | 5432 |
| Keycloak | 8080 |
| Vault | 8200 |

Service-local Compose dependency ports are isolated:

| Local stack | PostgreSQL host port | Keycloak host port |
| --- | --- | --- |
| User | 15432 | 18080 |
| Books | 25432 | 28080 |
| Video | 35432 | 38080 |

Internal PostgreSQL/Keycloak ports remain 5432/8080. Reviews' local PostgreSQL publishes 45432; its API remains 9171. Service APIs retain their canonical HTTP ports in either deployment mode.

Do not start the same API in shared and standalone modes simultaneously on the same port/container name. Port environment overrides can reintroduce collisions; re-render Compose after changing them.

## Database And Vault Provisioning

`postgres-init/init.sql` provisions the original databases and roles, including preserved `tododb`. `postgres-init/02-reviews.sql` adds `reviewsdb` and `reviewsadmin` with runtime grants; it can also be explicitly applied to existing volumes.

Use the same role/password contract on every PostgreSQL instance, matching configured Vault values. Runtime role: `theuser`. Business migration roles: `useradmin`, `authadmin`, `bookadmin`, `videoadmin`, `reviewsadmin`. Vault database administration uses `vaultadmin`. These role names are not instructions to use an admin account for ordinary runtime queries.

Initialization runs only for a fresh PostgreSQL volume. Never delete an existing volume just to re-run provisioning without a data-preservation/migration plan.

Compose mounts `vault/config/init-vault-secrets.sh`. That file is deliberately ignored and is not available from a fresh clone. Recreate it securely with the database and Keycloak secret keys expected by [service-configs](../service-configs/README.md), and preserve a secure copy outside Git. Vault's entrypoint calls it after starting the development server.

Keycloak needs the `company-platform` realm, appropriate clients/roles, and the User Service administrative service account. Replace the administrative client-secret placeholder before identity operations can work. Development-mode Vault and development credentials are not production deployment settings.

## Build And Validate

From this repository:

```bash
docker compose config --quiet
docker buildx bake -f docker-bake.hcl --print
```

The first command validates Compose structure; the second displays build targets without building. Neither proves a healthy runtime.

`build.sh` enters each sibling service and uses its own wrapper, then builds all eight images through Bake:

```bash
bash ./build.sh
```

Do not run it with `sh`: it uses Bash features. Pre-build JARs are required by the Dockerfiles. To build images only after preparing those JARs:

```bash
docker buildx bake -f docker-bake.hcl
```

Build order is Eureka, gateway, Config Server, user, auth, books, video, and reviews. Older Gradle wrappers may require a Java 21 launcher; Reviews Service's Gradle 9.8.0 wrapper runs directly on Java 27.

## Start And Inspect

Resolve the prerequisites/blockers below before expecting a healthy full deployment:

```bash
docker compose up -d
docker compose ps
docker compose logs -f api-gateway
docker compose logs -f cloud-config-service
```

```bash
curl http://localhost:9111/
curl http://localhost:9311/config/actuator/health
curl http://localhost:9211/api/users/ping
```

Protected business API checks require a genuine Keycloak token. A successful ping alone does not exercise ownership or administrative operations.

```bash
docker compose stop
```

Stopping preserves database volumes. Avoid destructive volume removal when resuming work.

## Known Full-Stack Blockers

- Config Server's mandatory legacy API-key Vault import and `/config` context conflict with newer secret/layout and some current client/health-check URLs.
- User Service lacks the configuration/discovery clients needed for its shared YAML settings and retains divergent datasource defaults.
- Gateway discovery defaults off; its Auth Service predicates do not match `/api/v1/...`. Books/video routes are absent; reviews routes are implemented.
- Keycloak external and internal issuer hostnames need a single consistent issuer contract matching token `iss`.
- Auth/User springdoc 2.8.5 compatibility with Boot 4 is unresolved.
- Docker layered-JAR extraction, image health checks, and the complete container startup path require post-upgrade verification.
- Fine-grained permission decisions/overrides and downstream enforcement are not implemented yet.

## Verification And Resume

The checkpoint records successful builds/tests for migrated services, including books (14 unit/MVC + 2 PostgreSQL integration tests), video (6 + 1), and auth (5 unit tests). PostgreSQL integration tests require Docker.

The port audit parsed application/shared YAML and rendered all six Compose files, confirming canonical API ports and unique service-local dependency ports. It does not certify the unresolved runtime behavior above.

[Implementation checkpoint](IAM_IMPLEMENTATION_CHECKPOINT.md) records completed work, known gaps, Git status, and the next implementation step. Update it after each meaningful implementation/verification milestone so work can resume reliably.

## Reviews Integration

Reviews Service uses PostgreSQL reviewsdb, runtime role theuser, and migration role
reviewsadmin with the requested development password from Vault/config. The API is
/api/v1/reviews on 9171; repeated book/video reviews are supported. Local PostgreSQL
publishes 45432. Gateway REVIEWS_SERVICE_URI defaults to http://localhost:9171 and
shared Compose sets http://reviews-service:9171. Use postgres-init/02-reviews.sql
from micro-services for additive provisioning on existing volumes. The ignored Vault
seed now includes secret/data/db/reviewsdb for dev/qa/prod. Task APIs are retired.

## Repository Contents

| Path | Purpose |
| --- | --- |
| `compose.yml` | Shared local platform: Vault, PostgreSQL, Keycloak, Eureka, Config Server, gateway, and business services. |
| `docker-bake.hcl` | Multi-image Buildx targets for all sibling service Dockerfiles. |
| `build.sh` / `build.bat` | Convenience build scripts that invoke sibling Gradle wrappers and Bake. |
| `clone_repos.sh` / `clone_repos.bat` | Historical clone helpers; verify repository names/access before relying on them. |
| `postgres-init/init.sql` | Initial shared PostgreSQL databases, roles, grants, and default privileges. |
| `postgres-init/02-reviews.sql` | Additive reviews database/role/grant provisioning for existing platform volumes. |
| `vault/config/vault-entrypoint.sh` | Development Vault bootstrap wrapper. |
| `vault/config/init-vault-secrets.sh` | Ignored local secret seed, required for a fresh dev Vault. |
| `IAM_IMPLEMENTATION_CHECKPOINT.md` | Running checkpoint of migration status, smoke results, and blockers. |

## Shared Runtime Topology

```text
client
  -> api-gateway:9211
       -> user-service:9121
       -> auth-service:9141
       -> books-service:9151
       -> video-service:9161
       -> reviews-service:9171

business services
  -> keycloak:8080 for JWT issuer/JWKS and identity admin calls
  -> postgres:5432 for service-owned databases
  -> vault:8200 for secrets when enabled
  -> cloud-config-service:9311/config for shared YAML when enabled
  -> eureka-discovery-service:9111/eureka for discovery when enabled
```

Canonical service databases:

| Database | Owner service | Runtime role | Migration role |
| --- | --- | --- | --- |
| `userdb` | User Service | `theuser` | `useradmin` |
| `authdb` | Auth Service | `theuser` | `authadmin` |
| `booksdb` | Books Service | `theuser` | `bookadmin` |
| `videodb` | Video Service | `theuser` | `videoadmin` |
| `reviewsdb` | Reviews Service | `theuser` | `reviewsadmin` |

## Command Cookbook

Validate definitions without starting anything:

```bash
docker compose config --quiet
docker buildx bake -f docker-bake.hcl --print
```

Build all sibling JARs and images:

```bash
bash ./build.sh
```

Build a single image after its service JAR exists:

```bash
docker buildx bake -f docker-bake.hcl api-gateway
docker buildx bake -f docker-bake.hcl reviews-service
```

Start the shared platform:

```bash
docker compose up -d
docker compose ps
```

Inspect important logs:

```bash
docker compose logs -f vault
docker compose logs -f cloud-config-service
docker compose logs -f api-gateway
docker compose logs -f reviews-service
```

Basic unauthenticated checks:

```bash
curl http://localhost:9111/
curl http://localhost:9311/config/actuator/health
curl http://localhost:9211/actuator/health
curl http://localhost:9211/api/users/ping
```

Apply reviews provisioning to an existing PostgreSQL volume:

```bash
docker compose exec -T postgres psql -U root -d postgres -v ON_ERROR_STOP=1 < postgres-init/02-reviews.sql
```

Stop without deleting persistent data:

```bash
docker compose stop
```

## Operating Rules

- Keep all service repositories as siblings of `micro-services`; relative Docker build contexts depend on that shape.
- Do not run service-local Compose stacks and the shared platform for the same service on the same ports at the same time.
- Do not delete database volumes to “fix” provisioning without a data migration decision.
- A successful `docker compose config` or Bake print validates structure only; it does not prove Keycloak issuer alignment, Vault seed completeness, Flyway credentials, or gateway route correctness.
- Update this README, [service-configs](../service-configs/README.md), and the affected service README together when changing ports, database names, Vault paths, or gateway routes.
