package com.fleetpilot.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The real webapp folder (servlets, filter, JSP, JSTL) deployed on an embedded Tomcat 11, between a fake Keycloak and
 * a fake fleet API on local ports. The test plays the browser: it follows the redirects by hand and keeps the cookie.
 */
@DisplayName("Lessor extranet on Tomcat: sign-in, contracts, readings")
class ExtranetIT {

    private static final String ARVAL_CONTRACT = "11111111-1111-1111-1111-111111111111";
    private static final String ARVAL_VEHICLE = "22222222-2222-2222-2222-222222222222";
    private static final String OTHER_CONTRACT = "33333333-3333-3333-3333-333333333333";

    private static Tomcat tomcat;
    private static HttpServer keycloak;
    private static HttpServer api;
    private static String base;
    /** The roles and lessor written in the next access token, and the nonce of the pending sign-in. */
    private static volatile String nextRoles = "[\"api-supplier\"]";
    private static volatile String nextLessor = "Arval";
    private static volatile String pendingNonce;
    private static final List<String> READINGS = new CopyOnWriteArrayList<>();
    private static final List<String> API_TOKENS = new CopyOnWriteArrayList<>();

    private HttpClient browser;

    @BeforeAll
    static void start() throws Exception {
        keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        keycloak.createContext("/realms/fleetpilot/protocol/openid-connect/token", ExtranetIT::token);
        keycloak.start();
        api = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        api.createContext("/api/v1/", ExtranetIT::api);
        api.start();

        tomcat = new Tomcat();
        File work = Files.createTempDirectory("extranet-tomcat").toFile();
        tomcat.setBaseDir(work.getAbsolutePath());
        tomcat.setPort(0);
        tomcat.getConnector();
        String issuer = "http://localhost:" + keycloak.getAddress().getPort() + "/realms/fleetpilot";
        System.setProperty("FLEETPILOT_ISSUER", issuer);
        System.setProperty("FLEETPILOT_ISSUER_INTERNAL", issuer);
        System.setProperty("FLEETPILOT_API_URL", "http://localhost:" + api.getAddress().getPort());
        Context context = tomcat.addWebapp("/extranet", new File("src/main/webapp").getAbsolutePath());
        WebResourceRoot resources = new StandardRoot(context);
        resources.addPreResources(new DirResourceSet(resources, "/WEB-INF/classes",
                new File("target/classes").getAbsolutePath(), "/"));
        context.setResources(resources);
        tomcat.start();
        int port = tomcat.getConnector().getLocalPort();
        // The public address is only known now: the listener reads it at the first request of the context
        System.setProperty("FLEETPILOT_PUBLIC_URL", "http://localhost:" + port);
        context.reload();
        base = "http://localhost:" + port + "/extranet";
    }

    @AfterAll
    static void stop() throws Exception {
        tomcat.stop();
        tomcat.destroy();
        keycloak.stop(0);
        api.stop(0);
    }

    @BeforeEach
    void newBrowser() {
        browser = HttpClient.newBuilder().cookieHandler(new CookieManager()).followRedirects(HttpClient.Redirect.NEVER)
                .build();
        nextRoles = "[\"api-supplier\"]";
        nextLessor = "Arval";
        READINGS.clear();
    }

    // ---------------------------------------------------------------- the journeys

    @Test
    void an_anonymous_visitor_is_sent_to_keycloak_with_pkce() throws Exception {
        HttpResponse<String> response = get(base + "/");
        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElseThrow();
        assertThat(location).contains("/realms/fleetpilot/protocol/openid-connect/auth?")
                .contains("client_id=fleetpilot-legacy-portal")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=" + URLEncoder.encode(base + "/callback", StandardCharsets.UTF_8));
    }

