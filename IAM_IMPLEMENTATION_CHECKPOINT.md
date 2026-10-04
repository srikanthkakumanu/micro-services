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
2. video-service test, integrationTest and bootJar passed; todo-service remains otherwise unmigrated, with its API port now aligned to 9171.
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

Each repository was committed independently using its own Git configuration and pushed to origin/master.

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
auth-service origin is https://github.com/srikanthkakumanu/auth-service.git; the new GitHub repository is private.
Its master branch tracks origin/master. Existing service origin URLs were preserved; temporary HTTPS
credential-helper settings used GH_TOKEN for authenticated pushes without embedding the token in URLs.

The Vault seed file micro-services/vault/config/init-vault-secrets.sh remains local and uncommitted
because micro-services/.gitignore explicitly excludes it. Preserve or securely recreate it when resuming
on another machine. The Downloads implementation plan and workspace-level checkpoint are outside Git;
a versioned copy of this checkpoint is saved in micro-services/IAM_IMPLEMENTATION_CHECKPOINT.md.

Next implementation task remains todo-service; no feature migration was started there.

## GitHub Push Checkpoint (2026-10-04)

All ten service/config/platform repositories were pushed successfully to origin/master.
micro-services also includes commit a1ecc53, which records the implementation checkpoint.
The subsequent checkpoint update records the GitHub push status in the same repository.
The ignored local Vault seed script remains uncommitted and was not pushed.

## Port Audit (2026-10-04)

Fixed video-service local API port 9141 (collided with auth-service) to 9161.
Aligned todo-service local/default/Compose API port to 9171 instead of 9131.
Fixed api-gateway default Eureka URL to port 9111 instead of 8761.

Canonical service ports: Eureka 9111, user 9121, auth 9141, books 9151,
video 9161, todo 9171, gateway 9211, Cloud Config 9311.
Shared infrastructure: PostgreSQL 5432, Keycloak 8080, Vault 8200.
Standalone dependency host ports: user PostgreSQL 15432/Keycloak 18080,
books PostgreSQL 25432/Keycloak 28080, video PostgreSQL 35432/Keycloak 38080.
Internal container database/identity ports remain 5432 and 8080.
Standalone legacy todo dependencies remain MariaDB 3306/phpMyAdmin 40001 until migration.
README host-run URLs and API/debug Docker examples updated accordingly.

Validated all eight local application port defaults and matching shared configs through YAML parsing.
Rendered all six Compose files as JSON and checked API mappings, SERVER_PORT values,
within-stack uniqueness, and standalone dependency uniqueness against the shared platform.
No assigned ports appeared occupied in the current lsof listener check; git diff --check passed.
Start each API in one deployment mode at a time; running duplicate copies of the same service
requires separate host ports/container names. Arbitrary environment overrides can introduce conflicts.
Port-audit edits and the preceding .vscode ignore edits are not yet committed or pushed.

## README Refresh (2026-10-04)

Replaced the root README.md in all ten platform repositories: user-service,
auth-service, api-gateway, books-service, video-service, todo-service,
cloud-config-service, eureka-discovery, service-configs, and micro-services.
Documents now describe actual service responsibilities, relationships, current
build versions, architecture/security boundaries, APIs, ports, database ownership,
configuration, build/run commands, tests, and pending migration work.

Documentation audit clarified that api-gateway currently has only user/auth routes;
books/video/todo routes are absent, and auth predicates omit the implemented /api/v1
prefix. Treat earlier checkpoint statements suggesting completed books routes as
superseded by this source-verified audit. No route implementation changed here.
User Service still lacks Config Client/Vault/Eureka clients; Auth Service lacks
Config Client. To-do remains MariaDB-based. Config Server retains the legacy
mandatory Vault import and /config context-path mismatches. These runtime gaps
are now explicit in the corresponding READMEs.

