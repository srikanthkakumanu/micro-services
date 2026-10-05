package com.platform.security.claims;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccessTokenClaimsTest {

	@Test
	void readsEveryContractClaim() {
		var claims = new HashMap<String, Object>();
		claims.put("iss", "http://localhost:8080/realms/platform");
		claims.put("sub", "7c1f");
		claims.put("aud", List.of("user-service", "auth-service"));
		claims.put("exp", Instant.ofEpochSecond(2_000));
		claims.put("iat", 1_700L);
		claims.put("nbf", new Date(1_700_000L));
		claims.put("jti", "token-1");
		claims.put("azp", "frontend");
		claims.put("sid", "session-1");
		claims.put("typ", "Bearer");
		claims.put("preferred_username", "alice");
		claims.put("email", "alice@example.com");
		claims.put("email_verified", true);
		claims.put("name", "Alice Doe");
		claims.put("realm_access", Map.of("roles", List.of("USER", "USER_ADMIN")));
		claims.put("permissions", List.of("users:read", "users:write"));
		claims.put("groups", List.of("/engineering/platform"));

		var token = AccessTokenClaims.from(claims);

		assertThat(token.issuer()).isEqualTo("http://localhost:8080/realms/platform");
		assertThat(token.subject()).isEqualTo("7c1f");
		assertThat(token.audience()).containsExactly("user-service", "auth-service");
		assertThat(token.expiresAt()).isEqualTo(Instant.ofEpochSecond(2_000));
		assertThat(token.issuedAt()).isEqualTo(Instant.ofEpochSecond(1_700));
		assertThat(token.notBefore()).isEqualTo(Instant.ofEpochSecond(1_700));
		assertThat(token.tokenId()).isEqualTo("token-1");
		assertThat(token.authorizedParty()).isEqualTo("frontend");
		assertThat(token.sessionId()).isEqualTo("session-1");
		assertThat(token.type()).isEqualTo("Bearer");
		assertThat(token.preferredUsername()).isEqualTo("alice");
		assertThat(token.email()).isEqualTo("alice@example.com");
		assertThat(token.emailVerified()).isTrue();
		assertThat(token.name()).isEqualTo("Alice Doe");
		assertThat(token.realmRoles()).containsExactlyInAnyOrder("USER", "USER_ADMIN");
		assertThat(token.permissions()).containsExactlyInAnyOrder("users:read", "users:write");
		assertThat(token.groups()).containsExactly("/engineering/platform");
	}

	@Test
	void answersRolePermissionAndAudienceQuestions() {
		var token = AccessTokenClaims.from(Map.of(
				"aud", "user-service",
				"realm_access", Map.of("roles", List.of("USER")),
				"permissions", List.of("users:read")));

		assertThat(token.hasRole("USER")).isTrue();
		assertThat(token.hasRole("PLATFORM_ADMIN")).isFalse();
		assertThat(token.hasPermission("users:read")).isTrue();
		assertThat(token.hasPermission("users:write")).isFalse();
		assertThat(token.isIntendedFor("user-service")).isTrue();
		assertThat(token.isIntendedFor("auth-service")).isFalse();
	}

	@Test
	void treatsMissingAndMalformedClaimsAsEmpty() {
		var token = AccessTokenClaims.from(Map.of(
				"realm_access", "not-a-map",
				"permissions", List.of("users:read", 42),
				"exp", "not-a-time"));

		assertThat(token.subject()).isNull();
		assertThat(token.audience()).isEmpty();
		assertThat(token.realmRoles()).isEmpty();
		assertThat(token.permissions()).containsExactly("users:read");
		assertThat(token.groups()).isEmpty();
		assertThat(token.expiresAt()).isNull();
		assertThat(token.emailVerified()).isFalse();
	}

	@Test
	void requiresClaims() {
		assertThatNullPointerException().isThrownBy(() -> AccessTokenClaims.from(null));
	}
}
