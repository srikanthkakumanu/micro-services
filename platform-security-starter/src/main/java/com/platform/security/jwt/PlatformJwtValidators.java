package com.platform.security.jwt;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.Assert;

/** The claim checks every platform service applies after the signature has been verified. */
public final class PlatformJwtValidators {

	private PlatformJwtValidators() {
	}

	public static OAuth2TokenValidator<Jwt> create(PlatformJwtProperties properties) {
		Assert.hasText(properties.issuerUri(), "platform.security.jwt.issuer-uri must be set");
		Assert.hasText(properties.audience(), "platform.security.jwt.audience must be set");
		return new DelegatingOAuth2TokenValidator<>(
				new JwtTimestampValidator(properties.clockSkew()),
				new JwtIssuerValidator(properties.issuerUri()),
				new AudienceValidator(properties.audience()),
				new TokenTypeValidator(properties.requiredType()));
	}
}
