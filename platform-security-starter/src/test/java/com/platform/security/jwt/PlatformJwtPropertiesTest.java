package com.platform.security.jwt;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PlatformJwtPropertiesTest {

	@Test
	void resolvesAsymmetricAlgorithms() {
		assertThat(properties(List.of("RS256", "ps256", "ES256")).signatureAlgorithms())
				.containsExactly(SignatureAlgorithm.RS256, SignatureAlgorithm.PS256, SignatureAlgorithm.ES256);
	}

	@ParameterizedTest
	@ValueSource(strings = { "HS256", "HS384", "HS512", "none", "bogus" })
	void refusesSymmetricUnsignedAndUnknownAlgorithms(String algorithm) {
		assertThatIllegalStateException().isThrownBy(() -> properties(List.of(algorithm)).signatureAlgorithms())
				.withMessageContaining(algorithm);
	}

	@Test
	void refusesAnEmptyAllowlist() {
		assertThatIllegalStateException().isThrownBy(() -> properties(List.of()).signatureAlgorithms());
	}

	private static PlatformJwtProperties properties(List<String> algorithms) {
		return new PlatformJwtProperties("http://issuer", "http://issuer/certs", "user-service",
				Duration.ofSeconds(30), algorithms, "Bearer", Duration.ofSeconds(1), Duration.ZERO);
	}
}
