package com.platform.security.autoconfigure;

import com.platform.security.jwt.PlatformAuthoritiesConverter;
import com.platform.security.jwt.PlatformJwtDecoders;
import com.platform.security.jwt.PlatformJwtProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

/**
 * Gives a service a validating decoder and the shared authorities mapping as soon as
 * {@code platform.security.jwt.jwk-set-uri} is set. The service still declares its own filter chain.
 */
@AutoConfiguration
@ConditionalOnProperty("platform.security.jwt.jwk-set-uri")
@EnableConfigurationProperties(PlatformJwtProperties.class)
public class PlatformSecurityAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	PlatformAuthoritiesConverter platformAuthoritiesConverter() {
		return new PlatformAuthoritiesConverter();
	}

	@Bean
	@ConditionalOnMissingBean
	JwtAuthenticationConverter platformJwtAuthenticationConverter(PlatformAuthoritiesConverter authorities) {
		var converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.SERVLET)
	static class ServletConfiguration {

		@Bean
		@ConditionalOnMissingBean
		JwtDecoder platformJwtDecoder(PlatformJwtProperties properties) {
			return PlatformJwtDecoders.servlet(properties);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnWebApplication(type = Type.REACTIVE)
	static class ReactiveConfiguration {

		@Bean
		@ConditionalOnMissingBean
		ReactiveJwtDecoder platformReactiveJwtDecoder(PlatformJwtProperties properties) {
			return PlatformJwtDecoders.reactive(properties);
		}

		@Bean
		@ConditionalOnMissingBean
		ReactiveJwtAuthenticationConverterAdapter platformReactiveJwtAuthenticationConverter(
				JwtAuthenticationConverter converter) {
			return new ReactiveJwtAuthenticationConverterAdapter(converter);
		}
	}
}
