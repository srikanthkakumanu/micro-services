# ADR 0005: API login uses the direct access grant, for now

- Status: accepted
- Date: 2026-10-05

## Context

`POST /api/v1/auth/login` takes a username and password and returns tokens. Against Keycloak that is the Resource Owner Password Credentials grant (Keycloak calls it Direct Access Grant). OAuth 2.1 removes this grant: the client sees the user's password, and browser-based factors and federation cannot take part.

## Decision

Accept it for this development environment. `auth-service` is the only client with the grant enabled, and the grant is used in exactly one place: `KeycloakTokenAdapter.authenticate`, behind `AuthenticationProviderPort`.

The API contract does not expose the grant. Callers send credentials and receive `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `refreshExpiresIn` and `scope`; refresh, logout, introspection and revocation never mention it.

## Migration path

1. **Authorization Code + PKCE through a backend-for-frontend.** The browser is redirected to Keycloak, the BFF exchanges the code and keeps tokens server-side. `frontend` already exists in the realm as a public PKCE client. `/api/v1/auth/login` is then replaced by a redirect-starting endpoint for browsers; refresh, logout, sessions and everything else keep their contracts.
2. **Token exchange** for first-party clients that already hold another trusted token.

Either way the change is a new adapter for `AuthenticationProviderPort` plus turning the grant off on the `auth-service` client. The domain, the use cases and the other endpoints do not change.

## Consequences

- No MFA or brokered login can be added while this grant is in use. MFA is out of scope by owner decision.
- `auth-service` handles passwords in memory. They are wrapped in `Secret`, which never prints its value, and request models override `toString()`.
- Changing your own password re-uses the grant to prove knowledge of the current password.
