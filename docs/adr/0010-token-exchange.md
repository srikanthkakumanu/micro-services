# ADR 0010: Token exchange

- Status: accepted
- Date: 2026-10-05

## Context

A service that calls another service on behalf of a user should not forward the user's broad token, and should not call with its own identity and lose the user.

## Decision

Use Keycloak's **standard token exchange** (RFC 8693, available in 26.8.0), behind `TokenProviderPort.exchange` and exposed as `POST /api/v1/tokens/exchange`. The fallback named in `CLAUDE.md` (client credentials plus forwarded user context) is not needed.

- The calling service authenticates with its own client ID and secret in the request and presents the user's access token as the subject token.
- The result keeps the user as `sub`, has the calling service as `azp`, and names exactly the requested audience.
- A service can only obtain audiences it registered with (`audiences` on `POST /api/v1/clients`); anything else is `403 operation-not-permitted`.
- The user's token must name the calling service in its `aud`. It does, because registered services are added to the audience of platform user tokens.
- Clients registered through the API have exchange enabled. The three seeded platform clients do too.

## Consequences

- The endpoint is public in the sense of needing no bearer token: the client secret is the credential, as for `/api/v1/auth/service-token`.
- Exchanged tokens are not refreshable; the service exchanges again when it needs a new one.
- Verified end to end: an exchanged token is accepted by the target service as the user and rejected by a service it was not narrowed to.