Validated all ten READMEs: relative file links exist, fenced blocks are balanced,
Bash examples pass bash -n, and git diff --check passes in every repository.
No application builds, integration tests, or live platform startup were run for
this documentation-only update. Previously recorded test results are historical.
README refresh, port-audit changes, and .vscode ignore updates remain uncommitted
and unpushed. Preserve them when resuming; no feature migration started here.
Next implementation task remains todo-service, followed by infrastructure/runtime
alignment and complete end-to-end verification.

## Reviews Redesign Pause Checkpoint (2026-10-04 13:32 UTC)

User requested saving progress and stopping while away. Implementation is paused.
This section supersedes the earlier instruction to migrate todo-service as a task domain.

### Confirmed Design

- Replace todo-service with reviews-service locally and on the existing GitHub repository.
- PostgreSQL only; Java 27 retained despite Boot 4.1.1 documentation listing support only through Java 26.
- Target build: Boot 4.1.1, Cloud 2025.1.3, Gradle 9.8.0 independent Groovy wrapper,
  MapStruct 1.6.3, springdoc 3.1.1; Spring modules managed through BOMs.
- Multiple separately dated reviews per author per book/video, rating integer 1..5,
  optional text up to 4000 characters. No unique author/content constraint.
- Authenticated reading and immediate visibility. Authors edit/delete their own reviews;
  ADMIN/MANAGER may remove others' reviews, not edit their feedback.
- POST /api/v1/reviews creates (201 + Location); GET collection/item reads;
  PUT /{id} changes rating/text with required current version (200);
  DELETE /{id} removes (204). Retire /api/todo and task completion.
- Filters: contentType BOOK/VIDEO, contentId, authorId; page defaults 0, size 20,
  maximum 200; stable newest-first ordering by createdAt then ID descending.
- Verify book/video existence at creation using the caller's bearer token.
  Remote lookup happens before the DB transaction; missing content -> 404,
  unavailable catalog -> 503 with no review saved. Catalog access denial -> 403.
- Author is derived from JWT UUID subject; content and author remain immutable.
  Optimistic conflicts -> 409; validation -> 400; RFC 9457 errors.
- reviewsdb, runtime theuser, migration todoadmin using existing Vault/config passwords.
  Retain migration-account name deliberately; do not invent new credentials.
- API port 9171; standalone PostgreSQL host port 45432, shared PostgreSQL 5432.
  Existing Keycloak deployment is reused. Preserve old databases/volumes and Git history.
- No legacy task API compatibility, task-to-review conversion, moderation approval,
  rating aggregates, reactions, or Auth Service policy-decision integration in this version.

### Written On Disk (Not Verified)

Local directory moved:
  /Users/skakumanu/practice/todo-service -> /Users/skakumanu/practice/reviews-service

The existing .git directory/history/configuration moved with it. Branch remains master.
Origin STILL points to git@github.com:srikanthkakumanu/todo-service.git.
Last committed/pushed baseline remains 6820f45 (todo README + port fix).
No GitHub rename, commit, or push has been performed for this redesign.

Created reviews/ReviewsApplication.java and framework-independent domain records:
Review, ReviewId, AuthorId, Rating, ContentReference, ReviewActor, ReviewFilter,
ReviewPage, and ReviewException; input/output ports for use cases, persistence, catalog.
Created ReviewService with transaction orchestration and Clock/TransactionTemplate wiring.
Created JPA entity/repository/adapter, strict MapStruct persistence and web mappers,
catalog RestClient adapter with token propagation and 2-second connection/5-second read
timeouts, request/response records, current-actor extraction, REST controller, and
ProblemDetail exception advice.

Removed tracked legacy todo Java classes and MariaDB SQL scripts from the working tree.
These deletions are intentional and not yet committed. Old data/volumes were not touched.
Added PostgreSQL V1__create_reviews.sql with constraints and paging indexes.
Replaced build.gradle/settings.gradle and application.yaml for reviews/PostgreSQL.
Changed wrapper distribution URL to Gradle 9.8.0; wrapper JAR/scripts have NOT yet been
regenerated or verified. Replaced Dockerfile with Java 27 non-root executable-JAR image
and curl health check; image has NOT been built.

