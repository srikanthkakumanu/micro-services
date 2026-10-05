package com.platform.e2e;

import java.util.List;
import java.util.Map;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.PASSWORD;
import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.claims;
import static com.platform.e2e.Platform.permission;
import static com.platform.e2e.Platform.realmRoles;
import static com.platform.e2e.Platform.roles;
import static com.platform.e2e.Platform.strings;
import static com.platform.e2e.Platform.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;

/** Access administration, decisions, guards and audit, end to end through the gateway. */
class AccessE2ETest {

	@Test
	void accessAdministrationReachesTheUsersToken() {
		String admin = adminToken();
		Platform.User user = Platform.newUser();
		String role = unique("ROLE").toUpperCase();
		String child = unique("CHILD").toUpperCase();
		String permission = unique("res") + ":read";

		// Role, composite role, group with a subgroup.
		api(admin).body(Map.of("name", role)).post("/api/v1/roles").then().statusCode(201);
		api(admin).body(Map.of("name", child)).post("/api/v1/roles").then().statusCode(201);
		api(admin).body(roles(child)).post("/api/v1/roles/" + role + "/composites").then().statusCode(204);
		String group = api(admin).body(Map.of("name", unique("group"))).post("/api/v1/groups").then().statusCode(201).extract().path("id");
		String subgroup = api(admin).body(Map.of("name", "team")).post("/api/v1/groups/" + group + "/children").then().statusCode(201).extract().path("id");

		// Add the user to the subgroup, assign the role to the parent group. The gateway sends
		// /users/{id}/groups and /users/{id}/roles to auth-service.
		api(admin).put("/api/v1/users/" + user.id() + "/groups/" + subgroup).then().statusCode(204);
		api(admin).body(roles(role)).post("/api/v1/groups/" + group + "/roles").then().statusCode(204);
		api(admin).queryParam("effective", true).get("/api/v1/users/" + user.id() + "/roles").then().statusCode(200)
				.body("name", hasItems(role, child));
		api(admin).get("/api/v1/users/" + user.id() + "/roles").then().body("name", not(hasItem(role)));

		JsonPath login = Platform.login(user.username(), PASSWORD);
		assertThat(realmRoles(login.getString("accessToken"))).contains(role, child);
		assertThat(strings(claims(login.getString("accessToken")).get("groups"))).singleElement().asString().endsWith("/team");

		// Create a permission, attach it to the role: it appears in the token after refresh.
		api(admin).body(Map.of("service", "user-service", "name", permission)).post("/api/v1/permissions").then().statusCode(201);
		api(admin).put("/api/v1/roles/" + role + "/permissions/user-service/" + permission).then().statusCode(204);
		JsonPath refreshed = api(null).body(Map.of("refreshToken", login.getString("refreshToken"))).post("/api/v1/auth/refresh").jsonPath();
		assertThat(strings(claims(refreshed.getString("accessToken")).get("permissions"))).containsExactly(permission);
		api(admin).get("/api/v1/users/" + user.id() + "/permissions").then().body("name", hasItem(permission));

		// Revoke the role from the group: reflected after the next refresh.
		api(admin).body(roles(role)).delete("/api/v1/groups/" + group + "/roles").then().statusCode(204);
		JsonPath afterRevoke = api(null).body(Map.of("refreshToken", refreshed.getString("refreshToken"))).post("/api/v1/auth/refresh").jsonPath();
		assertThat(realmRoles(afterRevoke.getString("accessToken"))).doesNotContain(role, child);
		assertThat(claims(afterRevoke.getString("accessToken"))).doesNotContainKey("permissions");

		api(admin).delete("/api/v1/permissions/user-service/" + permission).then().statusCode(204);
		api(admin).delete("/api/v1/groups/" + group).then().statusCode(204);
		api(admin).delete("/api/v1/roles/" + role).then().statusCode(204);
		api(admin).delete("/api/v1/roles/" + child).then().statusCode(204);
	}