    @Test
    void a_lessor_signs_in_reads_its_contracts_and_records_a_reading() throws Exception {
        signIn("/contracts?filter=overrun");

        HttpResponse<String> home = get(base + "/contracts?filter=overrun");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("Contrats en cours", "ARV-2025-111511", "GY-727-VQ", "Sophie Lemoine", "Arval")
                .contains("+52 950 km")
                .doesNotContain("ARV-2024-100200");
        assertThat(API_TOKENS).allMatch(header -> header.startsWith("Bearer access-"));

        HttpResponse<String> contract = get(base + "/contract?id=" + ARVAL_CONTRACT);
        assertThat(contract.body()).contains("Saisir un relevé kilométrique", "86 269 km", "Boîtier connecté");
        String csrf = field(contract.body(), "_csrf");

        HttpResponse<String> posted = post(base + "/reading", Map.of("_csrf", csrf, "contract", ARVAL_CONTRACT,
                "vehicle", ARVAL_VEHICLE, "mileage", "86 519", "readAt", "2026-10-15T09:30"));
        assertThat(posted.statusCode()).isEqualTo(302);
        assertThat(posted.headers().firstValue("Location").orElseThrow()).endsWith("/contract?id=" + ARVAL_CONTRACT);
        assertThat(READINGS).singleElement().satisfies(body -> assertThat(body)
                .contains("\"mileage\":86519", "\"source\":\"LESSOR\"", "\"readAt\":\"2026-10-15T07:30:00Z\""));
        assertThat(get(base + "/contract?id=" + ARVAL_CONTRACT).body()).contains("Relevé de 86 519 km enregistré.");

        HttpResponse<String> csv = get(base + "/contracts.csv?filter=overrun");
        assertThat(csv.headers().firstValue("Content-Disposition").orElseThrow()).contains("contrats-arval-");
        assertThat(csv.body()).startsWith("﻿Contrat;").contains("ARV-2025-111511;GY-727-VQ;Renault Trucks D Wide");
    }

    @Test
    void a_form_without_its_token_and_a_contract_of_another_lessor_are_refused() throws Exception {
        signIn("/");
        HttpResponse<String> forged = post(base + "/reading", Map.of("contract", ARVAL_CONTRACT, "vehicle", ARVAL_VEHICLE,
                "mileage", "1"));
        assertThat(forged.statusCode()).isEqualTo(403);
        assertThat(READINGS).isEmpty();

        HttpResponse<String> other = get(base + "/contract?id=" + OTHER_CONTRACT);
        assertThat(other.statusCode()).isEqualTo(404);
        assertThat(other.body()).contains("pas porté par votre société");
    }

    @Test
    void an_account_that_is_not_a_lessor_is_turned_away() throws Exception {
        nextRoles = "[\"driver\"]";
        HttpResponse<String> callback = signIn("/");
        assertThat(callback.statusCode()).isEqualTo(403);
        assertThat(callback.body()).contains("pas un compte loueur", "Se connecter avec un autre compte");
        assertThat(get(base + "/").statusCode()).isEqualTo(302);
    }

    @Test
    void the_sign_out_ends_the_session_and_keycloak_too() throws Exception {
        signIn("/");
        String csrf = field(get(base + "/").body(), "_csrf");
        HttpResponse<String> out = post(base + "/logout", Map.of("_csrf", csrf));
        assertThat(out.headers().firstValue("Location").orElseThrow()).contains("/protocol/openid-connect/logout?")
                .contains("id_token_hint=");
        assertThat(get(base + "/").statusCode()).isEqualTo(302);
        assertThat(get(base + "/signed-out").body()).contains("Vous êtes déconnecté");
    }

    // ---------------------------------------------------------------- the browser