No builds/tests/container startup have run on the new code. Treat all implementation
as work in progress; do not describe it as working or production-ready.

### Resume In This Order

1. Inspect git diff/status in reviews-service and preserve all existing workspace changes.
2. Add explicit stateless JWT SecurityConfig with Keycloak realm-role conversion,
   public health only, authenticated review/docs access, and RFC 9457 401/403 responses.
   CurrentReviewActor exists, but the explicit security configuration is NOT written.
3. Regenerate/verify the independent Gradle 9.8.0 wrapper and executable bits.
   JDK 27 is at /Library/Java/JavaVirtualMachines/temurin-27.jdk/Contents/Home.
   Java 21 launcher remains available at the Microsoft JDK path in prior checkpoints.
4. Compile, fix API/MapStruct/framework compatibility issues, and add domain/use-case,
   MVC/security, catalog-client, and PostgreSQL integration tests. Tests do not exist yet.
5. Replace the still-legacy reviews-service/compose.yml and README.md.
   They STILL describe todo/MariaDB and must not be used as reviews setup instructions.
6. Update service-configs todo-service YAML filenames/content, reviewsdb Vault paths,
   PostgreSQL provisioning/grants (including existing-volume additive provisioning),
   and ignored local Vault seed. Do not commit secrets or erase old data.
7. Update micro-services Compose/Bake/build/clone references and docs; add reviews gateway
   route and downstream token-preserving target configuration in api-gateway.
8. Update implementation plan in Downloads (outside writable roots; approval required).
   Preserve checkpoint history, adding superseding decisions instead of rewriting history.
9. Run check, integrationTest, bootJar, Docker image/health verification, config/port checks,
   and real Keycloak/catalog smoke checks where feasible. Log remaining platform blockers.
10. Commit redesign-related changes separately in affected repos without staging unrelated
    pending README/ignore/clone edits. Rename the existing GitHub repository to reviews-service
    (do not create a replacement), update origin, push master, and verify sync/history/settings.
    Recheck target name availability before renaming; previous gh repo view reported no
    srikanthkakumanu/reviews-service repository accessible on 2026-10-04.

### Workspace And Infrastructure Notes

Docker was reachable during inspection (29.8.1); no containers started for this task.
Other repos retain earlier uncommitted README/port/.vscode edits. micro-services also has
user-added clone helper changes; preserve those while adjusting the service reference.
No platform/config/gateway source changes have been made for reviews yet.
The user has stepped away: stop work after saving this checkpoint, await their return.

## Reviews Resume And Credential Decision (2026-10-04)

User resumed implementation. Gradle 9.8.0 wrapper regeneration and compilation passed.
Added explicit JWT security and 28 unit/MVC/client tests plus four PostgreSQL tests;
test, integrationTest, and bootJar passed before the final password change.
User explicitly changed the migration/admin role AND password to reviewsadmin/reviewsadmin.
This supersedes the prior retained todoadmin credential decision. Runtime remains theuser.
Updated test provisioning; platform PostgreSQL/Vault wiring remains next.
No GitHub rename/commit/push yet. Implementation is active, no longer paused.

## Reviews Implementation And Verification (2026-10-04)

Implemented reviews-service, replacing task CRUD without converting/deleting old task data.
User's final credential decision is reviewsadmin/reviewsadmin, runtime theuser.
Local path: /Users/skakumanu/practice/reviews-service.
GitHub renamed in place to https://github.com/srikanthkakumanu/reviews-service.
Verified unchanged repository ID 903470136, public visibility, and master default branch.
Origin now points to git@github.com:srikanthkakumanu/reviews-service.git.

