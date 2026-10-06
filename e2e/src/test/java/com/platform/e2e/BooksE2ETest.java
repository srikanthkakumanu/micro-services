package com.platform.e2e;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.BOOKS_SERVICE;
import static com.platform.e2e.Platform.PASSWORD;
import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.at;
import static com.platform.e2e.Platform.claims;
import static com.platform.e2e.Platform.realmRoles;
import static com.platform.e2e.Platform.roles;
import static com.platform.e2e.Platform.strings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * books-service as a consumer of the platform: its users are the ones user-service manages, a
 * login through auth-service is required, and what a user may do in the catalog follows the
 * roles auth-service manages. Users and role assignments are made only through the platform APIs.
 */
class BooksE2ETest {

	private static final String SEEDED_TITLE = "The Enigma of Elysium";

	@Test
	void usingTheCatalogNeedsALoginAndACatalogRole() {
		String admin = adminToken();
		Platform.User user = Platform.newUser();

		// Not logged in: refused at the gateway and by the service itself.
		api(null).get("/api/v1/books").then().statusCode(401).body("code", equalTo("unauthorized"));
		api(null).get("/api/v1/authors").then().statusCode(401);
		at(BOOKS_SERVICE, null).get("/api/v1/books").then().statusCode(401).body("code", equalTo("unauthorized"));
		at(BOOKS_SERVICE, "not.a.token").get("/api/v1/books").then().statusCode(401);

		// Logged in with only the default USER role: the token is meant for books-service, but
		// carries no catalog permission.
		JsonPath login = Platform.login(user.username(), PASSWORD);
		String plain = login.getString("accessToken");
		assertThat(strings(claims(plain).get("aud"))).contains("books-service");
		assertThat(realmRoles(plain)).contains("USER").doesNotContain("CATALOG_READER");
		api(plain).get("/api/v1/books").then().statusCode(403).body("code", equalTo("forbidden"));
		api(plain).get("/api/v1/authors").then().statusCode(403);

		// auth-service assigns the role; the next token carries what it grants.
		api(admin).body(roles("CATALOG_READER")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		JsonPath refreshed = refresh(login);
		String reader = refreshed.getString("accessToken");
		assertThat(realmRoles(reader)).contains("CATALOG_READER");
		assertThat(strings(claims(reader).get("permissions"))).containsExactly("books:read");

		// The seeded catalog is there, every book with its author, and valid ISBNs.
		api(reader).queryParam("size", 50).get("/api/v1/books").then().statusCode(200)
				.body("total", greaterThanOrEqualTo(44))
				.body("items.author.id", everyItem(notNullValue()))
				.body("items.isbn", everyItem(matchesPattern("97[89][0-9]{10}")));
		api(reader).queryParam("title", SEEDED_TITLE).get("/api/v1/books").then().statusCode(200)
				.body("items", hasSize(1))
				.body("items[0].publisher", equalTo("Mystic Press"))
				.body("items[0].author.firstName", equalTo("Evelyn")).body("items[0].author.lastName", equalTo("Wren"))
				.body("items[0].ownerId", nullValue());
		api(reader).queryParam("size", 1).get("/api/v1/authors").then().statusCode(200)
				.body("total", greaterThanOrEqualTo(47));
		// The same token is accepted by the service directly: it validates tokens itself.
		at(BOOKS_SERVICE, reader).queryParam("size", 1).get("/api/v1/books").then().statusCode(200);

		// A reader cannot write.
		api(reader).body(book("Not allowed", anAuthorId(reader))).post("/api/v1/books").then().statusCode(403)
				.body("code", equalTo("forbidden"));

		// Revoking the role takes the access away with the next token.
		api(admin).body(roles("CATALOG_READER")).delete("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		JsonPath afterRevoke = refresh(refreshed);
		api(afterRevoke.getString("accessToken")).get("/api/v1/books").then().statusCode(403);

		// Signing out everywhere ends the session: no more tokens for the catalog.
		api(afterRevoke.getString("accessToken")).post("/api/v1/auth/logout-all").then().statusCode(204);
		api(null).body(Map.of("refreshToken", afterRevoke.getString("refreshToken"))).post("/api/v1/auth/refresh")
				.then().statusCode(401);

		api(admin).delete("/api/v1/users/" + user.id()).then().statusCode(204);
	}

	@Test
	void anEditorKeepsTheirOwnBooksAndNobodyElses() {
		String admin = adminToken();
		Platform.User alice = Platform.newUser();
		Platform.User bob = Platform.newUser();
		for (Platform.User user : List.of(alice, bob)) {
			api(admin).body(roles("CATALOG_EDITOR")).post("/api/v1/users/" + user.id() + "/roles").then().statusCode(204);
		}
		String aliceToken = Platform.token(alice.username());
		String bobToken = Platform.token(bob.username());
		assertThat(strings(claims(aliceToken).get("permissions"))).containsExactlyInAnyOrder("books:read", "books:write");
		String authorId = anAuthorId(aliceToken);

		// The book belongs to the logged-in platform user; no user name is taken from the request.
		Map<String, Object> request = book("Alice's Field Notes", authorId);
		String id = api(aliceToken).body(request).post("/api/v1/books").then().statusCode(201)
				.header("Location", org.hamcrest.Matchers.startsWith("/api/v1/books/"))
				.body("ownerId", equalTo(alice.id())).body("author.id", equalTo(authorId)).extract().path("id");
		api(aliceToken).queryParam("owner", "me").get("/api/v1/books").then().statusCode(200)
				.body("items.id", equalTo(List.of(id)));
		api(bobToken).queryParam("owner", "me").get("/api/v1/books").then().statusCode(200).body("items", hasSize(0));
		api(bobToken).queryParam("ownerId", alice.id()).get("/api/v1/books").then().body("items.id", equalTo(List.of(id)));

		// Someone else with the same role cannot change or remove it, or reuse its ISBN.
		api(bobToken).body(request).put("/api/v1/books/" + id).then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(bobToken).delete("/api/v1/books/" + id).then().statusCode(403).body("code", equalTo("operation-not-permitted"));
		Map<String, Object> sameIsbn = new HashMap<>(book("Copycat", authorId));
		sameIsbn.put("isbn", request.get("isbn"));
		api(bobToken).body(sameIsbn).post("/api/v1/books").then().statusCode(409).body("code", equalTo("duplicate-book"));

		// An editor cannot add a book in another user's name, hand one over, change a book of the
		// seeded catalog, or change authors.
		Map<String, Object> forBob = new HashMap<>(book("For Bob", authorId));
		forBob.put("ownerId", bob.id());
		api(aliceToken).body(forBob).post("/api/v1/books").then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(aliceToken).body(Map.of("ownerId", bob.id())).put("/api/v1/books/" + id + "/owner").then().statusCode(403)
				.body("code", equalTo("forbidden"));
		String seededId = api(aliceToken).queryParam("title", SEEDED_TITLE).get("/api/v1/books").then().extract()
				.path("items[0].id");
		api(aliceToken).delete("/api/v1/books/" + seededId).then().statusCode(403)
				.body("code", equalTo("operation-not-permitted"));
		api(aliceToken).body(Map.of("firstName", "New", "lastName", "Author", "genre", "Poetry")).post("/api/v1/authors")
				.then().statusCode(403).body("code", equalTo("forbidden"));

		// The owner changes and removes it.
		Map<String, Object> revised = new HashMap<>(request);
		revised.put("title", "Alice's Field Notes, 2nd edition");
		revised.put("completed", true);
		api(aliceToken).body(revised).put("/api/v1/books/" + id).then().statusCode(200)
				.body("title", equalTo("Alice's Field Notes, 2nd edition")).body("completed", equalTo(true))
				.body("ownerId", equalTo(alice.id()));
		api(aliceToken).body(Map.of("title", "", "isbn", "123", "publisher", "P", "authorId", authorId))
				.put("/api/v1/books/" + id).then().statusCode(400).body("code", equalTo("invalid-value"));
		api(aliceToken).delete("/api/v1/books/" + id).then().statusCode(204);
		api(aliceToken).get("/api/v1/books/" + id).then().statusCode(404).body("code", equalTo("book-not-found"));

		api(admin).delete("/api/v1/users/" + alice.id()).then().statusCode(204);
		api(admin).delete("/api/v1/users/" + bob.id()).then().statusCode(204);
	}

	@Test
	void aCatalogManagerManagesAuthorsAndHandsBooksToPlatformUsers() {
		String admin = adminToken();
		Platform.User manager = Platform.newUser();
		Platform.User owner = Platform.newUser();
		Platform.User disabled = Platform.newUser();
		api(admin).body(roles("CATALOG_MANAGER")).post("/api/v1/users/" + manager.id() + "/roles").then().statusCode(204);
		api(admin).body(roles("CATALOG_EDITOR")).post("/api/v1/users/" + owner.id() + "/roles").then().statusCode(204);
		api(admin).post("/api/v1/users/" + disabled.id() + "/disable").then().statusCode(200);
		String managerToken = Platform.token(manager.username());
		String ownerToken = Platform.token(owner.username());
		assertThat(strings(claims(managerToken).get("permissions")))
				.containsExactlyInAnyOrder("books:read", "books:write", "books:manage", "authors:manage");

		// Authors.
		String authorId = api(managerToken).body(Map.of("firstName", "Ursula", "lastName", "Le Guin", "genre",
				"Science Fiction")).post("/api/v1/authors").then().statusCode(201).extract().path("id");
		api(managerToken).body(Map.of("firstName", "Ursula K.", "lastName", "Le Guin", "genre", "Speculative Fiction"))
				.put("/api/v1/authors/" + authorId).then().statusCode(200).body("firstName", equalTo("Ursula K."));
		api(managerToken).queryParam("name", "le guin").get("/api/v1/authors").then().statusCode(200)
				.body("items.id", org.hamcrest.Matchers.hasItem(authorId));

		// A book the manager adds for themselves, then hands to a user user-service knows.
		String id = api(managerToken).body(book("The Dispossessed", authorId)).post("/api/v1/books").then()
				.statusCode(201).body("ownerId", equalTo(manager.id())).extract().path("id");
		api(managerToken).body(Map.of("ownerId", "00000000-0000-4000-8000-000000000000"))
				.put("/api/v1/books/" + id + "/owner").then().statusCode(422).body("code", equalTo("owner-not-eligible"));
		api(managerToken).body(Map.of("ownerId", disabled.id())).put("/api/v1/books/" + id + "/owner").then()
				.statusCode(422).body("code", equalTo("owner-not-eligible"));
		api(managerToken).get("/api/v1/books/" + id).then().body("ownerId", equalTo(manager.id()));
		api(managerToken).body(Map.of("ownerId", owner.id())).put("/api/v1/books/" + id + "/owner").then()
				.statusCode(200).body("ownerId", equalTo(owner.id()));
		api(ownerToken).queryParam("owner", "me").get("/api/v1/books").then().body("items.id", equalTo(List.of(id)));

		// A manager may also add a book directly for another user, and change the seeded catalog.
		Map<String, Object> gift = new HashMap<>(book("A Gift", authorId));
		gift.put("ownerId", owner.id());
		String giftId = api(managerToken).body(gift).post("/api/v1/books").then().statusCode(201)
				.body("ownerId", equalTo(owner.id())).extract().path("id");
		JsonPath seeded = api(managerToken).queryParam("title", "Whispers in the Mist").get("/api/v1/books").jsonPath();
		Map<String, Object> seededUpdate = new HashMap<>();
		seededUpdate.put("title", seeded.getString("items[0].title"));
		seededUpdate.put("description", "Reviewed by the catalog team.");
		seededUpdate.put("isbn", seeded.getString("items[0].isbn"));
		seededUpdate.put("publisher", seeded.getString("items[0].publisher"));
		seededUpdate.put("authorId", seeded.getString("items[0].author.id"));
		api(managerToken).body(seededUpdate).put("/api/v1/books/" + seeded.getString("items[0].id")).then()
				.statusCode(200).body("description", equalTo("Reviewed by the catalog team.")).body("ownerId", nullValue());
		// Put the seeded entry back as it was, so the starter catalog stays the same for whoever uses it next.
		seededUpdate.put("description", seeded.getString("items[0].description"));
		api(managerToken).body(seededUpdate).put("/api/v1/books/" + seeded.getString("items[0].id")).then().statusCode(200);

		// An author with books stays; once the books are gone the author can go.
		api(managerToken).delete("/api/v1/authors/" + authorId).then().statusCode(409).body("code", equalTo("author-in-use"));
		api(ownerToken).delete("/api/v1/books/" + id).then().statusCode(204);
		api(managerToken).delete("/api/v1/books/" + giftId).then().statusCode(204);
		api(managerToken).delete("/api/v1/authors/" + authorId).then().statusCode(204);
		api(managerToken).get("/api/v1/authors/" + authorId).then().statusCode(404).body("code", equalTo("author-not-found"));

		for (Platform.User user : List.of(manager, owner, disabled)) {
			api(admin).delete("/api/v1/users/" + user.id()).then().statusCode(204);
		}
	}

	@Test
	void booksServiceRejectsTokensThatAreNotMeantForIt() {
		// books-service's own service token is meant for user-service only. It is valid, signed by
		// the platform, and still must not open the catalog.
		String serviceToken = api(null).body(Map.of("clientId", "books-service", "clientSecret",
				Platform.clientSecret("books-service"))).post("/api/v1/auth/service-token").then().statusCode(200)
				.extract().path("accessToken");
		assertThat(strings(claims(serviceToken).get("aud"))).contains("user-service").doesNotContain("books-service");
		assertThat(strings(claims(serviceToken).get("permissions"))).containsExactly("users:read");

		at(BOOKS_SERVICE, serviceToken).get("/api/v1/books").then().statusCode(401).body("code", equalTo("unauthorized"));

		// The platform administrator holds every catalog permission.
		String admin = adminToken();
		assertThat(strings(claims(admin).get("permissions"))).contains("books:read", "books:write", "books:manage",
				"authors:manage");
		at(BOOKS_SERVICE, admin).queryParam("size", 1).get("/api/v1/books").then().statusCode(200);
		api(admin).queryParam("clientId", "books-service").get("/api/v1/roles").then().statusCode(200)
				.body("name", org.hamcrest.Matchers.hasItems("books:read", "books:write", "books:manage", "authors:manage"));
		api(admin).get("/api/v1/roles/CATALOG_EDITOR/permissions").then().statusCode(200)
				.body("name", org.hamcrest.Matchers.containsInAnyOrder("books:read", "books:write"));
	}

	// --- helpers

	private static JsonPath refresh(JsonPath tokens) {
		return api(null).body(Map.of("refreshToken", tokens.getString("refreshToken"))).post("/api/v1/auth/refresh")
				.then().statusCode(200).extract().jsonPath();
	}

	private static String anAuthorId(String token) {
		return api(token).queryParam("size", 1).get("/api/v1/authors").then().statusCode(200).extract().path("items[0].id");
	}

	private static Map<String, Object> book(String title, String authorId) {
		return Map.of("title", title, "isbn", isbn(), "publisher", "E2E Press", "authorId", authorId);
	}

	/** A fresh valid ISBN-13 in the 979 range, which the seeded catalog does not use. */
	private static String isbn() {
		String twelve = "979" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
		int sum = 0;
		for (int i = 0; i < 12; i++) {
			int digit = twelve.charAt(i) - '0';
			sum += i % 2 == 0 ? digit : 3 * digit;
		}
		return twelve + (10 - sum % 10) % 10;
	}
}
