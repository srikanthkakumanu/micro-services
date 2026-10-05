# ADR 0012: Lock versus disable

- Status: accepted
- Date: 2026-10-05

## Context

The API has both enable/disable and lock/unlock. Keycloak has one `enabled` flag plus temporary lockout from failed sign-ins (brute-force detection).

## Decision

The domain has three account states: `ACTIVE`, `DISABLED`, `LOCKED`.

- **Disabled**: switched off. Stored as `enabled = false`.
- **Locked**: suspended by an administrator until explicitly unlocked. Stored as `enabled = false` plus the user attribute `platform.locked = true`.
- A locked user cannot be enabled or disabled, only unlocked. A disabled user cannot be locked.
- Unlocking and enabling also clear Keycloak's failed-sign-in lockout for that user.
- Disabling and locking end all of the user's sessions.

The realm's user profile allows attributes that only administrators can edit (`unmanagedAttributePolicy: ADMIN_EDIT`), so users cannot remove the marker themselves.

## Consequences

- Neither state can sign in; the difference is visible to administrators and enforced by the domain.
- Someone who re-enables a locked user in the Keycloak console bypasses the rule and leaves a stale marker. The console is for debugging only.
