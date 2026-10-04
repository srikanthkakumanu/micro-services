# IAM Implementation Checkpoint

Date: 2026-10-04

Use this file to resume the Keycloak IAM migration if the session/context resets.

## High-Level Goal

Implement the plan in `/Users/skakumanu/Downloads/Keycloak_SpringBoot_UserManagement_Implementation_Plan.md`.

Current standards from the plan:

- Java 27 target toolchain for every microservice.
- Spring Boot 4.1.1.
- Spring Cloud 2025.1.3 where applicable.
- Gradle Groovy DSL with each service's own `gradlew`.
- PostgreSQL instead of MariaDB/MySQL.
- Vault remains the secret source.
- Keycloak owns authentication.
- DDD + clean architecture / ports-and-adapters for business/IAM services.
- MapStruct latest stable where mapping is non-trivial.
- Use Java 21+ features and fluent APIs where feasible.

## Completed In This Session

### `user-service`

Purpose: user lifecycle/profile/account service.

Changes made:

- Migrated build toward Java 27 + Spring Boot 4.1.1.
- Updated Lombok to `1.18.48` because older Lombok failed on JDK 27 javac internals.
- Added OAuth2 resource server dependency.
- Removed local JWT/auth pieces:
  - `JWTGenerator`
  - `JWTAuthenticationFilter`
  - `JWTAuthEntryPoint`
  - `PasswordEncoderConfig`
- Added Keycloak resource-server security config.
- Added Keycloak realm role extraction from `realm_access.roles`.
- Added Keycloak admin integration skeleton under `user.integration.keycloak`.
- Removed stale custom API-key headers/logging from user, profile, and role controllers.
- Updated `requests.http` to use Keycloak bearer-token examples.
- Updated local `compose.yaml` from MariaDB/phpMyAdmin to PostgreSQL + Keycloak.
- Updated Docker runtime image baseline to `eclipse-temurin:27-jre-alpine`.
- Added lifecycle request DTOs:
  - `PasswordResetRequest`
  - `RoleAssignmentRequest`
- Refactored user model/config toward Keycloak-owned authentication:
  - local password removed from user domain/DTO path
  - `keycloakUserId` added
  - profile expanded with employee/department/manager/designation fields
- PostgreSQL migration updated from MariaDB-style `varbinary(16)` to PostgreSQL `uuid`.
- Local app config simplified for PostgreSQL + Keycloak.
- MapStruct `UserMapper` explicitly ignores `keycloakUserId` on create-request mapping.

Verified:

```bash
cd /Users/skakumanu/practice/user-service
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew test --stacktrace --no-daemon
```

Result: build successful after API-key cleanup.

Note: Gradle itself was launched with JDK 21 because Gradle 8.14.3 had issues running directly on Java 27, but the project toolchain targets Java 27.

### `api-gateway`

Purpose: public edge gateway and route entry point.

Changes made:

- Migrated build toward Java 27 + Spring Boot 4.1.1.
- Updated Spring Cloud to `2025.1.3`.
- Updated Lombok to `1.18.48`.
- Removed old custom HMAC/JJWT gateway auth filter.
- Added reactive Spring Security resource-server config.
- Added Keycloak realm role extraction.
- Replaced old route config with Gateway 5 WebFlux namespace:
  - `spring.cloud.gateway.server.webflux.*`
- Added routes for:
  - `user-service`
  - future `auth-service`
  - `books-service`
  - OpenAPI route groups

Verified:

```bash
cd /Users/skakumanu/practice/api-gateway
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew test --stacktrace --no-daemon
```

Result: build successful.

### `micro-services`

Purpose: runtime composition/deployment repository.

Changes made:

- `compose.yml`
  - replaced MariaDB service with PostgreSQL service
  - removed phpMyAdmin runtime dependency
  - added Keycloak service
  - updated dependencies from `mariadb` to `postgres`
  - added PostgreSQL URLs and Keycloak issuer env vars for services
  - added `video-service` and `todo-service` runtime entries
- Added PostgreSQL init script:
  - `postgres-init/init.sql`
- PostgreSQL users/passwords follow existing Vault/config convention:
  - `root/root`
  - `theuser/theuser`
  - `vaultadmin/vaultadmin`
  - `useradmin/useradmin`
  - `authadmin/authadmin`
  - `bookadmin/bookadmin`
  - `videoadmin/videoadmin`
  - `todoadmin/todoadmin`
