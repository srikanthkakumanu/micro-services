# Progress

Resume point for the clean-slate identity platform build. Branch in every in-scope repo: `feature/clean-slate-identity`. Nothing is pushed.

## Done

- **Phase 0 – discovery.** Report in [discovery-report.md](discovery-report.md); plan approved 2026-10-05.
- **Phase 1 – clean slate.** Legacy implementation removed in a separate `chore: remove legacy implementation` commit in `user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`, `service-configs` and `micro-services`. All Ruby scripts are gone.

- **Slice 2 – baseline and shared build.** ADRs 0001, 0003, 0011. Version catalog in `gradle/libs.versions.toml`. `platform-security-starter` built and tested on Java 27 (33 tests, none skipped: claims, authority mapping, tampered/unsigned/HS256/expired/future/wrong-issuer/wrong-audience/wrong-type tokens, key rotation, servlet and reactive auto-configuration). Gradle 9.8.0 wrapper and catalog-importing `settings.gradle` in the five service repos.

- **Slice 3 – infrastructure.** ADR 0008. `docker-compose.yml` with Postgres 18 (`keycloak`, `user_db`, `auth_db`, one owner each), Keycloak 26.8.0 importing `keycloak/platform-realm.json`, Vault 2.1.1 (dev), Mailpit, the one-shot `bootstrap` job, Eureka and the Config Server. `Makefile` with `up`, `down`, `reset`, `logs`, `ps`. Rebuilt `eureka-discovery` (3 tests) and `cloud-config-service` (6 tests against a real Vault container). Shared `application*.yml` in `service-configs`. Verified by hand on the running stack: all containers healthy; `platform-admin` can log in; the token carries `aud` for the three platform clients, a flat `permissions` array merged from both service clients and `typ: Bearer`; `iss` is `http://localhost:8080/realms/platform` both from the host and from inside the network; a service's Vault token cannot read another service's secrets.

## In progress

- Slice 4 – user-service.

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

- Email verification and password-reset emails are account actions, not authentication, so they stay in scope and use Mailpit.
- Eureka and Config Server run without HTTP Basic inside the dev network, behind a property.
- GitHub Actions workflows were removed with the legacy code and are not rebuilt.
- Local Kubernetes target is Docker Desktop (the only cluster present).

## Open issues

- Keycloak adds `offline_access` and `uma_authorization` to the default role on import, so they show up in `realm_access.roles`. Harmless; decide in the JWT contract whether to strip them.
- On this machine containers cannot reach `release-assets.githubusercontent.com`, so the Gradle wrapper cannot download its distribution inside an image build. The Dockerfiles take Gradle 9.8.0 from the official `gradle` image instead and the JDK from `eclipse-temurin:27-jdk`.
- `make test-e2e` is added with the end-to-end slice.

- `postgres-init/init.sql` and the books/video/reviews files in `service-configs` still carry uncommitted modifications from before the rebuild. They were left exactly as found because they belong to the out-of-scope services.
- The parked files in `legacy/` will not work against the new realm until those services are onboarded.
