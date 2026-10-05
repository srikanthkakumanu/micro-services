package com.platform.security.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PlatformJwtDecodersTest {

	private static final Date ISSUED_EARLIER = Date.from(Instant.now().minus(Duration.ofMinutes(10)));

	private TestIdentityProvider idp;
	private JwtDecoder decoder;

	@BeforeEach
	void start() {
		idp = new TestIdentityProvider();
		decoder = PlatformJwtDecoders.servlet(properties());
	}

	@AfterEach
	void stop() {
		idp.close();
	}

	@Test
	void acceptsAValidToken() {
		var jwt = decoder.decode(idp.validToken());

		assertThat(jwt.getSubject()).isEqualTo("7c1f");
		assertThat(jwt.getAudience()).contains(TestIdentityProvider.AUDIENCE);
	}

	@Test
	void rejectsATamperedSignature() {
		assertRejected(TestIdentityProvider.tamper(idp.validToken()));
	}

	@Test
	void rejectsATokenSignedByAnUnpublishedKey() {
		assertRejected(idp.tokenSignedByUnknownKey());
	}

	@Test
	void rejectsAnUnsignedToken() {
		assertRejected(idp.unsignedToken());
	}

	@Test
	void rejectsASymmetricallySignedToken() {
		assertRejected(idp.hmacSignedToken());
	}

	@Test
	void rejectsAnExpiredToken() {
		var expired = Date.from(Instant.now().minus(Duration.ofMinutes(2)));

		assertInvalid(idp.token(claims -> claims.issueTime(ISSUED_EARLIER).expirationTime(expired)), "expired");
	}

	@Test
	void rejectsATokenThatIsNotValidYet() {
		var future = Date.from(Instant.now().plus(Duration.ofMinutes(2)));

		assertInvalid(idp.token(claims -> claims.notBeforeTime(future)), "before");
	}

	@Test
	void toleratesTheConfiguredClockSkew() {
		var justExpired = Date.from(Instant.now().minus(Duration.ofSeconds(10)));
		var almostValid = Date.from(Instant.now().plus(Duration.ofSeconds(10)));

		decoder.decode(idp.token(claims -> claims.issueTime(ISSUED_EARLIER).expirationTime(justExpired)));
		decoder.decode(idp.token(claims -> claims.notBeforeTime(almostValid)));
	}

	@Test
	void rejectsAnotherIssuer() {
		assertInvalid(idp.token(claims -> claims.issuer("http://keycloak:8080/realms/platform")), "iss");
	}

	@Test
	void rejectsATokenMeantForAnotherService() {
		assertInvalid(idp.token(claims -> claims.audience(List.of("auth-service", "account"))), "audience");
	}

	@Test
	void rejectsATokenWithoutAnAudience() {
		assertInvalid(idp.token(claims -> claims.audience((String) null)), "audience");
	}

	@Test
	void rejectsRefreshAndIdTokens() {
		assertInvalid(idp.token(claims -> claims.claim("typ", "Refresh")), "type");
		assertInvalid(idp.token(claims -> claims.claim("typ", "ID")), "type");
		assertInvalid(idp.token(claims -> claims.claim("typ", null)), "type");
	}

	@Test
	void picksUpARotatedKeyWithoutRestartAndStillAcceptsThePassiveOne() {
		String oldToken = idp.validToken();
		decoder.decode(oldToken);
		int requestsBeforeRotation = idp.jwksRequests();

		idp.rotateSigningKey();
		var jwt = decoder.decode(idp.validToken());

		assertThat(jwt.getHeaders()).containsEntry("kid", idp.currentKeyId());
		assertThat(idp.jwksRequests()).isGreaterThan(requestsBeforeRotation);
		decoder.decode(oldToken);
	}

	@Test
	void reactiveDecoderAppliesTheSameRules() {
		ReactiveJwtDecoder reactive = PlatformJwtDecoders.reactive(properties());

		assertThat(reactive.decode(idp.validToken()).block().getSubject()).isEqualTo("7c1f");
		assertThatExceptionOfType(JwtException.class)
				.isThrownBy(() -> reactive.decode(idp.hmacSignedToken()).block());
		assertThatExceptionOfType(JwtException.class)
				.isThrownBy(() -> reactive.decode(idp.unsignedToken()).block());
		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(
				() -> reactive.decode(idp.token(claims -> claims.audience("auth-service"))).block());
	}

	@Test
	void requiresIssuerAudienceAndJwkSetUri() {
		assertThatIllegalArgumentException().isThrownBy(() -> PlatformJwtDecoders.servlet(
				new PlatformJwtProperties(null, idp.jwkSetUri(), "user-service", Duration.ZERO, List.of("RS256"), "Bearer")));
		assertThatIllegalArgumentException().isThrownBy(() -> PlatformJwtDecoders.servlet(
				new PlatformJwtProperties("http://issuer", idp.jwkSetUri(), " ", Duration.ZERO, List.of("RS256"), "Bearer")));
		assertThatIllegalArgumentException().isThrownBy(() -> PlatformJwtDecoders.reactive(
				new PlatformJwtProperties("http://issuer", null, "user-service", Duration.ZERO, List.of("RS256"), "Bearer")));
	}

	private void assertRejected(String token) {
		assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(token));
	}

	private void assertInvalid(String token, String reason) {
		assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token))
				.withMessageContaining(reason);
	}

	private PlatformJwtProperties properties() {
		return new PlatformJwtProperties(TestIdentityProvider.ISSUER, idp.jwkSetUri(),
				TestIdentityProvider.AUDIENCE, Duration.ofSeconds(30), List.of("RS256"), "Bearer");
	}
}
