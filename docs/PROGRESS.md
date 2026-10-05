# Progress

Resume point for the clean-slate identity platform build. Branch in every in-scope repo: `feature/clean-slate-identity`. Nothing is pushed.

## Done

- **Phase 0 – discovery.** Report in [discovery-report.md](discovery-report.md); plan approved 2026-10-05.
- **Phase 1 – clean slate.** Legacy implementation removed in a separate `chore: remove legacy implementation` commit in `user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`, `service-configs` and `micro-services`. All Ruby scripts are gone.

- **Slice 2 – baseline and shared build.** ADRs 0001, 0003, 0011. Version catalog in `gradle/libs.versions.toml`. `platform-security-starter` built and tested on Java 27 (33 tests, none skipped: claims, authority mapping, tampered/unsigned/HS256/expired/future/wrong-issuer/wrong-audience/wrong-type tokens, key rotation, servlet and reactive auto-configuration). Gradle 9.8.0 wrapper and catalog-importing `settings.gradle` in the five service repos.

- **Slice 3 – infrastructure.** ADR 0008. `docker-compose.yml` with Postgres 18 (`keycloak`, `user_db`, `auth_db`, one owner each), Keycloak 26.8.0 importing `keycloak/platform-realm.json`, Vault 2.1.1 (dev), Mailpit, the one-shot `bootstrap` job, Eureka and the Config Server. `Makefile` with `up`, `down`, `reset`, `logs`, `ps`. Rebuilt `eureka-discovery` (3 tests) and `cloud-config-service` (6 tests against a real Vault container). Shared `application*.yml` in `service-configs`. Verified by hand on the running stack: all containers healthy; `platform-admin` can log in; the token carries `aud` for the three platform clients, a flat `permissions` array merged from both service clients and `typ: Bearer`; `iss` is `http://localhost:8080/realms/platform` both from the host and from inside the network; a service's Vault token cannot read another service's secrets.

- **Slice 4 – user-service.** ADRs 0006, 0007, 0012. Every `user` row of the §7 catalog except the MFA parts: registration, public password-reset request, user CRUD and search, enable/disable/lock/unlock, own and admin profile, account actions, admin credentials. 137 tests, none skipped: 60 domain, 33 use-case, 7 ArchUnit rules, 15 Keycloak adapter tests against Keycloak 26.8.0 with the realm file and Mailpit, 5 JPA tests against Postgres 18, 22 controller slice tests, 5 full-context tests with Keycloak-issued tokens. Line coverage on `domain` + `application`: 99.1% (build fails below 80%). Runs in Compose with the `docker` profile, config from the Config Server and secrets from Vault; a token obtained from the host is accepted inside the network.

- **Slice 5a/5b – auth-service authentication, password and sessions.** ADR 0005. Login, refresh (rotating, reuse rejected), logout, logout-all, introspect, revoke, who-am-I, service tokens, change own password, list own credentials, own and admin sessions, admin sign-out-everywhere. 61 tests, none skipped (12 domain, 13 use-case, 7 ArchUnit, 11 adapter tests against Keycloak 26.8.0, 11 controller slice, 7 full-context); `domain` + `application` line coverage 97.0%. Runs in Compose; a token from `POST /api/v1/auth/login` is accepted by user-service.

## In progress

- Slice 5c–5g – auth-service authorization model: roles and groups, permissions and decisions, service clients, audit, tokens/keys/claims.

## Next

3. Infrastructure: compose (Postgres, Keycloak, Vault, Mailpit), realm import, Vault bootstrap, Eureka, Config Server, `service-configs`
4. user-service
5. auth-service (authentication → password/sessions → roles/groups → permissions/decisions → clients → audit → tokens/keys/claims)
6. api-gateway
7. Docker
8. End-to-end suite
9. Kubernetes
10. Docs and Definition of Done

## Decisions

| Decision | Source |
| --- | --- |
| Java 27 toolchain on Gradle 9.8.0 instead of `CLAUDE.md`'s Java 21 | Owner, 2026-10-05. Verified locally that Gradle 9.8.0 runs on Temurin 27. |
| No MFA: TOTP/OTP, email-code and SMS authentication are out of scope, including the OTP login step, `/api/v1/auth/me/mfa`, the configure-OTP required action and lost-OTP removal | Owner, 2026-10-05. Deviates from `CLAUDE.md` §6, §7, §10, §15. |
| Uncommitted work in the in-scope repos was discarded | Owner, 2026-10-05 |
| books/video/reviews assets are parked, not deleted: `service-configs/*` for them and `postgres-init/` untouched, old compose/bake/k8s copied to `legacy/` | Owner, 2026-10-05 |
| Base packages `com.users`, `com.auth`, `com.gateway`, `com.discovery`, `com.config` | Owner, 2026-10-05 |
| `micro-services` is the platform root: compose, k8s, realm, scripts, docs, ADRs, e2e module, shared version catalog and security starter | Discovery |
| Realm name `platform`; ports kept (Eureka 9111, user 9121, auth 9141, gateway 9211, config 9311) | Discovery |

## Assumptions

- `POST /api/v1/auth/service-token` is public in addition to the endpoints `CLAUDE.md` lists: a service has no token yet and authenticates with its client secret in the body.
- `GET /api/v1/auth/me` answers from the caller's validated token, so it reflects role changes after the next refresh.

- Email verification and password-reset emails are account actions, not authentication, so they stay in scope and use Mailpit.
- Eureka and Config Server run without HTTP Basic inside the dev network, behind a property.
- GitHub Actions workflows were removed with the legacy code and are not rebuilt.
- Local Kubernetes target is Docker Desktop (the only cluster present).

## Open issues

- user-service does not yet offer the read-only view of a user's roles, groups and permissions; it is added once auth-service exposes them.
- Introspection of the caller's token on sensitive user-service operations (credential changes) is not wired yet; it needs auth-service's introspection endpoint.
- Listing users sorts by username ascending only, because that is the only order Keycloak's user search offers. Any other `sort` value is rejected with 400.

- Keycloak adds `offline_access` and `uma_authorization` to the default role on import, so they show up in `realm_access.roles`. Harmless; decide in the JWT contract whether to strip them.
- On this machine containers cannot reach `release-assets.githubusercontent.com`, so the Gradle wrapper cannot download its distribution inside an image build. The Dockerfiles take Gradle 9.8.0 from the official `gradle` image instead and the JDK from `eclipse-temurin:27-jdk`.
- `make test-e2e` is added with the end-to-end slice.

- `postgres-init/init.sql` and the books/video/reviews files in `service-configs` still carry uncommitted modifications from before the rebuild. They were left exactly as found because they belong to the out-of-scope services.
- The parked files in `legacy/` will not work against the new realm until those services are onboarded.
