package com.fleetpilot.portal.auth;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fleetpilot.portal.config.PortalConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenID Connect with Keycloak, authorization code flow with PKCE, written with the JDK HTTP client: the extranet was
 * a servlet application with its own password table, and moving it to single sign-on did not justify a framework.
 *
 * <p>The tokens are received by the server itself, over the back channel, with the client secret: the ID token comes
 * straight from Keycloak and its claims are read without checking the signature (OpenID Connect Core, 3.1.3.7); the
 * nonce is still checked against the one of the request.
 */
public class OidcClient {

    /** Tokens of a signed-in user, kept in the HTTP session. */
    public record Tokens(String accessToken, String refreshToken, String idToken, Instant expiresAt)
            implements Serializable {

        public boolean expiresWithin(Duration margin, Instant now) {
            return expiresAt.minus(margin).isBefore(now);
        }
    }

    /** Keycloak refused the code or the refresh token, or could not be reached. */
    public static class OidcException extends Exception {
        public OidcException(String message) {
            super(message);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PortalConfig config;
    private final HttpClient http;
    private final JsonMapper json;

    public OidcClient(PortalConfig config, HttpClient http, JsonMapper json) {
        this.config = config;
        this.http = http;
        this.json = json;
    }

    /** Where the browser goes to sign in. */
    public URI authorizationUrl(String redirectUri, String state, String nonce, String codeVerifier) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", config.clientId());
        query.put("response_type", "code");
        query.put("scope", "openid profile email");
        query.put("redirect_uri", redirectUri);
        query.put("state", state);
        query.put("nonce", nonce);
        query.put("code_challenge", codeChallenge(codeVerifier));
        query.put("code_challenge_method", "S256");
        return URI.create(config.issuer() + "/protocol/openid-connect/auth?" + form(query));
    }

    public Tokens exchange(String code, String redirectUri, String codeVerifier) throws OidcException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        return tokenRequest(form, null);
    }

    public Tokens refresh(Tokens tokens) throws OidcException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", tokens.refreshToken());
        return tokenRequest(form, tokens.idToken());
    }

    /** Where the browser goes to close the Keycloak session too. */
    public URI logoutUrl(String idToken, String postLogoutRedirectUri) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", config.clientId());
        query.put("post_logout_redirect_uri", postLogoutRedirectUri);
        if (idToken != null) {
            query.put("id_token_hint", idToken);
        }
        return URI.create(config.issuer() + "/protocol/openid-connect/logout?" + form(query));
    }

    private Tokens tokenRequest(Map<String, String> form, String previousIdToken) throws OidcException {
        String basic = Base64.getEncoder().encodeToString(
                (encode(config.clientId()) + ":" + encode(config.clientSecret())).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.internalIssuer() + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(form)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new OidcException("Keycloak unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OidcException("Interrupted");
        }
        if (response.statusCode() != 200) {
            throw new OidcException("Token request refused (" + response.statusCode() + "): " + response.body());
        }
        JsonNode body = json.readTree(response.body());
        String idToken = body.path("id_token").asString(previousIdToken);
        return new Tokens(body.path("access_token").asString(), body.path("refresh_token").asString(null), idToken,
                Instant.now().plusSeconds(body.path("expires_in").asLong(60)));
    }

    /** The claims of a token, read from its payload. */
    public Map<String, Object> claims(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Not a JWT");
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = json.readValue(payload, Map.class);
        return claims;
    }

    /** A random value for state, nonce and code verifier: 32 bytes, URL-safe. */
    public static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE S256 (RFC 7636): base64url of the SHA-256 of the verifier. */
    static String codeChallenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
