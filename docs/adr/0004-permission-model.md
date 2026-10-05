# ADR 0004: Permissions are client roles

- Status: accepted
- Date: 2026-10-05

## Context

Permissions are a first-class domain concept named `<resource>:<action>`, owned by the service that enforces them. Roles grant permissions, and permissions must reach the access token so services authorize without a network call. `CLAUDE.md` §6 asks us to choose between client roles and Keycloak Authorization Services.

## Decision

Option 1. A permission is a **client role on the owning service's Keycloak client**. Realm roles are composites over other realm roles and over permissions.

- The `platform-permissions` client scope has one client-role mapper per service client, all writing to the same multivalued `permissions` claim. Keycloak merges them into one flat array (verified against 26.8.0).
- Onboarding a service adds a mapper for its client to that scope; that is part of registering a service client.
- Contextual checks that a static token cannot express (ownership, attributes) go through `POST /api/v1/authz/decisions`, with the rules held in auth-service.

Option 2 (Authorization Services with UMA) was not chosen: decisions would need a round trip to Keycloak for every check, tokens would not carry permissions, and nothing in the requirements needs resource-level policies inside Keycloak.

The domain model (`Permission`, `PermissionName`, `Role`, `RoleRef`, `Group`) does not mention client roles. `Permission.asRole()` is the only place the storage choice shows, and only `KeycloakRoleAdapter` and `KeycloakRoleSupport` know Keycloak.

## Rules that follow

- A client role is listed as a permission only if its name matches `<resource>:<action>`.
- Creating a permission also adds it to `PLATFORM_ADMIN`, so "platform administrators hold every permission" stays true and a new permission can be attached to roles straight away.
- Nobody grants, bundles or revokes a role or permission they do not hold. This covers assigning to users and groups, adding composites, attaching permissions, adding a user to a group, and moving a group under a parent. Platform administrators hold everything.
- Roles and permissions seeded by the realm import carry the attribute `platform.system=true` and cannot be deleted.
- The last enabled direct holder of `PLATFORM_ADMIN` cannot have that role revoked.

## Consequences

- A change in roles or permissions reaches a user's token at their next refresh or login.
- The last-administrator rule counts direct holders only. An administrator who holds the role solely through a group is not counted.
- For a group, Keycloak's effective client-role listing omits permissions that come through a realm role inherited from a parent group. The adapter adds the client composites of each effective realm role itself.