    /** Follows the sign-in: the login page of Keycloak is skipped, its answer is the callback with a code. */
    private HttpResponse<String> signIn(String path) throws Exception {
        String location = get(base + path).headers().firstValue("Location").orElseThrow();
        Map<String, String> query = Arrays.stream(URI.create(location).getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(p -> p[0], p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8)));
        pendingNonce = query.get("nonce");
        HttpResponse<String> callback = get(base + "/callback?code=code-1&state="
                + URLEncoder.encode(query.get("state"), StandardCharsets.UTF_8));
        if (callback.statusCode() == 302) {
            assertThat(callback.headers().firstValue("Location").orElseThrow()).endsWith("/extranet" + path);
        }
        return callback;
    }

    private HttpResponse<String> get(String url) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String url, Map<String, String> form) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return browser.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String html, String name) {
        Matcher matcher = Pattern.compile("name=\"" + name + "\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).as("hidden field " + name).isTrue();
        return matcher.group(1);
    }

    // ---------------------------------------------------------------- fake Keycloak and fake fleet API

    private static void token(HttpExchange exchange) throws IOException {
        String form = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                "fleetpilot-legacy-portal:dev-legacy-portal-secret".getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization")) || !form.contains("code_verifier=")) {
            reply(exchange, 401, "{\"error\":\"unauthorized_client\"}");
            return;
        }
        String id = jwt("{\"sub\":\"u1\",\"preferred_username\":\"extranet@arval.fr\",\"name\":\"Sophie Lemoine\","
                + "\"email\":\"extranet@arval.fr\",\"nonce\":\"" + pendingNonce + "\"}");
        String access = jwt("{\"sub\":\"u1\",\"realm_access\":{\"roles\":" + nextRoles + "},\"lessor\":\"" + nextLessor + "\"}");
        reply(exchange, 200, "{\"access_token\":\"access-" + access + "\",\"refresh_token\":\"r1\",\"id_token\":\"" + id
                + "\",\"expires_in\":300}");
    }

    private static void api(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        API_TOKENS.add(exchange.getRequestHeaders().getFirst("Authorization"));
        if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/mileage")) {
            READINGS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, "{}");
        } else if (path.equals("/api/v1/contracts")) {
            reply(exchange, 200, "{\"content\":[" + contract(ARVAL_CONTRACT, "ARV-2025-111511", "GY-727-VQ", 52_950) + ","
                    + contract("44444444-4444-4444-4444-444444444444", "ARV-2024-100200", "GH-482-KM", 0)
                    + "],\"page\":0,\"size\":100,\"totalElements\":2,\"totalPages\":1}");
        } else if (path.equals("/api/v1/contracts/" + ARVAL_CONTRACT)) {
            reply(exchange, 200, contract(ARVAL_CONTRACT, "ARV-2025-111511", "GY-727-VQ", 52_950));
        } else if (path.equals("/api/v1/vehicles/" + ARVAL_VEHICLE + "/mileage")) {
            reply(exchange, 200, "[{\"mileage\":86269,\"readAt\":\"2026-10-07T18:00:00Z\",\"source\":\"TELEMETRY\"}]");
        } else {
            reply(exchange, 404, "{\"code\":\"CONTRACT_NOT_FOUND\",\"detail\":\"Contract not found\"}");
        }
    }

    private static String contract(String id, String number, String registration, int overrun) {
        String projection = overrun > 0
                ? "{\"projectedMileage\":" + (165_000 + overrun) + ",\"projectedOverrunKm\":" + overrun
                        + ",\"dailyAverageKm\":120.5,\"overrunRisk\":true}"
                : "{\"projectedMileage\":150000,\"projectedOverrunKm\":-15000,\"dailyAverageKm\":80.1,\"overrunRisk\":false}";
        return "{\"id\":\"" + id + "\",\"contractNumber\":\"" + number + "\",\"vehicle\":{\"id\":\"" + ARVAL_VEHICLE
                + "\",\"registration\":\"" + registration + "\",\"make\":\"Renault Trucks\",\"model\":\"D Wide\"},"
                + "\"lessor\":\"Arval\",\"startDate\":\"2025-08-07\",\"endDate\":\"2028-08-07\",\"durationMonths\":36,"
                + "\"contractualMileage\":165000,\"startMileage\":12,\"monthlyRent\":2724.28,\"maintenanceIncluded\":false,"
                + "\"tyresIncluded\":true,\"replacementVehicle\":false,\"status\":\"ACTIVE\",\"daysRemaining\":661,"
                + "\"projection\":" + projection + "}";
    }

    private static String jwt(String payload) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", status >= 400 ? "application/problem+json" : "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
