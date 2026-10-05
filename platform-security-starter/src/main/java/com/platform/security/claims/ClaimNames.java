package com.platform.security.claims;

/** Names of the claims that make up the platform access token contract (docs/jwt-contract.md). */
public final class ClaimNames {

	public static final String ISSUER = "iss";
	public static final String SUBJECT = "sub";
	public static final String AUDIENCE = "aud";
	public static final String EXPIRES_AT = "exp";
	public static final String ISSUED_AT = "iat";
	public static final String NOT_BEFORE = "nbf";
	public static final String TOKEN_ID = "jti";
	public static final String AUTHORIZED_PARTY = "azp";
	public static final String SESSION_ID = "sid";
	public static final String TYPE = "typ";
	public static final String PREFERRED_USERNAME = "preferred_username";
	public static final String EMAIL = "email";
	public static final String EMAIL_VERIFIED = "email_verified";
	public static final String NAME = "name";
	public static final String REALM_ACCESS = "realm_access";
	public static final String ROLES = "roles";
	public static final String PERMISSIONS = "permissions";
	public static final String GROUPS = "groups";

	private ClaimNames() {
	}
}
