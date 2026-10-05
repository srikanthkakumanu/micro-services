package com.platform.e2e;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.at;
import static com.platform.e2e.Platform.claims;
import static com.platform.e2e.Platform.permission;
import static com.platform.e2e.Platform.strings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/** Onboarding a new service entirely through the APIs, and token exchange on a user's behalf. */
class ServiceE2ETest {

	@Test
	void onboardSampleServiceThenRotateItsSecret() {
		String admin = adminToken();
		String clientId = "sample-service-" + Platform.unique("").substring(0, 6);
		String ownPermission = "samples" + Platform.unique("").substring(0, 6) + ":read";

		// Register its client; the secret is in Vault, not in the response.
		String location = api(admin).body(Map.of("clientId", clientId, "name", "Sample service", "audiences", List.of("user-service")))
				.post("/api/v1/clients").then().statusCode(201).extract().path("secretLocation");
		assertThat(location).isEqualTo("secret/clients/" + clientId);
		String secret = Platform.clientSecret(clientId);

		// Define its permission, attach it to a role, and let its service account read users.
		api(admin).body(Map.of("service", clientId, "name", ownPermission)).post("/api/v1/permissions").then().statusCode(201);
		api(admin).put("/api/v1/roles/USER_ADMIN/permissions/" + clientId + "/" + ownPermission).then().statusCode(204);
		api(admin).body(permission("user-service", "users:read")).post("/api/v1/clients/" + clientId + "/roles").then().statusCode(204);

		// Client-credentials token, then an authorized call with it. Services call each other
		// directly, not through the gateway, so the call goes straight to user-service.
		String serviceToken = api(null).body(Map.of("clientId", clientId, "clientSecret", secret))
				.post("/api/v1/auth/service-token").then().statusCode(200).extract().path("accessToken");
		assertThat(strings(claims(serviceToken).get("aud"))).contains("user-service");
		assertThat(strings(claims(serviceToken).get("permissions"))).containsExactly("users:read");
		at(Platform.USER_SERVICE, serviceToken).queryParam("size", 1).get("/api/v1/users").then().statusCode(200);
		// It was not given write access, and auth-service is not among its audiences.
		at(Platform.USER_SERVICE, serviceToken).delete("/api/v1/users/" + Platform.adminId()).then().statusCode(403);
		at(Platform.AUTH_SERVICE, serviceToken).get("/api/v1/auth/me").then().statusCode(401);

		// Rotate: the old secret fails, the new one works.
		api(admin).post("/api/v1/clients/" + clientId + "/rotate-secret").then().statusCode(200);
		String rotated = Platform.clientSecret(clientId);
		assertThat(rotated).isNotEqualTo(secret);
		api(null).body(Map.of("clientId", clientId, "clientSecret", secret)).post("/api/v1/auth/service-token").then()
				.statusCode(401).body("code", equalTo("invalid-credentials"));
		api(null).body(Map.of("clientId", clientId, "clientSecret", rotated)).post("/api/v1/auth/service-token").then().statusCode(200);

		api(admin).get("/api/v1/clients").then().body("clientId", hasItem(clientId));
		api(admin).delete("/api/v1/roles/USER_ADMIN/permissions/" + clientId + "/" + ownPermission).then().statusCode(204);
		api(admin).delete("/api/v1/clients/" + clientId).then().statusCode(204);
		assertThat(Platform.clientSecretExists(clientId)).isFalse();
	}

	@Test
	void aServiceExchangesAUserTokenForAnotherServicesAudience() {
		String admin = adminToken();
		String clientId = "exchange-" + Platform.unique("").substring(0, 8);
		api(admin).body(Map.of("clientId", clientId, "audiences", List.of("user-service"))).post("/api/v1/clients").then().statusCode(201);
		String secret = Platform.clientSecret(clientId);
		Platform.User user = Platform.newUser();
		String userToken = Platform.token(user.username());

		String exchanged = api(null).body(Map.of("clientId", clientId, "clientSecret", secret, "subjectToken", userToken,
				"audience", "user-service")).post("/api/v1/tokens/exchange").then().statusCode(200).extract().path("accessToken");

		assertThat(claims(exchanged)).containsEntry("sub", user.id()).containsEntry("azp", clientId);
		assertThat(strings(claims(exchanged).get("aud"))).containsExactly("user-service");
		// user-service accepts it as the user; a service it was not narrowed to does not.
		at(Platform.USER_SERVICE, exchanged).get("/api/v1/users/me").then().statusCode(200).body("id", equalTo(user.id()));
		at(Platform.AUTH_SERVICE, exchanged).get("/api/v1/auth/me").then().statusCode(401);

		api(admin).delete("/api/v1/clients/" + clientId).then().statusCode(204);
	}
}
