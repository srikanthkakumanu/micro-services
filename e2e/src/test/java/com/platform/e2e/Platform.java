package com.platform.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The running platform as the end-to-end tests see it. Everything goes through the public APIs;
 * the only things read from outside them are emails (Mailpit) and client secrets (Vault), which is
 * where a person or a deployment would read them too.
 */
final class Platform {

	static final String PASSWORD = "S3cret!Passw0rd";

	private static final Map<String, String> ENV = loadEnv();

	static final String GATEWAY = setting("E2E_GATEWAY_URL", "http://localhost:" + ENV.getOrDefault("GATEWAY_PORT", "9211"));
	static final String USER_SERVICE = setting("E2E_USER_SERVICE_URL", "http://localhost:" + ENV.getOrDefault("USER_SERVICE_PORT", "9121"));
	static final String AUTH_SERVICE = setting("E2E_AUTH_SERVICE_URL", "http://localhost:" + ENV.getOrDefault("AUTH_SERVICE_PORT", "9141"));
	static final String BOOKS_SERVICE = setting("E2E_BOOKS_SERVICE_URL", "http://localhost:" + ENV.getOrDefault("BOOKS_SERVICE_PORT", "9151"));
	static final String MAILPIT = setting("E2E_MAILPIT_URL", "http://localhost:" + ENV.getOrDefault("MAILPIT_UI_PORT", "8025"));
	static final String VAULT = setting("E2E_VAULT_URL", "http://localhost:" + ENV.getOrDefault("VAULT_PORT", "8200"));
	static final String ISSUER = setting("E2E_ISSUER", ENV.getOrDefault("KEYCLOAK_PUBLIC_URL", "http://localhost:8080") + "/realms/platform");

	private Platform() {
	}

	// --- requests

	/** A request to the gateway, optionally with a bearer token. */
	static RequestSpecification api(String token) {
		return at(GATEWAY, token);
	}

	static RequestSpecification at(String baseUrl, String token) {
		RequestSpecification request = RestAssured.given().baseUri(baseUrl).contentType(ContentType.JSON);
		return token == null ? request : request.header("Authorization", "Bearer " + token);
	}

	// --- signing in

	static JsonPath login(String username, String password) {
		Response response = api(null).body(Map.of("username", username, "password", password)).post("/api/v1/auth/login");
		assertThat(response.statusCode()).as("login of %s: %s", username, response.asString()).isEqualTo(200);
		return response.jsonPath();
	}

	static String token(String username) {
		return login(username, PASSWORD).getString("accessToken");
	}

	/** A fresh token of the bootstrap platform administrator. Never cached: tests rotate keys and end sessions. */
	static String adminToken() {
		return login("platform-admin", required("PLATFORM_ADMIN_PASSWORD")).getString("accessToken");
	}

	static String adminId() {
		return api(adminToken()).get("/api/v1/auth/me").jsonPath().getString("id");
	}

	// --- users

	/** Creates a verified user who can sign in with {@link #PASSWORD}, through the admin API. */
	static User newUser() {
		String username = unique("user");
		String admin = adminToken();
		String id = api(admin).body(Map.of("username", username, "email", username + "@example.com", "firstName", "Test",
				"lastName", "User", "emailVerified", true)).post("/api/v1/users").then().statusCode(201).extract().path("id");
		api(admin).body(Map.of("password", PASSWORD, "temporary", false)).put("/api/v1/users/" + id + "/credentials/password")
				.then().statusCode(204);
		return new User(id, username);
	}

	record User(String id, String username) {

		String email() {
			return username + "@example.com";
		}
	}

	static Map<String, Object> roles(String... names) {
		return Map.of("roles", java.util.Arrays.stream(names).map(name -> Map.of("name", name)).toList());
	}

	static Map<String, Object> permission(String service, String name) {
		return Map.of("roles", List.of(Map.of("name", name, "clientId", service)));
	}

	// --- tokens

	static Map<String, Object> claims(String token) {
		return decode(token.split("\\.")[1]);
	}

	static Map<String, Object> header(String token) {
		return decode(token.split("\\.")[0]);
	}