Completed Java 27/Boot 4.1.1/Cloud 2025.1.3/MapStruct 1.6.3/springdoc 3.1.1
service with regenerated independent Gradle 9.8.0 wrapper. Pure domain models/ports,
application transactions, JPA/MapStruct adapters, token-forwarding catalog validation,
stateless Keycloak JWT security and RFC 9457 errors are implemented.
API: /api/v1/reviews create/list/get/update/delete; repeated reviews supported,
author/content immutable, authors edit/delete, ADMIN/MANAGER remove others' reviews.
Optimistic versions protect updates; task endpoints are retired.

PostgreSQL reviewsdb migration, runtime/migration privileges, local Compose (45432 DB,
9171 API), shared Compose, Config Server YAML, Vault seed, gateway route, build/Bake/
clone references, READMEs and Downloads implementation-plan addendum updated.
02-reviews.sql is additive and explicitly reapplicable to an existing database;
old tododb/todoadmin provisioning remains for data preservation, not active reviews use.
Ignored local Vault seed remains outside Git and now creates reviewsdb secret entries.

Verification:
- ./gradlew check integrationTest bootJar passed on Java 27: 32 unit/MVC/client tests
  and 4 PostgreSQL Testcontainers tests, all green.
- API gateway test/bootJar passed.
- Java 27 non-root reviews Docker image built; fresh standalone PostgreSQL provisioning,
  schema migration/validation, startup and HTTP health 200 verified.
- Real Keycloak realm/client/users plus live book/video services and gateway smoke passed:
  genuine JWT validation/forwarding, repeated book/video reviews, owner-only edits,
  moderator removal (not editing), 409 stale edits, paging, 404 missing content,
  400 rating validation, retired task route, and 503 outage with no persisted review.
- Reapplying additive provisioning passed without deleting data.
- Compose JSON service identity, gateway target and unique shared ports verified;
  Bake, YAML, README links/fences/Bash syntax and git diff --check passed.

All temporary smoke containers are stopped; no existing user containers/data were removed.
The isolated reviews-service_reviews-postgres volume and stopped containers remain.
Smoke-only realm was reviews-smoke; temporary override/script files are in /private/tmp.
Normal application issuer remains company-platform from configuration.
Java 27 support exceeds Boot's documented ceiling; warnings are documented, tests passed.
Full legacy infrastructure/Vault/Config/Eureka combined startup remains separately
unverified; existing platform blockers are documented, not fixed by this redesign.

Commits/push are next; do not include unrelated pending .gitignore or other-service edits.

## Reviews GitHub Completion (2026-10-04)

Reviews redesign committed and pushed to master in the respective repositories:
- reviews-service: ab2a1a3 (renamed existing GitHub repository, origin updated)
- service-configs: f80a1a7 (new reviews YAML contracts)
- api-gateway: c4b54ac (authenticated reviews route)
- micro-services: 56c1828 (deployment/provisioning/build/clone wiring and checkpoint)

Final tests: 32 unit/MVC/catalog-client and 4 PostgreSQL integration tests passed.
Both valid end-to-end and catalog-outage/no-save smoke checks passed with actual
Keycloak JWTs through the gateway; Java 27 Docker image/health verification passed.
Temporary test containers are stopped. No volumes/data were deleted.
Implementation plan in Downloads includes the reviews domain replacement addendum.
Final deployment-documentation corrections are being committed/pushed separately.

Reviews, configuration and gateway working trees are clean before these final docs.
In micro-services, earlier .gitignore and unrelated auth/video clone-helper additions
remain uncommitted intentionally. Do not revert or accidentally stage them.
Ignored Vault seed is updated locally but not pushed; securely recreate it when cloning.
All older pause/WIP/todoadmin decisions in checkpoint history are superseded.
Next platform work is the documented legacy infrastructure/Config/Vault/Eureka and
remaining User/Auth integration alignment, not another to-do migration.
