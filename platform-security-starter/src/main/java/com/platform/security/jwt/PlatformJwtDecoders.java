package com.platform.security.jwt;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Builds decoders that verify the signature against the JWKS, accept only the allowlisted
 * algorithms and then apply {@link PlatformJwtValidators}.
 *
 * <p>Keys are cached for {@code jwk-set-cache-ttl}. An unknown {@code kid} triggers an immediate
 * re-fetch, which is what makes key rotation work without a restart; the cache expiring is what
 * makes a retired key stop being trusted. The servlet and reactive decoders share this behaviour.
 */
public final class PlatformJwtDecoders {

	private PlatformJwtDecoders() {
	}

	public static JwtDecoder servlet(PlatformJwtProperties properties) {
		var decoder = new NimbusJwtDecoder(processor(properties));
		decoder.setJwtValidator(PlatformJwtValidators.create(properties));
		return decoder;
	}

	public static ReactiveJwtDecoder reactive(PlatformJwtProperties properties) {
		ConfigurableJWTProcessor<SecurityContext> processor = processor(properties);
		// Fetching keys blocks, so signature verification runs off the event loop.
		var decoder = new NimbusReactiveJwtDecoder(
				jwt -> Mono.fromCallable(() -> processor.process(jwt, null)).subscribeOn(Schedulers.boundedElastic())
						// Whatever goes wrong while verifying, the token is simply not acceptable.
						.onErrorMap(ex -> !(ex instanceof JwtException),
								ex -> new BadJwtException("The token could not be verified", ex)));
		decoder.setJwtValidator(PlatformJwtValidators.create(properties));
		return decoder;
	}

	private static ConfigurableJWTProcessor<SecurityContext> processor(PlatformJwtProperties properties) {
		Set<JWSAlgorithm> algorithms = properties.signatureAlgorithms().stream()
				.map(algorithm -> JWSAlgorithm.parse(algorithm.getName())).collect(Collectors.toSet());
		var processor = new DefaultJWTProcessor<SecurityContext>();
		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, jwkSource(properties)));
		// Claims are checked by PlatformJwtValidators, where the failures get proper error descriptions.
		processor.setJWTClaimsSetVerifier((claims, context) -> {
		});
		return processor;
	}

	private static JWKSource<SecurityContext> jwkSource(PlatformJwtProperties properties) {
		Assert.hasText(properties.jwkSetUri(), "platform.security.jwt.jwk-set-uri must be set");
		long ttl = properties.jwkSetCacheTtl().toMillis();
		Assert.isTrue(ttl >= 1000, "platform.security.jwt.jwk-set-cache-ttl must be at least one second");
		try {
			var builder = JWKSourceBuilder.create(URI.create(properties.jwkSetUri()).toURL())
					// A short TTL leaves no room for the library's refresh-ahead window, so keys are
					// simply re-fetched when the cache has expired.
					.refreshAheadCache(false)
					.cache(ttl, Math.min(ttl, 15_000));
			long minInterval = properties.jwkSetRefreshMinInterval().toMillis();
			if (minInterval > 0) {
				builder.rateLimited(minInterval);
			}
			else {
				builder.rateLimited(false);
			}
			return builder.build();
		}
		catch (MalformedURLException ex) {
			throw new IllegalArgumentException("platform.security.jwt.jwk-set-uri is not a valid URL", ex);
		}
	}
}
