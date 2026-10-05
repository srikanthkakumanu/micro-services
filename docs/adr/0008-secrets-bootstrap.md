# ADR 0008: Secrets and bootstrap

- Status: accepted
- Date: 2026-10-05

## Context

`CLAUDE.md` §9 and §10 require that no secret is committed, that services read secrets from Vault, and that we pick one way of getting client secrets into the imported realm and one way of creating database credentials.

## Decision

**Realm file.** `keycloak/platform-realm.json` contains no secrets and no passwords. Client secrets are set after import by a bootstrap script through the Admin API (the second option in §10), not by placeholders in the file.

**Where dev secrets come from.**

1. `scripts/init-env.sh` (run by `make up`) creates a git-ignored `.env` with random values: database passwords, the Keycloak console admin password, the bootstrap `PLATFORM_ADMIN` password and one Vault token per service.
2. Postgres reads the database passwords from the environment on first start (`postgres/init/01-create-databases.sh`) and creates `keycloak`, `user_db` and `auth_db`, each with its own owning user.
3. The one-shot `bootstrap` container runs `scripts/vault-bootstrap.sh`: KV v2 at `secret/`, the secrets below, a read-only policy and a policy-scoped token per service. It generates the three platform client secrets itself; they never exist outside Vault and Keycloak.
4. The same container then runs `scripts/keycloak-bootstrap.sh`, which reads from Vault and sets the client secrets and the `platform-admin` password in Keycloak.

| Vault path | Contents | Read by |
| --- | --- | --- |
| `secret/user-service` | datasource username and password, Keycloak client secret | user-service |
| `secret/auth-service` | datasource username and password, Keycloak client secret | auth-service |
| `secret/api-gateway` | Keycloak client secret | api-gateway |
| `secret/application` | shared values (none sensitive yet) | all services |
| `secret/keycloak` | Keycloak console admin and `platform-admin` credentials | bootstrap, people debugging |
| `secret/clients/<client-id>` | secrets of service clients registered through the API | written by auth-service |

Keys are Spring property names (`spring.datasource.password`, `platform.keycloak.client-secret`), so Spring Cloud Vault binds them with no mapping code.

**Vault auth.** Token auth. Services get their own token, limited to their own path; the dev root token is used only by the bootstrap container and is the one labelled dev-only value in `.env.example`.

## Consequences

- `make reset` deletes `.env` and the volumes together, so secrets and data never drift apart.
- Vault runs in dev mode (in memory). After a Vault restart, run the bootstrap again; it keeps nothing but is idempotent against Keycloak.
- Hardening is configuration: real Vault storage, AppRole or Kubernetes auth instead of fixed tokens, and secrets supplied by an operator instead of `init-env.sh`.
