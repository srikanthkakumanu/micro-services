package com.platform.e2e;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static com.platform.e2e.Platform.adminToken;
import static com.platform.e2e.Platform.api;
import static com.platform.e2e.Platform.at;
import static com.platform.e2e.Platform.claims;
import static com.platform.e2e.Platform.header;
import static com.platform.e2e.Platform.strings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * The token contract and its validation: at the gateway and at each service called directly,
 * through key rotation and lifetime changes, and across the host and the container network.
 */
class JwtE2ETest {

	/** Where a token is checked: the gateway and each service on its own. */
	private static final Map<String, String> VALIDATORS = Map.of(
			Platform.GATEWAY, "/api/v1/users/me",
			Platform.USER_SERVICE, "/api/v1/users/me",
			Platform.AUTH_SERVICE, "/api/v1/auth/me");

	// --- validation

	@Test
	void forgedAndDamagedTokensAreRejectedEverywhere() throws Exception {
		String valid = adminToken();
		assertAcceptedEverywhere(valid);

		// Tampered signature.
		assertRejectedEverywhere("tampered signature", valid.substring(0, valid.length() - 8)
				+ (valid.endsWith("AAAAAAAA") ? "BBBBBBBB" : "AAAAAAAA"));

		// Tampered payload: a different subject under the original signature.
		String[] parts = valid.split("\\.");
		String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
				.replace("platform-admin", "someone-else");
		assertRejectedEverywhere("tampered payload", parts[0] + "."
				+ java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2]);

		// alg: none, with the claims of a real token.
		JWTClaimsSet realClaims = SignedJWT.parse(valid).getJWTClaimsSet();
		assertRejectedEverywhere("alg none", new PlainJWT(realClaims).serialize());

