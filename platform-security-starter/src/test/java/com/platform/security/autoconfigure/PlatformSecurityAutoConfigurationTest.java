package com.platform.security.autoconfigure;

import com.platform.security.jwt.PlatformAuthoritiesConverter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSecurityAutoConfigurationTest {

	private static final String[] PROPERTIES = {
			"platform.security.jwt.issuer-uri=http://localhost:8080/realms/platform",
			"platform.security.jwt.jwk-set-uri=http://keycloak:8080/realms/platform/protocol/openid-connect/certs",
			"platform.security.jwt.audience=user-service" };

	private static final AutoConfigurations AUTO_CONFIGURATION = AutoConfigurations
			.of(PlatformSecurityAutoConfiguration.class);

	@Test
	void servletApplicationGetsDecoderAndConverter() {
		new WebApplicationContextRunner().withConfiguration(AUTO_CONFIGURATION).withPropertyValues(PROPERTIES)
				.run(context -> {
					assertThat(context).hasSingleBean(JwtDecoder.class);
					assertThat(context).hasSingleBean(JwtAuthenticationConverter.class);
					assertThat(context).hasSingleBean(PlatformAuthoritiesConverter.class);
					assertThat(context).doesNotHaveBean(ReactiveJwtDecoder.class);
				});
	}

	@Test
	void reactiveApplicationGetsReactiveDecoderAndConverter() {
		new ReactiveWebApplicationContextRunner().withConfiguration(AUTO_CONFIGURATION).withPropertyValues(PROPERTIES)
				.run(context -> {
					assertThat(context).hasSingleBean(ReactiveJwtDecoder.class);
					assertThat(context).hasSingleBean(ReactiveJwtAuthenticationConverterAdapter.class);
					assertThat(context).doesNotHaveBean(JwtDecoder.class);
				});
	}

	@Test
	void staysOutOfTheWayUntilConfigured() {
		new ApplicationContextRunner().withConfiguration(AUTO_CONFIGURATION)
				.run(context -> assertThat(context).doesNotHaveBean(PlatformAuthoritiesConverter.class));
	}

	@Test
	void failsFastWhenASymmetricAlgorithmIsConfigured() {
		new WebApplicationContextRunner().withConfiguration(AUTO_CONFIGURATION).withPropertyValues(PROPERTIES)
				.withPropertyValues("platform.security.jwt.algorithms=HS256")
				.run(context -> assertThat(context).hasFailed());
	}
}