	private static Map<String, Object> decode(String part) {
		return new JsonPath(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8)).getMap("$");
	}

	static List<String> strings(Object claim) {
		if (claim == null) {
			return List.of();
		}
		return claim instanceof String single ? List.of(single) : ((List<?>) claim).stream().map(String::valueOf).toList();
	}

	@SuppressWarnings("unchecked")
	static List<String> realmRoles(String token) {
		return strings(((Map<String, Object>) claims(token).get("realm_access")).get("roles"));
	}

	// --- outside the APIs: email and the secret store

	/** The text of the newest email to the address, waiting briefly for it to arrive. */
	static String latestEmailTo(String address) {
		return eventually(Duration.ofSeconds(15), () -> {
			List<String> ids = RestAssured.given().baseUri(MAILPIT).queryParam("query", "to:" + address)
					.get("/api/v1/search").jsonPath().getList("messages.ID");
			if (ids == null || ids.isEmpty()) {
				return null;
			}
			return RestAssured.given().baseUri(MAILPIT).get("/api/v1/message/" + ids.getFirst()).jsonPath().getString("Text");
		});
	}

	/** Does what a person does with a verification email: opens the link and confirms. */
	static void followEmailLink(String emailText) {
		Matcher link = Pattern.compile("https?://\\S+action-token\\S+").matcher(emailText.replace("\r", ""));
		assertThat(link.find()).as("the email contains an action link").isTrue();
		HttpClient browser = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<String> page = get(browser, link.group(), null);
		Matcher proceed = Pattern.compile("href=\"([^\"]*action-token[^\"]*)\"").matcher(page.body());
		if (proceed.find()) {
			// The identity provider marks its cookies Secure. A browser still returns them to
			// http://localhost; Java's cookie handler does not, so they are passed on by hand.
			String cookies = page.headers().allValues("set-cookie").stream().map(cookie -> cookie.split(";", 2)[0])
					.collect(java.util.stream.Collectors.joining("; "));
			get(browser, proceed.group(1).replace("&amp;", "&"), cookies);
		}
	}

	private static HttpResponse<String> get(HttpClient client, String url, String cookies) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).GET();
			if (cookies != null && !cookies.isBlank()) {
				request.header("Cookie", cookies);
			}
			HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isLessThan(400);
			return response;
		}
		catch (IOException | InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Reads a registered client's secret from where the platform stored it. */
	static String clientSecret(String clientId) {
		return RestAssured.given().baseUri(VAULT).header("X-Vault-Token", required("VAULT_DEV_ROOT_TOKEN"))
				.get("/v1/secret/data/clients/" + clientId).then().statusCode(200).extract().path("data.data.client-secret");
	}

	static boolean clientSecretExists(String clientId) {
		return RestAssured.given().baseUri(VAULT).header("X-Vault-Token", required("VAULT_DEV_ROOT_TOKEN"))
				.get("/v1/secret/data/clients/" + clientId).statusCode() == 200;
	}

	// --- helpers

	static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	/** Polls until the supplier returns a non-null, non-false value. */
	static <T> T eventually(Duration timeout, Supplier<T> attempt) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (true) {
			T value = attempt.get();
			if (value != null && !Boolean.FALSE.equals(value)) {
				return value;
			}
			if (System.nanoTime() > deadline) {
				throw new AssertionError("Condition not met within " + timeout);
			}
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(ex);
			}
		}
	}

	static String required(String name) {
		String value = System.getenv(name) != null ? System.getenv(name) : ENV.get(name);
		assertThat(value).as("%s must be set (it is generated into .env by `make up`)", name).isNotBlank();
		return value;
	}

	private static String setting(String name, String fallback) {
		return System.getenv(name) != null ? System.getenv(name) : fallback;
	}

	/** The generated .env of the Compose stack, so the suite runs the same from make and from an IDE. */
	private static Map<String, String> loadEnv() {
		var values = new HashMap<String, String>();
		for (Path candidate : List.of(Path.of(".env"), Path.of("..", ".env"))) {
			if (Files.exists(candidate)) {
				try {
					for (String line : Files.readAllLines(candidate)) {
						int equals = line.indexOf('=');
						if (equals > 0 && !line.startsWith("#")) {
							values.put(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
						}
					}
				}
				catch (IOException ex) {
					throw new IllegalStateException(ex);
				}
				break;
			}
		}
		return values;
	}
}