		// HS256, keyed with the platform's published RSA public key (algorithm confusion).
		RSAKey published = JWKSet.parse(api(null).get("/api/v1/auth/.well-known/jwks.json").asString()).getKeys().stream()
				.filter(key -> key instanceof RSAKey && header(valid).get("kid").equals(key.getKeyID()))
				.map(key -> (RSAKey) key).findFirst().orElseThrow();
		var hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(published.getKeyID()).build(), realClaims);
		hmac.sign(new MACSigner(published.toRSAPublicKey().getEncoded()));
		assertRejectedEverywhere("HS256", hmac.serialize());

		// Signed by a key the platform never published, otherwise carrying acceptable claims; and
		// the same with a wrong issuer and with a not-before in the future.
		RSAKey attacker = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
		assertRejectedEverywhere("unknown key", sign(attacker, new JWTClaimsSet.Builder(realClaims).build()));
		assertRejectedEverywhere("wrong iss", sign(attacker,
				new JWTClaimsSet.Builder(realClaims).issuer("http://evil.example/realms/platform").build()));
		assertRejectedEverywhere("future nbf", sign(attacker, new JWTClaimsSet.Builder(realClaims)
				.notBeforeTime(Date.from(Instant.now().plus(Duration.ofMinutes(5)))).build()));
		assertRejectedEverywhere("garbage", "not.a.token");
		for (var validator : VALIDATORS.entrySet()) {
			at(validator.getKey(), null).get(validator.getValue()).then().statusCode(401)
					.contentType("application/problem+json").body("code", equalTo("unauthorized"));
		}
	}

	@Test
	void aGenuineTokenWithoutTheServicesAudienceIsRejected() {
		String admin = adminToken();
		String clientId = "no-audience-" + Platform.unique("").substring(0, 6);
		api(admin).body(Map.of("clientId", clientId)).post("/api/v1/clients").then().statusCode(201);

		// Properly signed by the platform, but meant for nobody: it has no audience at all.
		String token = api(null).body(Map.of("clientId", clientId, "clientSecret", Platform.clientSecret(clientId)))
				.post("/api/v1/auth/service-token").then().statusCode(200).extract().path("accessToken");
		assertThat(strings(claims(token).get("aud"))).doesNotContain("api-gateway", "user-service", "auth-service");
		assertRejectedEverywhere("missing own aud", token);

		api(admin).delete("/api/v1/clients/" + clientId).then().statusCode(204);
	}

	@Test
	void shortenedLifetimesApplyToNewTokensWhichThenExpireEverywhere() {
		String admin = adminToken();
		Map<String, Object> original = api(admin).get("/api/v1/token-settings").jsonPath().getMap("$");
		String token;
		try {
			var shorter = new HashMap<>(original);
			shorter.put("accessTokenLifespan", 30);
			api(admin).body(shorter).put("/api/v1/token-settings").then().statusCode(200).body("accessTokenLifespan", equalTo(30));

			JsonPath login = Platform.login("platform-admin", Platform.required("PLATFORM_ADMIN_PASSWORD"));
			assertThat(login.getInt("expiresIn")).isBetween(28, 30);
			token = login.getString("accessToken");
			long lifetime = ((Number) claims(token).get("exp")).longValue() - ((Number) claims(token).get("iat")).longValue();
			assertThat(lifetime).isEqualTo(30);
			assertAcceptedEverywhere(token);
		}
		finally {
			api(adminToken()).body(original).put("/api/v1/token-settings").then().statusCode(200);
		}
		assertThat(Platform.login("platform-admin", Platform.required("PLATFORM_ADMIN_PASSWORD")).getInt("expiresIn")).isBetween(298, 300);

		// Past its 30 seconds plus the 30-second clock skew allowance, the token is expired everywhere.
		long expiresAt = ((Number) claims(token).get("exp")).longValue();
		String expired = token;
		Platform.eventually(Duration.ofSeconds(90), () -> Instant.now().getEpochSecond() > expiresAt + 32);
		assertRejectedEverywhere("expired", expired);
	}

	@Test
	void refreshTokenReuseIsDetectedAndRejected() {
		Platform.User user = Platform.newUser();
		JsonPath login = Platform.login(user.username(), Platform.PASSWORD);

		api(null).body(Map.of("refreshToken", login.getString("refreshToken"))).post("/api/v1/auth/refresh").then().statusCode(200);
		api(null).body(Map.of("refreshToken", login.getString("refreshToken"))).post("/api/v1/auth/refresh").then()
				.statusCode(401).body("code", equalTo("invalid-token"));
	}

	// --- contract

	@Test
	void theAccessTokenCarriesExactlyTheDocumentedClaims() {
		Map<String, Object> claims = claims(adminToken());

		// docs/jwt-contract.md. `groups` is present only for members of a group, `nbf` only if set.
		Set<String> always = Set.of("iss", "sub", "aud", "exp", "iat", "jti", "azp", "sid", "typ", "scope",
				"preferred_username", "email", "email_verified", "name", "realm_access", "permissions");
		Set<String> sometimes = Set.of("groups", "nbf");
		assertThat(claims.keySet()).containsAll(always);
		assertThat(claims.keySet()).allMatch(claim -> always.contains(claim) || sometimes.contains(claim),
				"only documented claims");
		assertThat(claims).containsEntry("iss", Platform.ISSUER).containsEntry("typ", "Bearer")
				.containsEntry("azp", "auth-service").containsEntry("preferred_username", "platform-admin");
		assertThat(strings(claims.get("aud"))).contains("api-gateway", "user-service", "auth-service");
		assertThat(strings(claims.get("permissions"))).contains("users:read", "roles:manage", "clients:manage");
		assertThat(Platform.realmRoles(adminToken())).contains("PLATFORM_ADMIN", "USER");
		assertThat(header(adminToken())).containsEntry("alg", "RS256").containsKey("kid");
	}

	@Test
	void aCustomClaimAppearsAfterReLoginAndDisappearsAfterRemoval() {
		String admin = adminToken();
		String name = "e2e-" + Platform.unique("").substring(0, 6);
		String claim = "e2e_" + Platform.unique("").substring(0, 6);

		api(admin).body(Map.of("name", name, "claim", claim, "source", "FIXED", "value", "acme")).post("/api/v1/token-claims").then().statusCode(201);
		assertThat(claims(adminToken())).containsEntry(claim, "acme");

		api(admin).delete("/api/v1/token-claims/" + name).then().statusCode(204);
		assertThat(claims(adminToken())).doesNotContainKey(claim);
	}

	// --- key rotation

	@Test
	void signingKeyRotationWithoutDowntime() {
		String oldToken = adminToken();
		String oldKid = (String) header(oldToken).get("kid");
		List<Map<String, Object>> keys = api(oldToken).get("/api/v1/keys").jsonPath().getList("$");
		String oldKeyId = (String) keys.stream().filter(key -> oldKid.equals(key.get("kid"))).findFirst().orElseThrow().get("id");
		api(oldToken).post("/api/v1/keys/" + oldKeyId + "/disable").then().statusCode(403).body("code", equalTo("operation-not-permitted"));

		// Rotate: old tokens are still accepted, new tokens carry the new kid and are accepted
		// too, with no restart (the JWKS is fetched again for the unknown kid).
		api(oldToken).post("/api/v1/keys/rotate").then().statusCode(200);
		String newToken = adminToken();
		assertThat(header(newToken).get("kid")).isNotEqualTo(oldKid);
		// Validators limit how often an unknown kid makes them refetch keys, so allow a few seconds.
		for (var validator : VALIDATORS.entrySet()) {
			Platform.eventually(Duration.ofSeconds(20),
					() -> at(validator.getKey(), newToken).get(validator.getValue()).statusCode() == 200);
		}
		assertAcceptedEverywhere(oldToken);

		// Disable the old key: its tokens are rejected once each validator's key cache has expired.
		api(newToken).post("/api/v1/keys/" + oldKeyId + "/disable").then().statusCode(204);
		for (var validator : VALIDATORS.entrySet()) {
			Platform.eventually(Duration.ofSeconds(60),
					() -> at(validator.getKey(), oldToken).get(validator.getValue()).statusCode() == 401);
		}
		assertAcceptedEverywhere(newToken);

		api(newToken).delete("/api/v1/keys/" + oldKeyId).then().statusCode(204);
	}

	// --- issuer consistency

	@Test
	void aTokenIsAcceptedWhereverItWasObtained() throws Exception {
		// Obtained from the host through the gateway: accepted by the services inside the network.
		String fromHost = adminToken();
		assertThat(claims(fromHost)).containsEntry("iss", Platform.ISSUER);
		assertAcceptedEverywhere(fromHost);

		// Obtained inside the network, service to service: the same issuer, accepted from the host.
		String fromInside = loginFromInsideTheNetwork();
		assertThat(claims(fromInside)).containsEntry("iss", Platform.ISSUER);
		assertAcceptedEverywhere(fromInside);
	}

	/** Runs the login from inside a container, against auth-service's internal address. */
	private static String loginFromInsideTheNetwork() throws Exception {
		String body = "{\"username\":\"platform-admin\",\"password\":\"" + Platform.required("PLATFORM_ADMIN_PASSWORD") + "\"}";
		var command = new ArrayList<>(List.of(System.getenv().getOrDefault("E2E_INTERNAL_EXEC",
				"docker compose exec -T api-gateway").split(" ")));
		command.addAll(List.of("curl", "-s", "-X", "POST", "http://auth-service:9141/api/v1/auth/login", "-H",
				"Content-Type: application/json", "-d", body));
		// Only standard output is the response; kubectl writes notices to standard error.
		Process process = new ProcessBuilder(command).directory(composeDirectory())
				.redirectError(ProcessBuilder.Redirect.DISCARD).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as("in-network login command").isZero();
		return new JsonPath(output).getString("accessToken");
	}

	private static File composeDirectory() {
		File here = new File("").getAbsoluteFile();
		return new File(here, "docker-compose.yml").exists() ? here : here.getParentFile();
	}

	// --- helpers

	private static void assertAcceptedEverywhere(String token) {
		for (var validator : VALIDATORS.entrySet()) {
			assertThat(at(validator.getKey(), token).get(validator.getValue()).statusCode())
					.as("%s%s", validator.getKey(), validator.getValue()).isEqualTo(200);
		}
	}

	private static void assertRejectedEverywhere(String what, String token) {
		for (var validator : VALIDATORS.entrySet()) {
			assertThat(at(validator.getKey(), token).get(validator.getValue()).statusCode())
					.as("%s at %s%s", what, validator.getKey(), validator.getValue()).isEqualTo(401);
		}
	}

	private static String sign(RSAKey key, JWTClaimsSet claims) throws Exception {
		var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}
}
