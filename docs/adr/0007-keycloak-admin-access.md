# ADR 0007: Split of Keycloak admin access

- Status: accepted (auth-service column to be confirmed when that service is built)
- Date: 2026-10-05

## Decision

Each service authenticates to the Admin API as its own confidential client's service account. The roles are granted in `keycloak/platform-realm.json`.

| `realm-management` role | user-service | auth-service | Why |
| --- | --- | --- | --- |
| `manage-users`, `view-users`, `query-users` | yes | yes | user lifecycle and credentials; role and group mappings, sessions |
| `view-realm` | yes | yes | user-service reads the members of `PLATFORM_ADMIN` to protect the last administrator |
| `manage-realm` | no | yes | roles, groups, token settings, signing keys |
| `manage-clients`, `view-clients`, `query-clients` | no | yes | service clients, permissions (client roles), claim mappers |
| `view-events`, `manage-events` | no | yes | audit |
| `query-groups` | no | yes | group lookups |
| `manage-authorization` | no | no | permission option 2 was not chosen (ADR 0004) |

`api-gateway` has a service account with no admin roles.

## Consequences

- `view-realm` for user-service goes beyond the three roles `CLAUDE.md` lists. It is read-only, and the alternative (asking auth-service on every disable, lock and delete) would couple the two contexts at runtime.
- user-service cannot change roles, groups or clients: it never mutates access.
