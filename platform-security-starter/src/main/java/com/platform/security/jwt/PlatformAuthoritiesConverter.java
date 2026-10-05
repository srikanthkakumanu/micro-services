package com.platform.security.jwt;

import java.util.Collection;
import java.util.LinkedHashSet;

import com.platform.security.claims.AccessTokenClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The one mapping from token to Spring authorities: realm roles become {@code ROLE_<name>} and
 * permissions are used as they are, so {@code hasRole('USER_ADMIN')} and
 * {@code hasAuthority('users:read')} both work.
 */
public final class PlatformAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	public static final String ROLE_PREFIX = "ROLE_";

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		var claims = AccessTokenClaims.from(jwt.getClaims());
		var authorities = new LinkedHashSet<GrantedAuthority>();
		claims.realmRoles().forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
		claims.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
		return authorities;
	}
}
