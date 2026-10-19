package com.fleetpilot.portal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fleetpilot.portal.config.PortalConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OidcClientTest {

    private final PortalConfig config = PortalConfig.from(name -> null);
    private final OidcClient oidc = new OidcClient(config, HttpClient.newHttpClient(), JsonMapper.builder().build());

    private static Map<String, String> query(String url) {
        return Arrays.stream(url.substring(url.indexOf('?') + 1).split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
    }

    private static String jwt(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".signature";
    }

    @Test
    void the_code_challenge_follows_rfc_7636() {
        // Example of appendix B of RFC 7636
        assertThat(OidcClient.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void the_browser_is_sent_to_keycloak_with_state_nonce_and_pkce() {
        String url = oidc.authorizationUrl("http://localhost:8095/extranet/callback", "s1", "n1", "verifier").toString();

        assertThat(url).startsWith("http://localhost:8180/realms/fleetpilot/protocol/openid-connect/auth?");
        Map<String, String> query = query(url);
        assertThat(query).containsEntry("client_id", "fleetpilot-legacy-portal")
                .containsEntry("response_type", "code")
                .containsEntry("redirect_uri", "http://localhost:8095/extranet/callback")
                .containsEntry("state", "s1")
                .containsEntry("nonce", "n1")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("code_challenge", OidcClient.codeChallenge("verifier"));
        assertThat(query.get("scope")).contains("openid");
    }

    @Test
    void the_sign_out_closes_the_keycloak_session_and_comes_back() {
        Map<String, String> query = query(oidc.logoutUrl("id-token", "http://localhost:8095/extranet/signed-out").toString());
        assertThat(query).containsEntry("id_token_hint", "id-token")
                .containsEntry("post_logout_redirect_uri", "http://localhost:8095/extranet/signed-out");
    }

    @Test
    void only_a_lessor_account_enters_the_extranet() {
        OidcClient.Tokens tokens = new OidcClient.Tokens("a", "r", "i", Instant.now().plusSeconds(300));
        Map<String, Object> id = oidc.claims(jwt("{\"preferred_username\":\"extranet@arval.fr\",\"name\":\"Sophie Lemoine\","
                + "\"email\":\"extranet@arval.fr\",\"nonce\":\"n1\"}"));
        Map<String, Object> lessor = Map.of("realm_access", Map.of("roles", List.of("api-supplier")), "lessor", "Arval");

        PortalUser user = PortalUser.from(id, lessor, tokens);
        assertThat(user.lessor()).isEqualTo("Arval");
        assertThat(user.name()).isEqualTo("Sophie Lemoine");
        assertThat(user.initials()).isEqualTo("SL");

        // A driver, or a supplier account without lessor, is turned away
        assertThat(PortalUser.from(id, Map.of("realm_access", Map.of("roles", List.of("driver")), "lessor", "Arval"),
                tokens)).isNull();
        assertThat(PortalUser.from(id, Map.of("realm_access", Map.of("roles", List.of("api-supplier"))), tokens)).isNull();
    }

    @Test
    void tokens_are_refreshed_a_little_before_they_expire() {
        Instant now = Instant.parse("2026-10-16T10:00:00Z");
        OidcClient.Tokens tokens = new OidcClient.Tokens("a", "r", "i", now.plusSeconds(20));
        assertThat(tokens.expiresWithin(Duration.ofSeconds(30), now)).isTrue();
        assertThat(tokens.expiresWithin(Duration.ofSeconds(10), now)).isFalse();
    }

    @Test
    void the_settings_come_from_the_environment_of_tomcat() {
        PortalConfig docker = PortalConfig.from(Map.of(
                "FLEETPILOT_ISSUER_INTERNAL", "http://keycloak:8080/realms/fleetpilot/",
                "FLEETPILOT_API_URL", "http://vehicle-service:8081")::get);
        assertThat(docker.issuer()).hasToString("http://localhost:8180/realms/fleetpilot");
        assertThat(docker.internalIssuer()).hasToString("http://keycloak:8080/realms/fleetpilot");
        assertThat(docker.apiUrl()).hasToString("http://vehicle-service:8081/");
    }
}
