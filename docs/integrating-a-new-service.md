# Integrating a new service

How `books-service`, or any other service, joins the platform. Everything is done through the APIs; nothing here needs the Keycloak console or an edit to the realm file. The end-to-end test `ServiceE2ETest` does exactly this for a `sample-service`.

All calls go through the gateway (`http://localhost:9211` in Compose) with a token that holds the permission named in each step. `PLATFORM_ADMIN` holds them all.

## 1. Register the client

Needs `clients:manage`.

```http
POST /api/v1/clients
{"clientId": "books-service", "name": "Books", "audiences": ["user-service"]}
```

`audiences` lists the services this one will call. The response contains `secretLocation` (`secret/clients/books-service`), not the secret: read it from Vault and give it to the service the same way its other secrets arrive.

Registration also adds the service to the audience of platform user tokens and makes its permissions flow into the `permissions` claim.

## 2. Define its permissions and grant them

Needs `permissions:manage`.

```http
POST /api/v1/permissions
{"service": "books-service", "name": "books:write", "description": "Create and change books"}

PUT /api/v1/roles/EDITOR/permissions/books-service/books:write
```

Create the role first with `POST /api/v1/roles` (`roles:manage`) if it does not exist. Users who hold the role get `books:write` in their next token.

To let the service itself call another service with a permission, give it to its service account:

```http
POST /api/v1/clients/books-service/roles
{"roles": [{"name": "users:read", "clientId": "user-service"}]}
```

## 3. Validate tokens in the service

Add the starter and three properties. The starter gives you a decoder that applies every check in [jwt-contract.md](jwt-contract.md) and the shared authority mapping.

```groovy
// settings.gradle
dependencyResolutionManagement {
	versionCatalogs { libs { from(files('../micro-services/gradle/libs.versions.toml')) } }
}
includeBuild '../micro-services'

// build.gradle
implementation libs.platform.security.starter
implementation 'org.springframework.boot:spring-boot-starter-security-oauth2-resource-server'
```

```yaml
platform:
  security:
    jwt:
      issuer-uri: http://localhost:8080/realms/platform   # shared; already in service-configs/application.yml
      jwk-set-uri: ${platform.keycloak.server-url}/realms/platform/protocol/openid-connect/certs
      audience: books-service                              # your own client ID
```

```java
@Configuration
@EnableMethodSecurity
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
				.oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.build();
	}
}

@PreAuthorize("hasAuthority('books:write')")
@PostMapping("/api/v1/books")
BookResponse create(@AuthenticationPrincipal Jwt jwt, ...) {
	var caller = AccessTokenClaims.from(jwt.getClaims());   // typed claims, no string lookups
	...
}
```

Validate the token yourself even though the gateway already did. Do not trust identity headers.

## 4. Add a gateway route

In `api-gateway`'s `application.yml`, copy an existing block. Put anything more specific than an existing pattern above it.

```yaml
- id: books-service
  uri: lb://books-service
  predicates:
    - Path=/api/v1/books,/api/v1/books/**
```

Register the service with Eureka under the same name, and add `books-service.yml` (plus `-docker` and `-k8s`) to `service-configs` for its non-secret settings.

## 5. Decisions a token cannot make

A token says what a user may do in general. For "may this user change *this* review", ask:

```http
POST /api/v1/authz/decisions
{"subject": "<user id>", "action": "update", "resource": {"type": "reviews", "id": "7", "ownerId": "<owner id>"}}
→ {"effect": "ALLOW", "allowed": true, "reason": "owner"}
```

Rules, first match wins:

1. The subject holds `<type>:<action>` → allow (`permission`).
2. The subject is `ownerId` and the action is `read`, `write`, `update` or `delete` → allow (`owner`).
3. Otherwise deny (`not-granted`).

Send `{"requests": [...]}` (up to 100) to get `{"decisions": [...]}` in the same order. A person may only ask about themselves; a service may ask about anyone. Permissions are read fresh, not from the token.

## 6. Service-to-service calls

Services call each other directly through Eureka, not through the gateway.

- **As itself:** `POST /api/v1/auth/service-token` with `clientId` and `clientSecret` returns a client-credentials token whose `aud` is the client's registered audiences and whose `permissions` are those of its service account.
- **On behalf of a user:** `POST /api/v1/tokens/exchange` with `clientId`, `clientSecret`, the user's `subjectToken` and the target `audience`. The result keeps the user as `sub` and names only that audience. The target must be one of the client's registered audiences.

## 7. Rotate the secret

`POST /api/v1/clients/books-service/rotate-secret` writes a new secret to the same Vault location. The old one stops working immediately, so the service must pick the new one up.