- Databases created:
  - `keycloak`
  - `vaultdb`
  - `userdb`
  - `authdb`
  - `booksdb`
  - `videodb`
  - `tododb`
- Added missing Vault dev seed script:
  - `vault/config/init-vault-secrets.sh`
- Updated prod Vault DB script from MariaDB plugin/path to PostgreSQL plugin/path:
  - `init-vault-prod.sh`
- Updated build orchestration:
  - `build.sh` now uses actual `eureka-discovery` directory
  - `docker-bake.hcl` now uses actual `eureka-discovery` context and includes `video-service`/`todo-service`
- Updated README references away from MariaDB/API-key/JWT secrets.

Verified:

```bash
cd /Users/skakumanu/practice
ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f); puts "ok #{f}" }' service-configs/*.yaml micro-services/compose.yml
docker compose -f micro-services/compose.yml config
rg -n "mariadb|MariaDB|mysql|MySQL|phpmyadmin|/var/lib/mysql|jdbc:mariadb|mariadb-init|api-key|key-secret|JWT" micro-services service-configs
```

Results:

- YAML parse successful.
- Docker Compose config renders successfully.
- No stale MariaDB/API-key/JWT matches in `micro-services` or `service-configs`.

### `service-configs`

Purpose: externalized Spring config repo served by `cloud-config-service`.

Changes made:

- Replaced stale user/books configs with PostgreSQL + Keycloak configs.
- Preserved Vault keys:
  - `user`
  - `password`
  - `flw-user`
  - `flw-password`
  - `db-name`
- Replaced profile overlays with small dev/qa Vault import overlays.
- Fixed malformed `books-service-clean.yaml`.
- Added new configs for:
  - `auth-service`
  - `video-service`
  - `todo-service`

Verified:

```bash
cd /Users/skakumanu/practice
ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f); puts "ok #{f}" }' service-configs/*.yaml
```

Result: all YAML parsed successfully.

## Implementation File Inventory

The changes listed below were committed on 2026-10-04; they are no longer a dirty worktree.

### `user-service`

Modified/deleted/new files include:

- `build.gradle`
- `src/main/java/user/SecurityApplication.java`
- `src/main/java/user/config/UserSecurityConfig.java`
- `src/main/java/user/controller/RoleController.java`
- `src/main/java/user/controller/UserAuthController.java`
- `src/main/java/user/controller/UserController.java`
- `src/main/java/user/controller/UserProfileController.java`
- `src/main/java/user/domain/*`
- `src/main/java/user/dto/*`
- `src/main/java/user/mapper/UserMapper.java`
- `src/main/java/user/service/UserService.java`
- `src/main/java/user/service/UserServiceImpl.java`
- `src/main/java/user/integration/keycloak/*`
- `src/main/resources/application*.yaml`
- `src/main/resources/db/migration/V1__ddl_userdb_createTables.sql`
- `compose.yaml`
- `requests.http`
- `Dockerfile`
- deleted local JWT/password classes.

### `auth-service`

Purpose: authorization service for roles, permissions, and IAM policy metadata.

New independent service created at `/Users/skakumanu/practice/auth-service` with its own Gradle wrapper copied from `user-service`.

Implemented:

- Java 27 Gradle toolchain.
- Spring Boot 4.1.1 and Spring Cloud 2025.1.3.
- PostgreSQL + Flyway.
- OAuth2 resource-server security with Keycloak `realm_access.roles` mapping.
- DDD/hexagonal package structure:
  - `domain/model`
  - `domain/port/in`
  - `domain/port/out`
  - `application/usecase`
  - `infrastructure/persistence`
  - `infrastructure/web`
  - `infrastructure/security`
  - `infrastructure/exception`
- Domain model:
  - `Role`
  - `Permission`
  - `RoleId`
  - `PermissionId`
  - `RoleName`
  - `PermissionName`
  - `PermissionEffect`
- Use cases:
  - create/list roles
  - create/list permissions
  - assign permission to role
- JPA persistence adapters with MapStruct edge mappers.
- REST endpoints:
  - `GET /api/v1/roles`
  - `POST /api/v1/roles`
  - `POST /api/v1/roles/{roleId}/permissions/{permissionId}`
  - `GET /api/v1/permissions`
  - `POST /api/v1/permissions`
- Dockerfile using `eclipse-temurin:27-jre-alpine`.
- Unit tests for role and permission use cases.

Verified:

```bash
cd /Users/skakumanu/practice/auth-service
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew clean build --stacktrace --no-daemon
```

Result: build successful, 5 tests passed.

