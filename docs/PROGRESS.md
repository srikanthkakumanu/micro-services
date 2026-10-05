# Progress

Resume point for the clean-slate identity platform build. Branch in every in-scope repo: `feature/clean-slate-identity`. Nothing is pushed.

## Done

- **Phase 0 – discovery.** Report in [discovery-report.md](discovery-report.md); plan approved 2026-10-05.
- **Phase 1 – clean slate.** Legacy implementation removed in a separate `chore: remove legacy implementation` commit in `user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`, `service-configs` and `micro-services`. All Ruby scripts are gone.

- **Slice 2 – baseline and shared build.** ADRs 0001, 0003, 0011. Version catalog in `gradle/libs.versions.toml`. `platform-security-starter` built and tested on Java 27 (33 tests, none skipped: claims, authority mapping, tampered/unsigned/HS256/expired/future/wrong-issuer/wrong-audience/wrong-type tokens, key rotation, servlet and reactive auto-configuration). Gradle 9.8.0 wrapper and catalog-importing `settings.gradle` in the five service repos.

- **Slice 3 – infrastructure.** ADR 0008. `docker-compose.yml` with Postgres 18 (`keycloak`, `user_db`, `auth_db`, one owner each), Keycloak 26.8.0 importing `keycloak/platform-realm.json`, Vault 2.1.1 (dev), Mailpit, the one-shot `bootstrap` job, Eureka and the Config Server. `Makefile` with `up`, `down`, `reset`, `logs`, `ps`. Rebuilt `eureka-discovery` (3 tests) and `cloud-config-service` (6 tests against a real Vault container). Shared `application*.yml` in `service-configs`. Verified by hand on the running stack: all containers healthy; `platform-admin` can log in; the token carries `aud` for the three platform clients, a flat `permissions` array merged from both service clients and `typ: Bearer`; `iss` is `http://localhost:8080/realms/platform` both from the host and from inside the network; a service's Vault token cannot read another service's secrets.

- **Slice 4 – user-service.** ADRs 0006, 0007, 0012. Every `user` row of the §7 catalog except the MFA parts: registration, public password-reset request, user CRUD and search, enable/disable/lock/unlock, own and admin profile, account actions, admin credentials. 137 tests, none skipped: 60 domain, 33 use-case, 7 ArchUnit rules, 15 Keycloak adapter tests against Keycloak 26.8.0 with the realm file and Mailpit, 5 JPA tests against Postgres 18, 22 controller slice tests, 5 full-context tests with Keycloak-issued tokens. Line coverage on `domain` + `application`: 99.1% (build fails below 80%). Runs in Compose with the `docker` profile, config from the Config Server and secrets from Vault; a token obtained from the host is accepted inside the network.

- **Slice 5a/5b – auth-service authentication, password and sessions.** ADR 0005. Login, refresh (rotating, reuse rejected), logout, logout-all, introspect, revoke, who-am-I, service tokens, change own password, list own credentials, own and admin sessions, admin sign-out-everywhere. 61 tests, none skipped (12 domain, 13 use-case, 7 ArchUnit, 11 adapter tests against Keycloak 26.8.0, 11 controller slice, 7 full-context); `domain` + `application` line coverage 97.0%. Runs in Compose; a token from `POST /api/v1/auth/login` is accepted by user-service.

- **Slice 5c/5d (part) – roles, groups, role mappings, user groups, permissions.** ADR 0004. Realm and client role CRUD, composites, holders; group CRUD with subgroups, move, attributes, members and role mappings; user role assignment with direct and effective views; user groups; permission CRUD per service, attach and detach, a user's effective permissions. Escalation guards are domain rules. auth-service is now at 128 tests, none skipped, 98.9% line coverage on `domain` + `application`. Verified against Keycloak 26.8.0 that a role mapped to a parent group reaches a subgroup member's token, that a newly created and attached permission appears after refresh, and that revocation is reflected after refresh.

- **Slice 5d–5g – decisions, service clients, audit, tokens, keys, claims.** Single and batch decisions (permission and ownership rules); service-client register, update, enable/disable, delete, secret rotation to Vault, service-account roles; audit of login, admin and API events persisted in `auth_db`; token inspect, introspect, revoke and standard token exchange; token settings; signing-key rotation with the create → passive → disable → delete order enforced; custom claims platform-wide and per client. auth-service is complete for the §7 catalog except MFA: 207 tests, none skipped, 99.0% line coverage on `domain` + `application`. Verified against Keycloak 26.8.0, Vault 2.1.1 and Postgres 18: a service onboarded only through the API gets a working client-credentials token with its permission and audience; rotating its secret invalidates the old one; token exchange narrows `aud` to the target service; after key rotation old tokens stay valid and new ones carry the new `kid`; a custom claim appears on next login and disappears after removal.

- **Slice 6 – api-gateway.** Routes via Eureka with `/api/v1/users/{id}/roles|groups|permissions` ahead of `/api/v1/users/**`, public allow-list, token relay, correlation ID, CORS, OIDC discovery and JWKS pass-through, OpenAPI documents of both services behind `/docs/<service>/v3/api-docs` and one Swagger UI. 46 tests, none skipped. The security starter now has a configurable JWKS cache lifetime shared by servlet and reactive decoders (35 tests), so a retired signing key stops being trusted (15 s in dev).
- **Slice 7 – Docker.** Multi-stage Dockerfiles for all five Spring services (JDK 27 build, JRE 27 Alpine runtime, non-root, healthcheck). `make reset && make up` brings ten containers up healthy from a clean state; verified through the gateway: login, protected calls to both services, the user-roles route, discovery documents, 401 without a token.

