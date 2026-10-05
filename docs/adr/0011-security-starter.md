# ADR 0011: Shared security starter

- Status: accepted
- Date: 2026-10-05

## Context

The gateway and every service must validate tokens identically (`CLAUDE.md` §10a) and map roles and permissions to authorities with one converter. Future services must be able to do the same in a few lines (§12). The previous code had three slightly different hand-written converters.

## Decision

Build a small Spring Boot starter now, `platform-security-starter`, rather than a documented snippet. Three consumers exist from day one and drift between them is a security bug.

It contains:

- `AccessTokenClaims` and `ClaimNames`: plain Java value objects for the token contract.
- `PlatformJwtDecoders`: servlet and reactive decoders. Signature is checked against the JWKS, which is cached and re-fetched when an unknown `kid` appears. Only allowlisted asymmetric algorithms are accepted.
- `PlatformJwtValidators`: exact `iss`, own client ID in `aud`, `exp`/`nbf` with configurable clock skew, `typ` equal to `Bearer`.
- `PlatformAuthoritiesConverter`: realm roles become `ROLE_<name>`, permissions are used as they are.
- Auto-configuration that activates when `platform.security.jwt.jwk-set-uri` is set.

A service configures:

```yaml
platform:
  security:
    jwt:
      issuer-uri: http://localhost:8080/realms/platform      # exact iss, the public URL
      jwk-set-uri: http://keycloak:8080/realms/platform/protocol/openid-connect/certs
      audience: user-service                                  # its own client ID
```

`issuer-uri` and `jwk-set-uri` are separate on purpose: the issuer is the public URL in every environment, while keys are fetched over the internal network. That is what keeps `iss` identical inside and outside Docker and Kubernetes.

## Consequences

- The starter does not define a filter chain. Each service keeps its own public-path rules.
- Scopes and Keycloak client roles in `resource_access` are deliberately not mapped; permissions reach services only through the flat `permissions` claim.
