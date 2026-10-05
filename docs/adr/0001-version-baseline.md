# ADR 0001: Version baseline

- Status: accepted
- Date: 2026-10-05

## Context

`CLAUDE.md` §4 asks for one mutually compatible, centrally declared set of versions, checked against current stable releases before any build file is written. It names Java 21. The owner then chose Java 27, which is installed locally and was already the level of the previous code.

## Decision

| Component | Version | Checked against |
| --- | --- | --- |
| Java (toolchain) | 27 | Local Temurin 27; owner decision |
| Gradle (wrapper, Groovy DSL) | 9.8.0 | `services.gradle.org/versions/current`; runs on JDK 27 locally |
| Spring Boot | 4.1.1 | Maven Central |
| Spring Cloud | 2025.1.3 | Maven Central; the train for Boot 4.1 |
| Keycloak server | 26.8.0 | GitHub latest release |
| Keycloak Admin Client | 26.0.12 | Maven Central; versioned separately from the server since 26.0 and compatible with 26.x servers |
| PostgreSQL | 18 | Image already used by the platform |
| Testcontainers | managed by the Spring Boot BOM (2.0.x) | Maven Central |
| testcontainers-keycloak | 4.4.0 | Maven Central |
| springdoc-openapi | 3.1.1 | Maven Central |
| MapStruct | 1.6.3 | Maven Central |
| ArchUnit | 1.5.1 | Maven Central |
| JaCoCo | 0.8.15 | Maven Central |

All versions live in `micro-services/gradle/libs.versions.toml`. Every service imports that catalog from its `settings.gradle`; no service declares a version of its own. Libraries the Spring Boot BOM manages are left to the BOM.

**Token signing:** RS256. It is Keycloak's default, every JOSE library supports it, and nothing here needs smaller signatures. PS256 and ES256 are accepted by the validation code (the allowlist is configuration), so switching is a realm key and a property change. HS256 and `none` cannot be configured.

## Consequences

- This deviates from `CLAUDE.md`'s Java 21 by owner decision. The out-of-scope services already use Java 27, so the workspace stays on one JDK.
- JaCoCo 0.8.15 lists Java 27 class-file support as experimental (official support is in the unreleased next version). It instruments and reports correctly in this build. If a tool stops reading Java 27 bytecode, compile with `--release 21` on the same toolchain rather than dropping coverage or architecture tests, and move to the next JaCoCo release when it ships.
- The Keycloak Admin Client version must be re-checked whenever the server version changes.
