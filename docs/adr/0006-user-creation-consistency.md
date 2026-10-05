# ADR 0006: Consistency when creating a user

- Status: accepted
- Date: 2026-10-05

## Context

Keycloak is the source of truth for identity, credentials and account state. user-service's database is the source of truth for the extended profile. Creating a user writes to both and they share no transaction.

## Decision

Creation is an application-level process with compensation (`com.users.application.UserCreation`):

1. Create the identity in Keycloak.
2. Insert the empty profile row.
3. For self-registration, send the verification email.
4. If step 2 or 3 fails, delete the profile row and the Keycloak user, then rethrow the original failure. A failure of the compensation itself is attached to it as a suppressed exception and logged.

Deletion runs the other way round: identity first, then profile. An orphaned profile row is unreachable and harmless; an identity without a profile would still be able to sign in, and the profile read path tolerates a missing row by returning an empty profile.

## Consequences

- A crash between steps 1 and 2 leaves an identity with no profile row. That is safe for the reason above (the bootstrap administrator is in exactly that state).
- A crash during compensation can leave an identity nobody asked for. It cannot sign in until its email is verified. A reconciliation job is the fix if this ever matters; it is not built now.
- Self-registration is all-or-nothing from the caller's point of view, including the email.
