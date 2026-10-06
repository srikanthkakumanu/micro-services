package com.platform.e2e;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.PASSWORD;
import static com.platform.e2e.Platform.VIDEO_SERVICE;
import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.at;
import static com.platform.e2e.Platform.claims;
import static com.platform.e2e.Platform.realmRoles;
import static com.platform.e2e.Platform.roles;
import static com.platform.e2e.Platform.strings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * video-service as a consumer of the platform: its users are the ones user-service manages, a
 * login through auth-service is required, and what a user may do with videos follows the video
 * roles auth-service manages. Users and role assignments are made only through the platform APIs.
 */
class VideosE2ETest {

	private static final String SEEDED_TITLE = "Spring Boot in One Hour";

	@Test
	void usingVideosNeedsALoginAndAVideoRole() {
		String admin = adminToken();
		Platform.User user = Platform.newUser();

		// Not logged in: refused at the gateway and by the service itself.
		api(null).get("/api/v1/videos").then().statusCode(401).body("code", equalTo("unauthorized"));
		at(VIDEO_SERVICE, null).get("/api/v1/videos").then().statusCode(401).body("code", equalTo("unauthorized"));
		at(VIDEO_SERVICE, "not.a.token").get("/api/v1/videos").then().statusCode(401);

		// Logged in with only the default USER role.
		JsonPath login = Platform.login(user.username(), PASSWORD);
		assertThat(strings(claims(login.getString("accessToken")).get("aud"))).contains("video-service");
		api(login.getString("accessToken")).get("/api/v1/videos").then().statusCode(403).body("code", equalTo("forbidden"));

		// A role for the book catalog opens the books, not the videos.
		api(admin).body(roles("CATALOG_MANAGER")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		JsonPath withBooks = refresh(login);
		String bookManager = withBooks.getString("accessToken");
		assertThat(realmRoles(bookManager)).contains("CATALOG_MANAGER").doesNotContain("VIDEO_READER");
		api(bookManager).queryParam("size", 1).get("/api/v1/books").then().statusCode(200);
		api(bookManager).get("/api/v1/videos").then().statusCode(403).body("code", equalTo("forbidden"));
		api(bookManager).body(Map.of("title", title())).post("/api/v1/videos").then().statusCode(403);
		api(admin).body(roles("CATALOG_MANAGER")).delete("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);

		// auth-service assigns the video role; the next token carries what it grants.
		api(admin).body(roles("VIDEO_READER")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		JsonPath refreshed = refresh(withBooks);
		String reader = refreshed.getString("accessToken");
		assertThat(strings(claims(reader).get("permissions"))).containsExactly("videos:read");

		// The starter set is there, owned by nobody.
		api(reader).queryParam("size", 50).get("/api/v1/videos").then().statusCode(200)
				.body("total", greaterThanOrEqualTo(20));
		api(reader).queryParam("title", "spring boot").get("/api/v1/videos").then().statusCode(200)
				.body("items", hasSize(1)).body("items[0].title", equalTo(SEEDED_TITLE))
				.body("items[0].ownerId", nullValue()).body("items[0].completed", equalTo(false));
		at(VIDEO_SERVICE, reader).queryParam("size", 1).get("/api/v1/videos").then().statusCode(200);
		// A video reader cannot write, and cannot open the book catalog.
		api(reader).body(Map.of("title", title())).post("/api/v1/videos").then().statusCode(403)
				.body("code", equalTo("forbidden"));
		api(reader).get("/api/v1/books").then().statusCode(403);

		// Revoking the role takes the access away with the next token.
		api(admin).body(roles("VIDEO_READER")).delete("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		api(refresh(refreshed).getString("accessToken")).get("/api/v1/videos").then().statusCode(403);

		api(admin).delete("/api/v1/users/" + user.id()).then().statusCode(204);
	}

	@Test
	void anEditorKeepsTheirOwnVideosAndNobodyElses() {
		String admin = adminToken();
		Platform.User alice = Platform.newUser();
		Platform.User bob = Platform.newUser();
		for (Platform.User user : List.of(alice, bob)) {
			api(admin).body(roles("VIDEO_EDITOR")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		}
		String aliceToken = Platform.token(alice.username());
		String bobToken = Platform.token(bob.username());
		assertThat(strings(claims(aliceToken).get("permissions"))).containsExactlyInAnyOrder("videos:read", "videos:write");
		String title = title();

		// The video belongs to the logged-in platform user.
		String id = api(aliceToken).body(Map.of("title", title, "description", "Alice's own.")).post("/api/v1/videos")
				.then().statusCode(201).header("Location", startsWith("/api/v1/videos/"))
				.body("ownerId", equalTo(alice.id())).body("completed", equalTo(false)).extract().path("id");
		api(aliceToken).queryParam("owner", "me").get("/api/v1/videos").then().statusCode(200)
				.body("items.id", equalTo(List.of(id)));
		api(bobToken).queryParam("owner", "me").get("/api/v1/videos").then().statusCode(200).body("items", hasSize(0));
		api(bobToken).queryParam("ownerId", alice.id()).get("/api/v1/videos").then().body("items.id", equalTo(List.of(id)));

		// Someone else with the same role cannot change, complete or remove it, or reuse its title.
		api(bobToken).body(Map.of("title", title())).put("/api/v1/videos/" + id).then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(bobToken).post("/api/v1/videos/" + id + "/complete").then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(bobToken).delete("/api/v1/videos/" + id).then().statusCode(403).body("code", equalTo("operation-not-permitted"));
		api(bobToken).body(Map.of("title", title)).post("/api/v1/videos").then().statusCode(409)
				.body("code", equalTo("duplicate-video"));

		// An editor cannot add a video in another user's name, hand one over, or change the starter set.
		api(aliceToken).body(Map.of("title", title(), "ownerId", bob.id())).post("/api/v1/videos").then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(aliceToken).body(Map.of("ownerId", bob.id())).put("/api/v1/videos/" + id + "/owner").then().statusCode(403)
				.body("code", equalTo("forbidden"));
		String seededId = api(aliceToken).queryParam("title", SEEDED_TITLE).get("/api/v1/videos").then().extract()
				.path("items[0].id");
		api(aliceToken).post("/api/v1/videos/" + seededId + "/complete").then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(aliceToken).delete("/api/v1/videos/" + seededId).then().statusCode(403);

		// The owner changes, completes and removes it.
		String newTitle = title();
		api(aliceToken).body(Map.of("title", newTitle, "description", "Second cut.")).put("/api/v1/videos/" + id).then()
				.statusCode(200).body("title", equalTo(newTitle)).body("description", equalTo("Second cut."));
		api(aliceToken).body(Map.of("title", "x".repeat(31))).put("/api/v1/videos/" + id).then().statusCode(400)
				.body("code", equalTo("invalid-value"));
		api(aliceToken).post("/api/v1/videos/" + id + "/complete").then().statusCode(200).body("completed", equalTo(true));
		api(aliceToken).queryParam("owner", "me").queryParam("completed", true).get("/api/v1/videos").then()
				.body("items.id", equalTo(List.of(id)));
		api(aliceToken).delete("/api/v1/videos/" + id).then().statusCode(204);
		api(aliceToken).get("/api/v1/videos/" + id).then().statusCode(404).body("code", equalTo("video-not-found"));

		api(admin).delete("/api/v1/users/" + alice.id()).then().statusCode(204);
		api(admin).delete("/api/v1/users/" + bob.id()).then().statusCode(204);
	}

	@Test
	void aVideoManagerHandsVideosToPlatformUsers() {
		String admin = adminToken();
		Platform.User manager = Platform.newUser();
		Platform.User owner = Platform.newUser();
		Platform.User disabled = Platform.newUser();
		api(admin).body(roles("VIDEO_MANAGER")).post("/api/v1/users/" + manager.id() + "/roles").then().statusCode(204);
		api(admin).body(roles("VIDEO_EDITOR")).post("/api/v1/users/" + owner.id() + "/roles").then().statusCode(204);
		api(admin).post("/api/v1/users/" + disabled.id() + "/disable").then().statusCode(200);
		String managerToken = Platform.token(manager.username());
		String ownerToken = Platform.token(owner.username());
		assertThat(strings(claims(managerToken).get("permissions")))
				.containsExactlyInAnyOrder("videos:read", "videos:write", "videos:manage");

		String id = api(managerToken).body(Map.of("title", title())).post("/api/v1/videos").then().statusCode(201)
				.body("ownerId", equalTo(manager.id())).extract().path("id");
		api(managerToken).body(Map.of("ownerId", "00000000-0000-4000-8000-000000000000"))
				.put("/api/v1/videos/" + id + "/owner").then().statusCode(422).body("code", equalTo("owner-not-eligible"));
		api(managerToken).body(Map.of("ownerId", disabled.id())).put("/api/v1/videos/" + id + "/owner").then()
				.statusCode(422).body("code", equalTo("owner-not-eligible"));
		api(managerToken).get("/api/v1/videos/" + id).then().body("ownerId", equalTo(manager.id()));
		api(managerToken).body(Map.of("ownerId", owner.id())).put("/api/v1/videos/" + id + "/owner").then()
				.statusCode(200).body("ownerId", equalTo(owner.id()));
		api(ownerToken).queryParam("owner", "me").get("/api/v1/videos").then().body("items.id", equalTo(List.of(id)));

		// A manager may also add a video directly for another user, and change the starter set.
		String giftId = api(managerToken).body(Map.of("title", title(), "ownerId", owner.id())).post("/api/v1/videos")
				.then().statusCode(201).body("ownerId", equalTo(owner.id())).extract().path("id");
		JsonPath seeded = api(managerToken).queryParam("title", "Graceful Shutdown").get("/api/v1/videos").jsonPath();
		String seededId = seeded.getString("items[0].id");
		api(managerToken).body(Map.of("title", seeded.getString("items[0].title"), "description", "Reviewed."))
				.put("/api/v1/videos/" + seededId).then().statusCode(200).body("description", equalTo("Reviewed."))
				.body("ownerId", nullValue());
		// Put the seeded entry back as it was, so the starter set stays the same for whoever uses it next.
		api(managerToken).body(Map.of("title", seeded.getString("items[0].title"), "description",
				seeded.getString("items[0].description"))).put("/api/v1/videos/" + seededId).then().statusCode(200);

		api(ownerToken).delete("/api/v1/videos/" + id).then().statusCode(204);
		api(managerToken).delete("/api/v1/videos/" + giftId).then().statusCode(204);
		for (Platform.User user : List.of(manager, owner, disabled)) {
			api(admin).delete("/api/v1/users/" + user.id()).then().statusCode(204);
		}
	}

	@Test
	void videoServiceRejectsTokensThatAreNotMeantForIt() {
		// video-service's own service token is meant for user-service only.
		String serviceToken = api(null).body(Map.of("clientId", "video-service", "clientSecret",
				Platform.clientSecret("video-service"))).post("/api/v1/auth/service-token").then().statusCode(200)
				.extract().path("accessToken");
		assertThat(strings(claims(serviceToken).get("aud"))).contains("user-service").doesNotContain("video-service");
		at(VIDEO_SERVICE, serviceToken).get("/api/v1/videos").then().statusCode(401).body("code", equalTo("unauthorized"));

		// The platform administrator holds every video permission.
		String admin = adminToken();
		assertThat(strings(claims(admin).get("permissions"))).contains("videos:read", "videos:write", "videos:manage");
		at(VIDEO_SERVICE, admin).queryParam("size", 1).get("/api/v1/videos").then().statusCode(200);
		api(admin).queryParam("clientId", "video-service").get("/api/v1/roles").then().statusCode(200)
				.body("name", hasItems("videos:read", "videos:write", "videos:manage"));
		api(admin).get("/api/v1/roles/VIDEO_EDITOR/permissions").then().statusCode(200)
				.body("name", containsInAnyOrder("videos:read", "videos:write"));
	}

	// --- helpers

	private static JsonPath refresh(JsonPath tokens) {
		return api(null).body(Map.of("refreshToken", tokens.getString("refreshToken"))).post("/api/v1/auth/refresh")
				.then().statusCode(200).extract().jsonPath();
	}

	/** A fresh title within the 30-character limit. */
	private static String title() {
		return "E2E " + UUID.randomUUID().toString().substring(0, 13);
	}
}
