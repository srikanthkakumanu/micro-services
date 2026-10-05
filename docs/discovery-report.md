# Discovery report (Phase 0)

Read-only discovery of the in-scope repositories, done on 2026-10-05 before the clean-slate rebuild. `books-service`, `reviews-service` and `video-service` were not read.

State at discovery: all seven in-scope repositories were on `master` with uncommitted changes. By decision of the owner that uncommitted work was discarded, not preserved.


### Role of `micro-services`

It is the **platform root / orchestration repo**, not a Gradle parent: it holds the shared `compose.yml`, `docker-bake.hcl`, Kubernetes manifests, the Keycloak realm file, Postgres init SQL, Vault scripts and platform docs. Each service is an independent sibling repo with its own Gradle wrapper. It stays the root: compose, k8s, realm, scripts, ADRs, `PROGRESS.md`, the e2e module and the shared build/starter modules will live here.

### Per repo

| Repo | Today | Stack |
| --- | --- | --- |
| `user-service` | Layered (controller/service/repository, Lombok JPA entities in `user.domain`), own `tbl_role` table, hand-rolled `RestClient` Keycloak client, `/api/users/**` (unversioned), no Config/Vault/Eureka client | Boot 4.1.1, Java 27, Gradle 9.8.0, port 9121 |
| `auth-service` | Small hexagonal app; roles/permissions/overrides in its **own DB**, no Keycloak adapter; only `/api/v1/roles` and `/api/v1/permissions` | Boot 4.1.1, Cloud 2025.1.3, port 9141 |
| `api-gateway` | WebFlux gateway, JWT resource server, routes for all five domain services, Swagger namespacing filter, shared audience `company-platform-api` | Boot 4.1.1, Cloud 2025.1.3, port 9211 |
| `eureka-discovery` | Standalone Eureka with HTTP Basic (in-memory users) | port 9111 |
| `cloud-config-service` | Config Server, `native` backend on `../service-configs`, Vault backend disabled, context path `/config` | port 9311 |
| `service-configs` | `*.yaml` per service for dev/qa; default Vault token `srikanth` hard-coded in `user-service.yaml` | no build |
| `micro-services` | See above; compose runs 8 Java services + Postgres 18, Keycloak `latest`, Vault `latest` | — |

### To delete (Phase 1)

**All five Java repos** (`user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`): entire `src/`, `build.gradle`, `Dockerfile`, `.dockerignore`, `README.md`, `.github/workflows/build.yml` (Java 27 CI with pinned wrapper hashes), and the untracked `bin/` directories (IDE output plus Ruby).

Ruby and other legacy, exact paths:

- `user-service/bin/verify-image.rb`, `auth-service/bin/verify-image.rb`, `api-gateway/bin/verify-image.rb`, `eureka-discovery/bin/verify-image.rb`, `cloud-config-service/bin/verify-image.rb`, `cloud-config-service/bin/verify-native-config.rb`, `api-gateway/bin/verify-swagger-browser.cjs`
- `micro-services/bin/verify-{k8s,keycloak,keycloak-apis,keycloak-login,postgres,vault,platform-config}.rb`
- `user-service`: `HELP.md`, `requests.http`, `compose.yaml`, `src/main/resources/db_scripts/` (incl. `.bak`), `logback-spring.xml.bak`
- `eureka-discovery`: `compose.yml`, `src/main/java/config/SecurityConfig.java.bak`
- `micro-services`: `init_db_users_config.sql` and `mariadb-init/` (MariaDB), `init-vault-prod.sh`, `vault/` (old scripts, `vault-prod.hcl`), `build.bat`, `build.sh`, `clone_repos.bat`, `clone_repos.sh`, `qodana.yaml`, `IAM_IMPLEMENTATION_CHECKPOINT.md`, `API_SECURITY_CONTROL_MATRIX.md`, `README.md`, `.env.example`, `keycloak/` (realm `company-platform`), `k8s/*.json` + `kustomization.yaml` + `README.md` + `settings.json` + `secrets.env.example` for platform services
- `service-configs`: `auth-service*.yaml`, `user-service*.yaml`, `README.md`

**Parked, not deleted:** `micro-services/postgres-init/` (untouched), `service-configs/{books,video,reviews}-service*.yaml` (untouched), and the books/video/reviews parts of `compose.yml`, `docker-bake.hcl` and `k8s/*.json`, moved to `micro-services/legacy/` with a README saying they are unmaintained until those services are onboarded.

### Worth keeping

- **Ports**: Eureka 9111, user 9121, auth 9141, gateway 9211, config 9311, Postgres 5432, Keycloak 8080, Vault 8200.
- **Names**: service IDs `user-service`, `auth-service`, `api-gateway`; Eureka app name `eureka-discovery-service`.
- **Patterns**: non-root Alpine runtime with Actuator readiness healthcheck; separate Flyway (migration) and runtime DB roles; gateway stripping caller-supplied `X-Forwarded-*` / identity headers; virtual threads.
- **Tooling present locally**: JDK 21 and 27, Docker 29.8 + Compose v5.5, `kubectl` with a `docker-desktop` context, `jq`, `make`. No `kind`, `minikube`, `kustomize` (use `kubectl kustomize`) or `vault` CLI (run it in the container).

### Findings that shape the plan

- auth-service's own roles/permissions tables contradict "Keycloak is the source of truth"; they go.
- The single shared audience `company-platform-api` contradicts per-service audiences (§10a); replaced by audience mappers per client.
- Existing realm disables direct grants and registration and has `ADMIN/MANAGER/USER` roles; the new realm needs direct grant on a login client and the `USER/USER_ADMIN/ACCESS_ADMIN/PLATFORM_ADMIN` seed roles.
- Latest Keycloak is **26.8.0**, which has standard token exchange (v2), so `/api/v1/tokens/exchange` uses it rather than the fallback.
