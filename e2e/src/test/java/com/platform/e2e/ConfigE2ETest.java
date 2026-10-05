package com.platform.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

/**
 * Each service runs with its environment profile, takes its configuration from the config server
 * and its secrets from Vault. A service only reports ready once it has its database credentials
 * and its identity-provider client secret, and neither exists anywhere but in Vault.
 */
class ConfigE2ETest {

	private static final String CONFIG_SERVER = System.getenv().getOrDefault("E2E_CONFIG_SERVER_URL", "http://localhost:9311");
	private static final String PROFILE = System.getenv().getOrDefault("E2E_PROFILE", "dev");

	@Test
	void theConfigServerServesSharedServiceAndProfileConfigurationWithoutSecrets() {
		for (String service : new String[] { "user-service", "auth-service", "api-gateway" }) {
			String body = RestAssured.given().baseUri(CONFIG_SERVER).get("/" + service + "/" + PROFILE).then().statusCode(200)
					.body("propertySources.name", hasItems("file:/config-repo/" + service + ".yml",
							"file:/config-repo/application-" + PROFILE + ".yml", "file:/config-repo/application.yml"))
					.extract().asString();
			assertThat(body).doesNotContainIgnoringCase("password").doesNotContainIgnoringCase("client-secret");
		}
	}

	@Test
	void everyServiceIsReadyAndUsesItsSecrets() {
		for (String url : new String[] { Platform.GATEWAY, Platform.USER_SERVICE, Platform.AUTH_SERVICE }) {
			RestAssured.given().baseUri(url).get("/actuator/health/readiness").then().statusCode(200).body("status", equalTo("UP"));
		}
		// Reading users needs user-service's database and its admin client secret; listing roles
		// needs auth-service's; writing a client secret needs its Vault token.
		String admin = Platform.adminToken();
		Platform.api(admin).queryParam("size", 1).get("/api/v1/users").then().statusCode(200);
		Platform.api(admin).get("/api/v1/users/me").then().statusCode(200);
		Platform.api(admin).get("/api/v1/roles").then().statusCode(200);
		Platform.api(admin).queryParam("size", 1).get("/api/v1/audit/login-events").then().statusCode(200);
	}
}
