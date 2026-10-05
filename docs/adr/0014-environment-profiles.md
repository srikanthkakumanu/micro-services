# ADR 0014: Configuration is split by environment

- Status: accepted
- Date: 2026-10-06
- Supersedes the `docker` and `k8s` profiles used until then

## Context

Configuration was split by where a service runs: a base file plus `-docker` and `-k8s`. The owner asked for it to be split by environment instead: separate files for `dev`, `qa` and `prod`.

## Decision

Every configuration has four files: `<name>.yml`, `<name>-dev.yml`, `<name>-qa.yml`, `<name>-prod.yml`. This applies to the config repository (`service-configs`: `application`, `user-service`, `auth-service`, `api-gateway`) and to the `application.yml` bundled in each of the five services.

- **Base** holds only what is the same everywhere: names, ports, the realm, token validation rules, gateway routes.
- **`dev`** holds local values, each overridable by an environment variable. One file therefore serves a service run on the host, the Compose stack and the Kubernetes dev overlay.
- **`qa` and `prod`** take every address from a required variable with no default. A missing setting stops the service at startup; it can never fall back to a development value.
- With no profile set, `dev` is used (`spring.profiles.default`).

Where a service runs is no longer a profile. Compose and Kubernetes pass the in-network addresses as variables: `CONFIG_SERVER_URL`, `KEYCLOAK_URL`, `KEYCLOAK_PUBLIC_URL`, `EUREKA_URL`, `DATABASE_URL`, `VAULT_URI`, `CORS_ALLOWED_ORIGINS`.

What differs between the environments today:

| | dev | qa | prod |
| --- | --- | --- | --- |
| Addresses | local defaults | required variables | required variables |
| Config Server and Vault imports | optional | required | required |
| JWKS cache lifetime | 15 s | 5 min | 5 min |
| API docs and Swagger UI | on | on | off |
| Service log level | DEBUG | INFO | INFO |
| Config Server file source | directory | Git (`CONFIG_REPO_URI`, `CONFIG_REPO_LABEL`) | Git |
| Eureka self-preservation | off | on | on |

**Compose:** `.env.dev.example`, `.env.qa.example` and `.env.prod.example` each set `ENVIRONMENT`; `make up ENV=qa` generates `.env` from the matching file. Switching environment needs `make reset`, because the generated secrets belong to the data volumes.

**Kubernetes:** the base reads `SPRING_PROFILES_ACTIVE` and the addresses from the overlay's `platform-settings` ConfigMap. There is one overlay per environment: `k8s/overlays/dev`, `qa` and `prod`.

## Consequences

- Running `qa` or `prod` locally uses the same local infrastructure as `dev` (dev-mode Vault, Mailpit, one Keycloak). The profiles select configuration; they do not make the local stack a hardened environment.
- Locally the Config Server cannot use its Git source, because Spring Cloud Config checks a local repository out in place and the checkout is mounted read-only. The `qa` and `prod` env files therefore set `CONFIG_REPO_TYPE=native`, which makes it read the mounted directory as in `dev`.
- The `qa` and `prod` Kubernetes overlays carry placeholder host names and a placeholder Git remote. They render and validate but have not been deployed.
- The end-to-end suite runs against `dev`. Its key-rotation scenario relies on the 15-second JWKS cache; with the 5-minute value of `qa` and `prod` it would have to wait that long.