### `api-gateway`

Modified/deleted/new files include:

- `build.gradle`
- `src/main/java/api/gateway/config/SecurityConfig.java`
- deleted `src/main/java/api/gateway/config/filter/AuthorizationHeaderFilter.java`
- `src/main/resources/application*.yaml`

### `micro-services`

Modified/new files:

- `README.md`
- `build.sh`
- `compose.yml`
- `docker-bake.hcl`
- `init-vault-prod.sh`
- `postgres-init/init.sql`
- `vault/config/init-vault-secrets.sh`
- added `auth-service` runtime entry
- updated service build contexts for sibling repo layout
- mounted sibling `service-configs` repo for cloud config native mode

### `service-configs`

Modified/new files:

- `books-service*.yaml`
- `user-service*.yaml`
- `auth-service*.yaml`
- `video-service*.yaml`
- `todo-service*.yaml`

## Exact Resume Point

### books-service Verified Migration (2026-10-04)

Baseline edits are on disk:

- Java 27 toolchain, Boot 4.1.1, Cloud 2025.1.3, Lombok 1.18.48, MapStruct 1.6.3.
- PostgreSQL driver, PostgreSQL Flyway starter/support, UUID migration, PostgreSQL/Keycloak compose.
- OAuth2 resource server with Keycloak realm-role and scope extraction.
- springdoc upgraded to 3.1.1 (official springdoc documentation requires 3.x for Boot 4).
- Fixed FlywayMigrationStrategy import for Boot 4.
- Fixed book search query-parameter bindings and author pagination default size (20).
- Replaced destructive MariaDB setup script with PostgreSQL psql role/database provisioning.
- Removed redundant explicit component scanning that bypassed MVC slice filtering.
- Added JWT converter and controller/security slice tests; tests disable external Vault/Config imports.

Additional implemented changes:

- Plain-Java immutable Book and Author aggregates, validated change/query records, CatalogActor.
- Domain input/output ports; application services own transaction boundaries.
- Moved JPA entities/repositories, REST controllers/DTO facades/mappers, exceptions and config into infrastructure packages.
- MapStruct translates domain records to JPA entities and legacy response DTOs.
- Separate immutable BookRequest/AuthorRequest records for writes; response DTOs retain the existing response fields.
- Book writes require ownership or ADMIN/MANAGER; regular-user creation takes ownership from JWT sub and cannot transfer ownership.
- Author writes require ADMIN/MANAGER; catalog reads remain shared for authenticated users.
- Updates for missing ids return not found instead of creating replacement rows.
- UTC Instant persistence timestamps; legacy response date-times mapped in UTC.
- Sample seeding moved to explicit seed profile, uses Jackson 3, skips populated tables and fixes invalid random author index range.
- README refreshed with independent build/run/config/auth instructions.
- Separate integrationTest task for Docker/PostgreSQL tests; regular test task excludes integration tag.

Verified: 14 unit/MVC/security tests passed; 2 PostgreSQL 18 integration tests passed; bootJar packaging passed.
Integration tests exercise Flyway as bookadmin and persistence as theuser, UUID ownership,
UTC timestamp mapping, update round-trip, combined author filters and pagination.
YAML parsing, Docker Compose config and git diff --check passed.
No stale MariaDB/API-key references in active books-service source/config/docs.
No framework imports in the domain packages.

Still outstanding for books-service: fine-grained auth-service integration when evaluation APIs exist,
domain/integration events and production-wide observability/deployment work. Concurrent-write conflict
handling and book-author referential rules should be strengthened
before production acceptance. Existing Flyway V1 was rewritten for the PostgreSQL migration; this assumes
a fresh PostgreSQL database, not an in-place upgrade of an existing V1 schema.

Remaining broader gaps: Keycloak realm/client bootstrap, complete user-service clean architecture,
auth-service authorization evaluation and user overrides, gateway trusted-header stripping/rate limiting,
video/todo migration, cloud-config/Eureka baseline upgrades, events/auditing, portal, and deployment readiness.

### video-service Verified Migration (2026-10-04)

