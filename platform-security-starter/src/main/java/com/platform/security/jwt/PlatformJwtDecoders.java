package com.platform.security.jwt;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.Assert;

/**
 * Builds decoders that verify the signature against the cached JWKS (re-fetched when an unknown
 * {@code kid} appears, which is what makes key rotation work without a restart), accept only the
 * allowlisted algorithms and then apply {@link PlatformJwtValidators}.
 */
public final class PlatformJwtDecoders {

	private PlatformJwtDecoders() {
	}

	public static JwtDecoder servlet(PlatformJwtProperties properties) {
		Assert.hasText(properties.jwkSetUri(), "platform.security.jwt.jwk-set-uri must be set");
		var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
				.jwsAlgorithms(algorithms -> algorithms.addAll(properties.signatureAlgorithms()))
				.build();
		decoder.setJwtValidator(PlatformJwtValidators.create(properties));
		return decoder;
	}

	public static ReactiveJwtDecoder reactive(PlatformJwtProperties properties) {
		Assert.hasText(properties.jwkSetUri(), "platform.security.jwt.jwk-set-uri must be set");
		var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri())
				.jwsAlgorithms(algorithms -> algorithms.addAll(properties.signatureAlgorithms()))
				.build();
		decoder.setJwtValidator(PlatformJwtValidators.create(properties));
		return decoder;
	}
}
