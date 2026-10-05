package com.platform.security.jwt;

import com.platform.security.claims.ClaimNames;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/** Accepts access tokens only: the {@code typ} claim must match, so refresh and ID tokens are rejected. */
public final class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

	private final String requiredType;

	public TokenTypeValidator(String requiredType) {
		Assert.hasText(requiredType, "requiredType must not be blank");
		this.requiredType = requiredType;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (requiredType.equals(token.getClaimAsString(ClaimNames.TYPE))) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
				"The token type must be " + requiredType, null));
	}
}
