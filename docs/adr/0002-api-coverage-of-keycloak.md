# ADR 0002: API coverage of Keycloak

- Status: accepted
- Date: 2026-10-05

## Context

`user-service` and `auth-service` are the only way to perform routine identity and access operations. Anything a routine task would need the Keycloak console for is a gap. Anything deliberately left out must be listed with its reason.

## Covered

Every row of the operations catalog in `CLAUDE.md` §7 is implemented and tested, except the MFA rows below. Beyond the catalog the APIs also cover: a public password-reset request, effective permissions of a user and of a role, service-account roles of a client, per-client custom claims and extra audiences, and an audit trail of API calls with the person who made them.

## Deliberately not exposed

| Keycloak capability | Why not |
| --- | --- |
| MFA: TOTP enrolment, verification and removal, OTP login step, OTP policy, the configure-OTP required action; email-code and SMS factors | Out of scope by owner decision (2026-10-05). The login flow (ADR 0005) would have to change first in any case. |
| Creating, deleting or renaming realms | One realm, created from the realm file at bootstrap. Not routine. |
| Themes, login pages, email templates, localisation | Presentation of Keycloak's own pages. The platform's clients do not show them, apart from the email-link confirmation. |
| Identity-provider brokering and social login | No external identity provider is in scope. Needs the browser flow of ADR 0005. |
| User federation (LDAP, Kerberos) | No directory to federate with. |
| Authentication flow editor, required-action and authenticator configuration | Structural, changed with the realm file, not day to day. |
| Client scopes as a general concept, consent screens, client policies and profiles | The platform manages the few scopes it needs itself (permissions, audiences, claims). Exposing scopes generally would let callers break the token contract. |
| Public and browser clients, redirect URIs, web origins | Only confidential service clients are managed. A browser client arrives with ADR 0005's migration; the `frontend` placeholder is in the realm file. |
| Authorization Services (resources, scopes, policies, UMA) | Permission option 2 was not chosen (ADR 0004). |
| Realm-wide settings: SMTP, password policy, brute-force thresholds, events configuration, SSL, user-profile schema | Environment configuration, set in the realm file. Token and session lifetimes, which do change operationally, are exposed. |
| Realm import and export, partial import | Bootstrap and backup tooling, not an API operation. |
| User impersonation | Not requested, and a strong privilege best left unavailable until someone needs it. |
| Offline tokens, client-initiated logout URLs, back-channel logout configuration | No consumer needs them yet. |
| User sorting other than by username | Keycloak's user search offers no other order. |

## Consequences

- When one of these becomes routine, it is added behind the existing ports; none needs a new architectural seam.
- The Keycloak console remains available for debugging only.
