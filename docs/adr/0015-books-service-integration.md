# ADR 0015: books-service joins the platform; every database credential lives in Vault

- Status: accepted
- Date: 2026-10-06
- Extends ADR 0004 (permission model), ADR 0007 (least privilege), ADR 0008 (secrets bootstrap)

## Context

`books-service` is the first business service on the platform. Before this it expected a realm, an audience and roles (`ADMIN`, `MANAGER`) that do not exist, ran its own Postgres and Keycloak, and carried credentials in its files. The owner asked for it to be rebuilt on domain-driven design and clean architecture, to use the users and roles that `user-service` and `auth-service` manage, to require a login and a proper role, and for every database user name and password to be stored in Vault and read from there. The owner also fixed a few development credentials.

## Decision

### Users and roles come from the platform

- **Login.** Nothing in books-service is usable without a platform access token obtained from `auth-service`. Only the health probes and the API description are open. The gateway validates the token and books-service validates it again, with the shared `platform-security-starter`.
- **Users.** books-service stores no user data. A book holds only `owner_id`, the platform user ID that tokens carry as `sub`. When a book is added for, or transferred to, another user, books-service asks `user-service` whether that user exists and is active (`GET /api/v1/users/{id}`), using its own client-credentials token from `auth-service`. This sits behind `UserDirectoryPort`.
- **Roles.** books-service defines no roles and has no role logic. Being logged in is not enough: the default `USER` role grants nothing in the catalog. The roles are created and assigned in `auth-service`:

| Role | Permissions it grants | May |
| --- | --- | --- |
| `CATALOG_READER` | `books:read` | read books and authors |
| `CATALOG_EDITOR` | `books:read`, `books:write` | also add books and change or remove their own |
| `CATALOG_MANAGER` | all four | also change any book, transfer ownership, manage authors |
| `PLATFORM_ADMIN` | all four | everything |

- The code checks permissions (`books:read`, `books:write`, `books:manage`, `authors:manage`), never role names, as ADR 0004 prescribes. They are client roles of the `books-service` client and reach the token's `permissions` claim.
- Whether a caller may change a particular book (owner or manager) is a domain rule in the `Book` aggregate. It needs no call to the decisions endpoint, because books-service knows the owner.

### Onboarding is done through the APIs

`scripts/onboard-services.sh` reads `onboarding/books-service.json` and calls `/api/v1/clients`, `/api/v1/permissions` and `/api/v1/roles` as the platform administrator. The realm file is not touched (CLAUDE.md §10, §12). It runs as a one-shot job after the gateway is up and before books-service starts, and is safe to repeat.

The dev Vault runs in memory and loses registered client secrets on restart. The job therefore issues a new client secret when Vault no longer has one, and keeps the one it has otherwise, so a running service is not cut off.

### Every database credential lives in Vault

Every database user name and password is stored in Vault and read from there by whatever uses it. Nothing else reads one from `.env`, a compose file, a manifest or a configuration file.

| Vault path | Holds | Read by |
| --- | --- | --- |
| `secret/postgres` | superuser name and password | the secrets-fetch step for Postgres; the database job |
| `secret/keycloak-db` | database, user, password | the secrets-fetch step for Keycloak; the database job |
| `secret/user-service`, `secret/auth-service` | datasource user and password | the service; the database job |
| `secret/books-service` | runtime user (`spring.datasource.*`) and schema admin (`spring.flyway.*`) | books-service; the database job |
| `secret/video-service` | the same for `videodb` | the database job (video-service is not built yet) |

To make that possible the start order changed: **Vault first**.

1. Vault starts; the `vault-seed` job fills it.
2. `secrets-fetch` reads the Postgres and Keycloak credentials from Vault and hands them over as files (`POSTGRES_USER_FILE`, `POSTGRES_PASSWORD_FILE`; a file Keycloak's start command reads). Those two cannot talk to Vault themselves.
3. Postgres starts. The `db-init` job creates every database and user with credentials read from Vault. It replaces the Postgres init script, is safe to repeat, and sets each password to the one Vault holds.
4. Keycloak starts, then the job that applies its secrets, then the services.

The one-shot jobs after the seeding use a read-only Vault token.

**What cannot be avoided in dev:** an in-memory Vault starts empty, so the values must exist outside it once to seed it. They are in the git-ignored `.env`, and **only the `vault-seed` job reads them**. In Kubernetes the same values are in the `platform-bootstrap` Secret, created from `.env` and never committed, and again only the seeding job reads the credentials in it. A persistent or external Vault removes this; `qa` and `prod` are expected to have one.

### Database layout for books

`booksdb` keeps the service's two-account scheme: `booksadmin` owns the schema and runs Flyway; `theuser` is the runtime account, limited to reading and writing rows through default privileges. `videodb` is created the same way with `videoadmin`, ready for video-service. `user_db`, `auth_db` and `keycloak` keep one owner each.

### Fixed development credentials

The owner chose these so they are easy to remember on a development machine:

| What | Value |
| --- | --- |
| Vault dev root token | `srikanth` |
| `booksdb` admin | `booksadmin` / `booksadmin` |
| `videodb` admin | `videoadmin` / `videoadmin` |
| Runtime account of `booksdb` and `videodb` | `theuser` / `theuser` |

They are in `.env.dev.example`, marked dev-only. This is a deliberate exception to "no secrets in Git" (CLAUDE.md §9, §17), limited to the dev environment. The `qa` and `prod` templates contain no password; there the same accounts get generated ones. Every other credential stays generated.

## Consequences

- A new database or account is added in two places: the seeding script and the database job.
- `theuser` is one Postgres role with access to both `booksdb` and `videodb`, by the owner's choice. The two services can therefore read each other's rows; separate runtime accounts would prevent that.
- The fetched Postgres and Keycloak credentials sit in a Docker volume in Compose (rewritten on every start) and in memory in Kubernetes. A memory-backed Docker volume was not usable: it is emptied when the job that writes it exits.
- books-service depends on `user-service` and `auth-service` only when a book is given to another user. Reading the catalog and keeping one's own books needs neither.
- A revoked role or a logout takes effect in books-service when the access token expires or is refreshed (about five minutes), as everywhere else (ADR 0009).
- When a user is deleted their books keep the old `owner_id`; only a catalog manager can then change them. Reacting to `UserDeleted` needs a message broker, which the platform does not have.
- The memory of an 8 GB Docker Desktop is not enough for the Compose stack and the local cluster at once now that there is a fourth service; run one at a time.
