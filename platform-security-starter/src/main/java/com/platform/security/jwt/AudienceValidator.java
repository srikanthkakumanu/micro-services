package com.platform.security.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/** Rejects tokens that were not issued for this service: {@code aud} must contain its client ID. */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String audience;

	public AudienceValidator(String audience) {
		Assert.hasText(audience, "audience must not be blank");
		this.audience = audience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		var audiences = token.getAudience();
		if (audiences != null && audiences.contains(audience)) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
				"The token is not intended for audience " + audience, null));
	}
}
