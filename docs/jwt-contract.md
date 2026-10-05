# Access token contract (v1)

The platform's access token is a public API. Services and clients may rely on everything on this page; anything else is not part of the contract. Changes are reviewed and versioned like any other API change. The end-to-end suite checks that a token carries exactly these claims.

Keycloak is the only issuer. Tokens are signed with **RS256**; the header carries `kid`.

## Claims

| Claim | Type | Meaning |
| --- | --- | --- |
| `iss` | string | Issuer. Always the public realm URL, for example `http://localhost:8080/realms/platform`, wherever the token was requested. |
| `sub` | string (UUID) | The user's ID, or the service account's for a service token. |
| `aud` | string or array | The client IDs of the services the token is meant for. |
| `exp`, `iat` | number | Expiry and issue time, seconds since the epoch. |
| `nbf` | number | Not-before. Present only if set. |
| `jti` | string | Token ID. |
| `azp` | string | The client the token was issued to: `auth-service` for a login, the service's own client ID for a service or exchanged token. |
| `sid` | string | Session ID. Absent on service tokens. |
| `typ` | string | `Bearer` for an access token. |
| `scope` | string | OAuth scope string; empty for platform tokens. |
| `preferred_username` | string | Username. `service-account-<clientId>` for a service token. |
| `email`, `email_verified`, `name` | string, boolean, string | Present for people. |
| `realm_access.roles` | array of strings | Effective realm roles, through composites and groups. |
| `permissions` | array of strings | Effective permissions as `<resource>:<action>`. Absent when there are none. |
| `groups` | array of strings | Paths of the groups the user is a direct member of. Absent when there are none. |

Besides the seed roles, `realm_access.roles` contains `default-roles-platform` and Keycloak's `offline_access` and `uma_authorization`. They carry no platform meaning.

The token holds nothing else about the user. Profile data is read from user-service.

Custom claims added through `/api/v1/token-claims` appear alongside these; they can never replace one of them.

## Audiences

- A login token names `api-gateway`, `user-service`, `auth-service` and every registered service client.
- A service token names the services listed in the client's `audiences` at registration.
- An exchanged token names exactly the one service it was requested for.

A service rejects a token that does not name it.

## What every validator checks

The gateway and each service check the same things, through `platform-security-starter`:

1. Signature against the JWKS. Keys are cached (`jwk-set-cache-ttl`); an unknown `kid` causes a refetch.
2. Algorithm is on the allowlist (RS256). `none` and HMAC algorithms are rejected.
3. `iss` equals the configured issuer exactly.
4. `aud` contains the service's own client ID.
5. `exp` and `nbf`, with a configurable clock skew (30 s).
6. `typ` is `Bearer`.

Authorities are mapped by one converter: realm roles become `ROLE_<name>`, permissions are used as they are. In code, use `AccessTokenClaims` rather than reading claims by name.

## Lifetimes and revocation

- Access tokens: 5 minutes in dev. Refresh tokens rotate, and a spent one is rejected.
- Logout, sign-out-everywhere, disabling or locking a user, an admin password reset and admin session revocation end sessions, so their refresh tokens stop working at once.
- An access token stays valid until it expires. For changes to access, credentials, clients, sessions and token configuration, the services also ask the identity provider whether the token is still active (ADR 0009).
- Settings are changed through `/api/v1/token-settings`, keys through `/api/v1/keys`.