- **Slice 8 – end-to-end suite.** REST Assured module `e2e`, run with `make test-e2e` against the running stack. 20 tests, none skipped, all passing, covering every §15.7 scenario except the MFA steps: self-service (register, verify email from Mailpit, login, profile, refresh, change password, sessions, logout), user admin, access admin with token checks, decisions, sessions, onboarding `sample-service` and secret rotation, guards, audit, JWT validation at the gateway and at each service directly, the JWT contract and custom claims, key rotation, lifetimes and refresh reuse, token exchange, issuer consistency between host and container network, and configuration. All test data is created through the APIs.

- **Slice 9 – Kubernetes.** ADR 0013. `k8s/base` and `k8s/overlays/dev`, namespace `identity-dev`, one replica per workload, probes on Actuator health groups, requests and limits, no Secret in Git. `scripts/k8s-up.sh` (`make k8s-up`) builds, applies, waits for rollout and runs the bootstrap Job. The end-to-end suite passes against it: `make test-e2e-k8s`, 20 tests, none skipped.

- **Slice 10 – docs and Definition of Done.** ADRs 0002, 0009, 0010; `jwt-contract.md`; `integrating-a-new-service.md`; a README in each of the seven repositories. Sensitive operations in both services now check the caller's token by introspection (ADR 0009).

## Final verification (2026-10-05)

| Check | Result |
| --- | --- |
| `./gradlew build` in `user-service` | 139 tests, 0 failed, 0 skipped; `domain` + `application` line coverage 99% |
| `./gradlew build` in `auth-service` | 209 tests, 0 failed, 0 skipped; `domain` + `application` line coverage 99% |
| `./gradlew build` in `api-gateway` | 46 tests, 0 failed, 0 skipped |
| `./gradlew build` in `cloud-config-service`, `eureka-discovery` | 6 and 3 tests, 0 failed, 0 skipped |
| `./gradlew build` in `micro-services` (security starter) | 35 tests, 0 failed, 0 skipped |
| `make reset && make up` | ten containers healthy from a clean state |
| `make test-e2e` (Compose) | 21 tests, 0 failed, 0 skipped |
| `make k8s-up` then `make test-e2e-k8s` | rollout complete, 21 tests, 0 failed, 0 skipped |
| Secret scan of tracked files | nothing but the labelled dev Vault root token in `.env.example` |

## Definition of Done

- [x] Discovery report and plan approved; legacy and Ruby code removed in a separate commit
- [x] Version baseline ADR written; all services build with `./gradlew build` (on Java 27, by owner decision, instead of 21)
- [x] ArchUnit rules pass in `user-service` and `auth-service`
- [x] All unit, integration, API, gateway and e2e tests pass
- [x] `make up` brings the whole stack up healthy from a clean state with one command
- [x] Kubernetes dev overlay deploys with one replica each and the e2e suite passes against it
- [x] No secrets in Git; all sensitive values come from Vault
- [x] Every operation in the §7 catalog is implemented and covered by tests, except MFA (owner decision); no step requires the Keycloak UI
- [x] API coverage ADR lists what is deliberately left out, with reasons
- [x] `docs/jwt-contract.md` written; token settings, signing keys and claims are managed through the APIs; JWT validation, rotation and revocation tests pass
- [x] OpenAPI docs reachable per service and together at the gateway; `README.md` in each repo
- [x] `docs/PROGRESS.md`, ADRs and `docs/integrating-a-new-service.md` are current

ArchUnit layering rules exist in the two services that have layers. The gateway, the Config Server and Eureka are single-package infrastructure applications with nothing to layer.

## Not done

- Nothing is pushed and no pull requests are open. All work is on `feature/clean-slate-identity` in each repository.
- GitHub Actions workflows were removed with the legacy code and not rebuilt.

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

- In the JWT validation scenario, "wrong `iss`" and "future `nbf`" are forged with a key the platform never published, because the platform itself will not sign such tokens. The claim checks themselves, with a valid signature, are covered by the security starter's tests.
- Validators limit how often an unknown `kid` makes them refetch the JWKS (5 s). Under a flood of forged tokens, tokens signed by a key rotated in during that window are rejected until the next refetch.
- Bean validation runs before method security, so a caller without the permission who sends an invalid body gets 400 rather than 403. Nothing is executed or disclosed either way.

- After a signing key is disabled, the identity provider rejects its tokens at once; a service rejects them when its JWKS cache expires (`platform.security.jwt.jwk-set-cache-ttl`, 15 s in dev, 5 min default).
- Keycloak admin events name the platform's service account as the actor. The person behind a change is on the matching `API` audit event recorded by auth-service; user-service does not record API events yet.
- A registered service is added to the audience of every platform user token, so it can accept user tokens directly. Narrower tokens are available through token exchange.

- A user's roles, groups and effective permissions are read at `/api/v1/users/{id}/roles|groups|permissions`. auth-service serves those paths and the gateway routes them there, so user-service itself holds no access data and never mutates access.
- Listing users sorts by username ascending only, because that is the only order Keycloak's user search offers. Any other `sort` value is rejected with 400.

- Keycloak adds `offline_access` and `uma_authorization` to the default role on import, so they show up in `realm_access.roles`. Harmless; decide in the JWT contract whether to strip them.
- On this machine containers cannot reach `release-assets.githubusercontent.com`, so the Gradle wrapper cannot download its distribution inside an image build. The Dockerfiles take Gradle 9.8.0 from the official `gradle` image instead and the JDK from `eclipse-temurin:27-jdk`.

- `postgres-init/init.sql` and the books/video/reviews files in `service-configs` still carry uncommitted modifications from before the rebuild. They were left exactly as found because they belong to the out-of-scope services.
- The parked files in `legacy/` will not work against the new realm until those services are onboarded.