	@Test
	void decisions() {
		String admin = adminToken();
		Platform.User user = Platform.newUser();
		Platform.User other = Platform.newUser();
		api(admin).body(permission("user-service", "users:read")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		String token = Platform.token(user.username());

		// Allow and deny.
		api(token).body(Map.of("action", "read", "resource", Map.of("type", "users"))).post("/api/v1/authz/decisions").then()
				.statusCode(200).body("allowed", equalTo(true)).body("reason", equalTo("permission"));
		api(token).body(Map.of("action", "write", "resource", Map.of("type", "users"))).post("/api/v1/authz/decisions").then()
				.statusCode(200).body("allowed", equalTo(false)).body("effect", equalTo("DENY"));

		// Batch evaluation with the ownership rule.
		api(token).body(Map.of("requests", List.of(
				Map.of("action", "update", "resource", Map.of("type", "reviews", "id", "1", "ownerId", user.id())),
				Map.of("action", "update", "resource", Map.of("type", "reviews", "id", "2", "ownerId", other.id())),
				Map.of("action", "read", "resource", Map.of("type", "users")))))
				.post("/api/v1/authz/decisions").then().statusCode(200)
				.body("decisions.allowed", equalTo(List.of(true, false, true)))
				.body("decisions.reason", equalTo(List.of("owner", "not-granted", "permission")));

		api(token).body(Map.of("subject", other.id(), "action", "read", "resource", Map.of("type", "users")))
				.post("/api/v1/authz/decisions").then().statusCode(403).body("code", equalTo("operation-not-permitted"));
	}

	@Test
	void guards() {
		String admin = adminToken();
		Platform.User accessAdmin = Platform.newUser();
		Platform.User plain = Platform.newUser();
		api(admin).body(roles("ACCESS_ADMIN")).post("/api/v1/users/" + accessAdmin.id() + "/roles").then().statusCode(204);
		String accessAdminToken = Platform.token(accessAdmin.username());
		String plainToken = Platform.token(plain.username());

		// 401 without a token, 403 without the permission.
		api(null).get("/api/v1/users").then().statusCode(401).contentType("application/problem+json").body("code", equalTo("unauthorized"));
		api(null).get("/api/v1/roles").then().statusCode(401);
		api(plainToken).get("/api/v1/users").then().statusCode(403).body("code", equalTo("forbidden"));
		api(plainToken).get("/api/v1/roles").then().statusCode(403);
		api(plainToken).get("/api/v1/users/" + accessAdmin.id()).then().statusCode(403);
		api(plainToken).get("/api/v1/users/" + plain.id()).then().statusCode(200);

		// Privilege escalation is refused: nobody grants what they do not hold.
		api(accessAdminToken).body(roles("PLATFORM_ADMIN")).post("/api/v1/users/" + plain.id() + "/roles").then()
				.statusCode(403).body("code", equalTo("operation-not-permitted"));
		api(accessAdminToken).body(roles("PLATFORM_ADMIN")).post("/api/v1/users/" + accessAdmin.id() + "/roles").then().statusCode(403);
		api(accessAdminToken).body(roles("PLATFORM_ADMIN")).post("/api/v1/roles/ACCESS_ADMIN/composites").then().statusCode(403);
		api(accessAdminToken).body(permission("auth-service", "clients:manage")).post("/api/v1/users/" + plain.id() + "/roles").then().statusCode(403);
		api(admin).queryParam("effective", true).get("/api/v1/users/" + plain.id() + "/roles").then().body("name", not(hasItem("PLATFORM_ADMIN")));

		// The last PLATFORM_ADMIN cannot be removed, disabled or demoted.
		String adminId = Platform.adminId();
		api(admin).body(roles("PLATFORM_ADMIN")).delete("/api/v1/users/" + adminId + "/roles").then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(admin).delete("/api/v1/users/" + adminId).then().statusCode(403);
		api(admin).post("/api/v1/users/" + adminId + "/disable").then().statusCode(403);

		// Seeded system roles, permissions and clients cannot be deleted.
		for (String role : List.of("USER", "USER_ADMIN", "ACCESS_ADMIN", "PLATFORM_ADMIN")) {
			api(admin).delete("/api/v1/roles/" + role).then().statusCode(403).body("code", equalTo("operation-not-permitted"));
		}
		api(admin).delete("/api/v1/permissions/user-service/users:read").then().statusCode(403);
		api(admin).delete("/api/v1/clients/auth-service").then().statusCode(403);
		api(admin).get("/api/v1/roles/PLATFORM_ADMIN").then().statusCode(200).body("system", equalTo(true));
	}

	@Test
	void audit() {
		String admin = adminToken();
		String adminId = Platform.adminId();
		Platform.User user = Platform.newUser();
		String role = unique("AUDIT").toUpperCase();
		api(null).body(Map.of("username", user.username(), "password", "Wr0ng!Passw0rd")).post("/api/v1/auth/login").then().statusCode(401);
		Platform.login(user.username(), PASSWORD);
		api(admin).body(Map.of("name", role)).post("/api/v1/roles").then().statusCode(201);
		api(admin).body(roles(role)).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);

		api(admin).queryParam("userId", user.id()).get("/api/v1/audit/login-events").then().statusCode(200)
				.body("items.type", hasItems("LOGIN", "LOGIN_ERROR"));
		api(admin).queryParam("actorId", adminId).queryParam("category", "API").queryParam("size", 50)
				.get("/api/v1/audit/admin-events").then().statusCode(200)
				.body("items.resourcePath", hasItems("/api/v1/roles", "/api/v1/users/" + user.id() + "/roles"));
		api(admin).queryParam("category", "ADMIN").queryParam("resourceType", "REALM_ROLE").queryParam("size", 100)
				.get("/api/v1/audit/admin-events").then().statusCode(200).body("items.resourcePath", hasItem("roles/" + role));

		api(admin).delete("/api/v1/roles/" + role).then().statusCode(204);
	}
}
