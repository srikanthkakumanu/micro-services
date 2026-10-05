package com.platform.security.jwt;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

/**
 * How this service validates platform access tokens.
 *
 * @param issuerUri exact expected {@code iss}; the public Keycloak realm URL
 * @param jwkSetUri where to fetch signing keys; may be an internal address that differs from the issuer
 * @param audience this service's client ID, which must be present in {@code aud}
 * @param clockSkew tolerance applied to {@code exp} and {@code nbf}
 * @param algorithms allowed asymmetric signature algorithms
 * @param requiredType expected {@code typ} claim
 * @param jwkSetCacheTtl how long fetched keys are trusted before they are fetched again; this bounds
 * how long tokens signed by a retired key are still accepted
 * @param jwkSetRefreshMinInterval shortest time between two fetches triggered by unknown key IDs,
 * so a flood of forged tokens cannot make the service hammer the identity provider; zero disables it
 */
@ConfigurationProperties("platform.security.jwt")
public record PlatformJwtProperties(
		String issuerUri,
		String jwkSetUri,
		String audience,
		@DefaultValue("30s") Duration clockSkew,
		@DefaultValue("RS256") List<String> algorithms,
		@DefaultValue("Bearer") String requiredType,
		@DefaultValue("5m") Duration jwkSetCacheTtl,
		@DefaultValue("5s") Duration jwkSetRefreshMinInterval) {

	/** Resolves the allowlist; symmetric algorithms and {@code none} can never be configured. */
	public List<SignatureAlgorithm> signatureAlgorithms() {
		if (algorithms.isEmpty()) {
			throw new IllegalStateException("platform.security.jwt.algorithms must not be empty");
		}
		return algorithms.stream().map(PlatformJwtProperties::asymmetric).toList();
	}

	private static SignatureAlgorithm asymmetric(String name) {
		var algorithm = SignatureAlgorithm.from(name.toUpperCase(Locale.ROOT));
		if (algorithm == null) {
			throw new IllegalStateException(
					"platform.security.jwt.algorithms accepts asymmetric signature algorithms only, got: " + name);
		}
		return algorithm;
	}
}
