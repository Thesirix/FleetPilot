package com.fleetpilot.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpilot.common.web.CorrelationIdFilter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.client.RestClient;

class ServiceRestClientsTest {

    private HttpServer server;
    private final Map<String, String> received = new ConcurrentHashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.put("method", exchange.getRequestMethod());
            received.put("path", exchange.getRequestURI().getPath());
            received.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            received.put("correlation", String.valueOf(exchange.getRequestHeaders().getFirst(CorrelationIdFilter.HEADER)));
            received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        MDC.clear();
    }

    private RestClient client() {
        ClientRegistration registration = ClientRegistration.withRegistrationId(ServiceRestClients.REGISTRATION)
                .clientId("fleetpilot-services").authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://keycloak/token").build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                Instant.now(), Instant.now().plusSeconds(300));
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any())).thenReturn(new OAuth2AuthorizedClient(registration, "integration-esb", token));
        return ServiceRestClients.create(RestClient.builder(), manager, "integration-esb",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void sends_a_patch_with_the_service_token_and_the_correlation_id() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "3f2b8c1e9a7d4e60b1c2d3e4f5a6b7c8");

        client().patch().uri("/api/v1/incidents/{id}/status", "43fec8de")
                .contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"UNDER_REVIEW\"}")
                .retrieve().toBodilessEntity();

        assertThat(received).containsEntry("method", "PATCH")
                .containsEntry("path", "/api/v1/incidents/43fec8de/status")
                .containsEntry("authorization", "Bearer service-token")
                .containsEntry("correlation", "3f2b8c1e9a7d4e60b1c2d3e4f5a6b7c8")
                .containsEntry("body", "{\"status\":\"UNDER_REVIEW\"}");
    }
}
