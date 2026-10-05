package com.platform.security.jwt;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAuthoritiesConverterTest {

	private final PlatformAuthoritiesConverter converter = new PlatformAuthoritiesConverter();

	@Test
	void mapsRealmRolesWithPrefixAndPermissionsAsTheyAre() {
		var jwt = Jwt.withTokenValue("token").header("alg", "RS256")
				.claim("realm_access", Map.of("roles", List.of("USER", "USER_ADMIN")))
				.claim("permissions", List.of("users:read", "users:write"))
				.build();

		assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
				.containsExactlyInAnyOrder("ROLE_USER", "ROLE_USER_ADMIN", "users:read", "users:write");
	}

	@Test
	void grantsNothingWhenTheTokenCarriesNoAccessClaims() {
		var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("someone").build();

		assertThat(converter.convert(jwt)).isEmpty();
	}

	@Test
	void ignoresScopesAndClientRoles() {
		var jwt = Jwt.withTokenValue("token").header("alg", "RS256")
				.claim("scope", "openid profile")
				.claim("resource_access", Map.of("user-service", Map.of("roles", List.of("users:delete"))))
				.build();

		assertThat(converter.convert(jwt)).isEmpty();
	}
}
