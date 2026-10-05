package com.platform.e2e;

import java.util.List;
import java.util.Map;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.PASSWORD;
import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/** Self-service and user administration, end to end through the gateway. */
class IdentityE2ETest {

	@Test
	void selfService() {
		String username = unique("self");
		String email = username + "@example.com";

		// Register, then verify the email address from the message in Mailpit.
		var registered = api(null).body(Map.of("username", username, "email", email, "firstName", "Self", "lastName",
				"Service", "password", PASSWORD)).post("/api/v1/users/register").then().statusCode(201).extract();
		String id = registered.path("id");
		assertThat(registered.header("Location")).isEqualTo("/api/v1/users/" + id);
		api(null).body(Map.of("username", username, "password", PASSWORD)).post("/api/v1/auth/login").then()
				.statusCode(403).body("code", equalTo("account-setup-required"));
		Platform.followEmailLink(Platform.latestEmailTo(email));

		// Login, read and update the own profile.
		JsonPath login = Platform.login(username, PASSWORD);
		String access = login.getString("accessToken");
		api(access).get("/api/v1/users/me").then().statusCode(200).body("id", equalTo(id))
				.body("username", equalTo(username)).body("emailVerified", equalTo(true));
		api(access).body(Map.of("firstName", "Selma", "lastName", "Service", "jobTitle", "Engineer", "timeZone",
				"Europe/London")).put("/api/v1/users/me").then().statusCode(200);
		api(access).get("/api/v1/users/me").then().body("firstName", equalTo("Selma")).body("jobTitle", equalTo("Engineer"));
		api(access).get("/api/v1/auth/me").then().statusCode(200).body("username", equalTo(username)).body("roles", hasItem("USER"));

		// Refresh.
		JsonPath refreshed = api(null).body(Map.of("refreshToken", login.getString("refreshToken")))
				.post("/api/v1/auth/refresh").then().statusCode(200).extract().jsonPath();
		assertThat(refreshed.getString("refreshToken")).isNotEqualTo(login.getString("refreshToken"));

		// Change the password, then sign in with the new one.
		String newPassword = "N3w!Passw0rd-123";
		api(refreshed.getString("accessToken")).body(Map.of("currentPassword", PASSWORD, "newPassword", newPassword))
				.put("/api/v1/auth/me/password").then().statusCode(204);
		api(null).body(Map.of("username", username, "password", PASSWORD)).post("/api/v1/auth/login").then()
				.statusCode(401).body("code", equalTo("invalid-credentials"));
		JsonPath second = Platform.login(username, newPassword);

		// Own sessions, logout, and the old refresh token is rejected.
		api(second.getString("accessToken")).get("/api/v1/auth/me/sessions").then().statusCode(200)
				.body("$", hasSize(1)).body("[0].current", equalTo(true));
		api(second.getString("accessToken")).body(Map.of("refreshToken", second.getString("refreshToken")))
				.post("/api/v1/auth/logout").then().statusCode(204);
		api(null).body(Map.of("refreshToken", second.getString("refreshToken"))).post("/api/v1/auth/refresh").then()
				.statusCode(401).body("code", equalTo("invalid-token"));
	}

	@Test
	void duplicateRegistrationIsAConflict() {
		String username = unique("dup");
		var body = Map.of("username", username, "email", username + "@example.com", "firstName", "Dup", "lastName",
				"Licate", "password", PASSWORD);

		api(null).body(body).post("/api/v1/users/register").then().statusCode(201);
		api(null).body(body).post("/api/v1/users/register").then().statusCode(409)
				.contentType("application/problem+json").body("code", equalTo("duplicate-user"));
	}

	@Test
	void userAdministration() {
		String admin = adminToken();
		String username = unique("managed");

		// Create and search.
		String id = api(admin).body(Map.of("username", username, "email", username + "@example.com", "firstName",
				"Managed", "lastName", "User", "emailVerified", true)).post("/api/v1/users").then().statusCode(201)
				.extract().path("id");
		api(admin).queryParam("q", username).get("/api/v1/users").then().statusCode(200).body("total", equalTo(1))
				.body("items[0].id", equalTo(id));
		api(admin).body(Map.of("password", PASSWORD, "temporary", false)).put("/api/v1/users/" + id + "/credentials/password")
				.then().statusCode(204);
		Platform.login(username, PASSWORD);

		// Disable: the user cannot log in. Enable again.
		api(admin).post("/api/v1/users/" + id + "/disable").then().statusCode(200).body("status", equalTo("DISABLED"));
		api(null).body(Map.of("username", username, "password", PASSWORD)).post("/api/v1/auth/login").then()
				.statusCode(403).body("code", equalTo("account-disabled"));
		api(admin).post("/api/v1/users/" + id + "/enable").then().statusCode(200).body("status", equalTo("ACTIVE"));
		Platform.login(username, PASSWORD);

		// Lock and unlock.
		api(admin).post("/api/v1/users/" + id + "/lock").then().statusCode(200).body("status", equalTo("LOCKED"));
		api(null).body(Map.of("username", username, "password", PASSWORD)).post("/api/v1/auth/login").then().statusCode(403);
		api(admin).post("/api/v1/users/" + id + "/enable").then().statusCode(409).body("code", equalTo("invalid-state"));
		api(admin).post("/api/v1/users/" + id + "/unlock").then().statusCode(200).body("status", equalTo("ACTIVE"));

		// Admin password reset with a temporary password: the user is forced to change it.
		api(admin).body(Map.of("password", "Temp0rary!Passw0rd", "temporary", true))
				.put("/api/v1/users/" + id + "/credentials/password").then().statusCode(204);
		api(admin).get("/api/v1/users/" + id).then().body("requiredActions", hasItem("UPDATE_PASSWORD"));
		api(null).body(Map.of("username", username, "password", "Temp0rary!Passw0rd")).post("/api/v1/auth/login").then()
				.statusCode(403).body("code", equalTo("account-setup-required"));

		// Remove a credential the user must no longer be able to use.
		List<String> credentials = api(admin).get("/api/v1/users/" + id + "/credentials").jsonPath().getList("id");
		assertThat(credentials).hasSize(1);
		api(admin).delete("/api/v1/users/" + id + "/credentials/" + credentials.getFirst()).then().statusCode(204);
		api(admin).get("/api/v1/users/" + id + "/credentials").then().body("$", hasSize(0));

		// Delete.
		api(admin).delete("/api/v1/users/" + id).then().statusCode(204);
		api(admin).get("/api/v1/users/" + id).then().statusCode(404).body("code", equalTo("user-not-found"));
	}

	@Test
	void anAdministratorSignsAUserOutEverywhere() {
		Platform.User user = Platform.newUser();
		JsonPath first = Platform.login(user.username(), PASSWORD);
		JsonPath second = Platform.login(user.username(), PASSWORD);
		String admin = adminToken();
		api(admin).get("/api/v1/sessions/users/" + user.id()).then().statusCode(200).body("$", hasSize(2));

		api(admin).post("/api/v1/sessions/users/" + user.id() + "/sign-out").then().statusCode(204);

		for (JsonPath session : List.of(first, second)) {
			api(null).body(Map.of("refreshToken", session.getString("refreshToken"))).post("/api/v1/auth/refresh").then()
					.statusCode(401).body("code", equalTo("invalid-token"));
		}
		api(admin).get("/api/v1/sessions/users/" + user.id()).then().body("$", hasSize(0));
	}
}
