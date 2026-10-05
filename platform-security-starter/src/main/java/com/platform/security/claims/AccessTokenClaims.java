package com.platform.security.claims;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Typed view of a platform access token. Build it once from the raw claims so services do not
 * read claims by string.
 */
public record AccessTokenClaims(
		String issuer,
		String subject,
		List<String> audience,
		Instant expiresAt,
		Instant issuedAt,
		Instant notBefore,
		String tokenId,
		String authorizedParty,
		String sessionId,
		String type,
		String preferredUsername,
		String email,
		boolean emailVerified,
		String name,
		Set<String> realmRoles,
		Set<String> permissions,
		Set<String> groups) {

	public AccessTokenClaims {
		audience = List.copyOf(audience);
		realmRoles = Set.copyOf(realmRoles);
		permissions = Set.copyOf(permissions);
		groups = Set.copyOf(groups);
	}

	public static AccessTokenClaims from(Map<String, Object> claims) {
		Objects.requireNonNull(claims, "claims");
		return new AccessTokenClaims(
				string(claims, ClaimNames.ISSUER),
				string(claims, ClaimNames.SUBJECT),
				List.copyOf(strings(claims.get(ClaimNames.AUDIENCE))),
				instant(claims.get(ClaimNames.EXPIRES_AT)),
				instant(claims.get(ClaimNames.ISSUED_AT)),
				instant(claims.get(ClaimNames.NOT_BEFORE)),
				string(claims, ClaimNames.TOKEN_ID),
				string(claims, ClaimNames.AUTHORIZED_PARTY),
				string(claims, ClaimNames.SESSION_ID),
				string(claims, ClaimNames.TYPE),
				string(claims, ClaimNames.PREFERRED_USERNAME),
				string(claims, ClaimNames.EMAIL),
				Boolean.TRUE.equals(claims.get(ClaimNames.EMAIL_VERIFIED)),
				string(claims, ClaimNames.NAME),
				realmRoles(claims),
				strings(claims.get(ClaimNames.PERMISSIONS)),
				strings(claims.get(ClaimNames.GROUPS)));
	}

	public boolean hasRole(String role) {
		return realmRoles.contains(role);
	}

	public boolean hasPermission(String permission) {
		return permissions.contains(permission);
	}

	public boolean isIntendedFor(String clientId) {
		return audience.contains(clientId);
	}

	private static Set<String> realmRoles(Map<String, Object> claims) {
		if (claims.get(ClaimNames.REALM_ACCESS) instanceof Map<?, ?> realmAccess) {
			return strings(realmAccess.get(ClaimNames.ROLES));
		}
		return Set.of();
	}

	private static String string(Map<String, Object> claims, String name) {
		Object value = claims.get(name);
		return value == null ? null : value.toString();
	}

	/** A claim may be a single string (a one-element {@code aud}) or a collection of strings. */
	private static Set<String> strings(Object value) {
		var result = new LinkedHashSet<String>();
		if (value instanceof String single) {
			result.add(single);
		}
		else if (value instanceof Collection<?> many) {
			many.stream().filter(String.class::isInstance).map(String.class::cast).forEach(result::add);
		}
		return result;
	}

	private static Instant instant(Object value) {
		return switch (value) {
			case Instant instant -> instant;
			case Date date -> date.toInstant();
			case Number seconds -> Instant.ofEpochSecond(seconds.longValue());
			case null, default -> null;
		};
	}
}
