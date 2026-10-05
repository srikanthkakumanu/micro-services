# ADR 0009: Revocation and the `jti` denylist

- Status: accepted
- Date: 2026-10-05

## Context

Access tokens are verified offline, so one stays valid until it expires even after its session has ended. `CLAUDE.md` §10a asks for session-based revocation, an introspection check on sensitive operations, and a decision on a `jti` denylist.

## Decision

1. **Revocation is session-based.** Logout, logout-all, admin sign-out, admin session revocation, disabling or locking a user, an admin password reset, removing a credential and changing one's own password all end sessions in Keycloak. The refresh tokens of those sessions are rejected immediately.
2. **Access tokens are short** (5 minutes in dev) and expire naturally.
3. **Sensitive operations introspect.** Before a state-changing call on the paths below, the service asks Keycloak whether the caller's token is still active and answers `401 invalid-token` if not.
   - auth-service: roles, groups, permissions, clients, role and group mappings of users, sessions, own password, signing keys, token settings, token claims.
   - user-service: creating, changing, enabling, disabling, locking, unlocking and deleting users, credentials, account actions.
4. **No `jti` denylist now.** It would need shared, low-latency storage consulted by the gateway and every service on every request, to shorten a window that is at most one access-token lifetime and is already closed for the operations that matter. If immediate revocation everywhere becomes a requirement, shortening the access-token lifetime through `/api/v1/token-settings` is the first lever; a denylist in the gateway is the second.

## Consequences

- A token from an ended session can still read for up to five minutes. It cannot change access, credentials, clients or users.
- Introspection adds one call to Keycloak per sensitive operation. If Keycloak cannot be reached, the operation is refused (`503`), not let through.
- A retired signing key is the other way a token dies early: validators stop trusting it when their JWKS cache expires (15 s in dev).
