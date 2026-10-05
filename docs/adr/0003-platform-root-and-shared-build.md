# ADR 0003: Platform root and shared build

- Status: accepted
- Date: 2026-10-05

## Context

Each service is its own Git repository with its own Gradle wrapper. They still need one set of versions and one implementation of token validation. Discovery showed `micro-services` already acts as the orchestration repository.

## Decision

`micro-services` is the platform root. It owns:

- `gradle/libs.versions.toml`, the version catalog (ADR 0001)
- `platform-security-starter/`, the shared security library (ADR 0011)
- Docker Compose, Kubernetes manifests, the Keycloak realm file, bootstrap scripts
- `docs/` (ADRs, progress, contracts) and the end-to-end test module

Each service's `settings.gradle` imports the catalog by relative path and adds `includeBuild '../micro-services'`. Gradle's composite build then substitutes `com.platform:platform-security-starter` with the source project, so nothing is published to a repository.

## Consequences

- The repositories must be checked out as siblings. This was already required by the old compose file.
- A service cannot be built from its own directory alone. Docker builds pass the platform root as a BuildKit named context.
- Publishing the starter and catalog to an artifact repository would remove the sibling requirement. That is a later, mechanical change: replace `includeBuild` and the `files(...)` import with coordinates.