- Updated its own wrapper distribution to Gradle 8.14.3 and restored gradlew executable permission.
- Boot 4.1.1, Java 27 toolchain, Cloud 2025.1.3, MapStruct 1.6.3, Lombok 1.18.48, springdoc 3.1.1.
- PostgreSQL/Flyway, shared Vault/Config imports, local PostgreSQL/Keycloak compose.
- Plain-Java Video aggregate/change/query/page records, input/output ports, transactional application service.
- REST/JPA/security/mappers separated into infrastructure packages.
- JWT subject owns regular-user creates; owner or ADMIN/MANAGER may update/delete/complete; transfers require manager.
- Fixed null create path and duplicate userId JPA mapping; UTC Instant persistence; filters run in PostgreSQL.
- Completion moved from controller read/mutate/save to a single transactional use case.
- Replaced placeholder test with JWT MVC tests, added focused use-case tests and PostgreSQL integration test.
- Separate immutable VideoRequest write contract; response DTO remains compatible.
- README and standalone PostgreSQL init script updated.
- Compose config, git diff --check and stale-config scan passed.

Verified: 6 unit/MVC tests passed; 1 PostgreSQL 18 integration test passed; bootJar packaging passed.
Integration test validates the migration, runtime-user CRUD, UUID ownership, UTC timestamps,
completion update and case-insensitive filtering/pagination in PostgreSQL.
No domain framework imports; compose config and git diff --check passed.
Existing V1 was rewritten for a fresh PostgreSQL database.
Remaining: fine-grained auth-service integration, events/audit, concurrent-write conflict handling,
production observability/deployment, collective Keycloak/gateway smoke test.

Next planned task:

Continue service-by-service:

1. books-service test, integrationTest and bootJar all passed; its verified checkpoint is saved.
2. video-service test, integrationTest and bootJar passed; no work started on todo-service beyond read-only inspection.
3. Migrate todo-service next, preserving /api/todo. Unlike the shared catalogs, to-dos should enforce ownership on reads and writes.
4. Then align cloud-config-service and eureka-discovery; return to the outstanding IAM/gateway/realm gaps listed above.
5. Refresh this checkpoint after each green service slice.

## Useful Commands

Run verified service tests:

```bash
cd /Users/skakumanu/practice/user-service
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew test --stacktrace --no-daemon

cd /Users/skakumanu/practice/api-gateway
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew test --stacktrace --no-daemon

cd /Users/skakumanu/practice/auth-service
JAVA_HOME=/Users/skakumanu/Library/Java/JavaVirtualMachines/ms-21.0.9/Contents/Home GRADLE_USER_HOME=.gradle ./gradlew clean build --stacktrace --no-daemon
```

Validate config/platform files:

```bash
cd /Users/skakumanu/practice
ruby -e 'require "yaml"; ARGV.each { |f| YAML.load_file(f); puts "ok #{f}" }' service-configs/*.yaml micro-services/compose.yml
docker compose -f micro-services/compose.yml config
rg -n "api-key|apiKey|key-secret|JWT_SECRET|eclipse-temurin:21|mariadb|MariaDB|mysql|MySQL|phpmyadmin|/var/lib/mysql|jdbc:mariadb|mariadb-init" user-service api-gateway auth-service micro-services service-configs -g '!build/**'
```

## Notes For Future Sessions

- Do not revert the current dirty work unless explicitly requested.
- Keep using per-service wrappers.
- If Gradle fails when launched on Java 27, launch Gradle with JDK 21 and keep Java 27 as the compile toolchain.
- The implementation plan markdown is the source of desired architecture and order:
  - `/Users/skakumanu/Downloads/Keycloak_SpringBoot_UserManagement_Implementation_Plan.md`

## Git Commit Checkpoint (2026-10-04)

Each repository was committed independently using its own Git configuration. No commits were pushed.

| Repository | Implementation Commit |
| --- | --- |
| api-gateway | 4857119 |
| books-service | 1f7bc31 |
| cloud-config-service | 7b924cd |
| eureka-discovery | 478723c |
| service-configs | 1abee4a |
| user-service | 0f36158 |
| video-service | 211e66a |
| todo-service | 64b27ee |
| micro-services | efe28ed |
| auth-service | ebe4316 |

auth-service now has its own root .git directory and local user.name/user.email configuration,
matching the identity in the other services. Build/IDE artifacts are ignored; domain/port/out is tracked.
No remote has been configured for the new auth-service repository.

The Vault seed file micro-services/vault/config/init-vault-secrets.sh remains local and uncommitted
because micro-services/.gitignore explicitly excludes it. Preserve or securely recreate it when resuming
on another machine. The Downloads implementation plan and workspace-level checkpoint are outside Git;
a versioned copy of this checkpoint is saved in micro-services/IAM_IMPLEMENTATION_CHECKPOINT.md.

Next implementation task remains todo-service; no feature migration was started there.
