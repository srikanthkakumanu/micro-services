# Progress

Resume point for the clean-slate identity platform build. Branch in every in-scope repo: `feature/clean-slate-identity`. Nothing is pushed.

## Done

- **Phase 0 – discovery.** Report in [discovery-report.md](discovery-report.md); plan approved 2026-10-05.
- **Phase 1 – clean slate.** Legacy implementation removed in a separate `chore: remove legacy implementation` commit in `user-service`, `auth-service`, `api-gateway`, `eureka-discovery`, `cloud-config-service`, `service-configs` and `micro-services`. All Ruby scripts are gone.

## In progress

- Slice 2 – version baseline ADR, shared version catalog, security starter, Gradle skeletons.

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

- `postgres-init/init.sql` and the books/video/reviews files in `service-configs` still carry uncommitted modifications from before the rebuild. They were left exactly as found because they belong to the out-of-scope services.
- The parked files in `legacy/` will not work against the new realm until those services are onboarded.
