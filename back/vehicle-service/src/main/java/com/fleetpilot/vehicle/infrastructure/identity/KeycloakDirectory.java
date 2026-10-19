package com.fleetpilot.vehicle.infrastructure.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.IdentityDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The user accounts in Keycloak, through its admin REST API, with the {@code fleetpilot-admin-api} client: a service
 * account that may only view and manage the users of the realm. The token is kept until shortly before it expires.
 */
@Component
public class KeycloakDirectory implements IdentityDirectory {

    private static final List<String> ROLES = List.of(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.CLIENT_MANAGER,
            Roles.DRIVER, Roles.GARAGE, Roles.API_SUPPLIER);
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private String token;
    private Instant tokenExpiry = Instant.EPOCH;

    public KeycloakDirectory(@Value("${fleetpilot.identity.keycloak-url:http://localhost:8180}") String keycloakUrl,
            @Value("${fleetpilot.identity.realm:fleetpilot}") String realm,
            @Value("${fleetpilot.identity.client-id:fleetpilot-admin-api}") String clientId,
            @Value("${fleetpilot.identity.client-secret:dev-fleetpilot-admin-secret}") String clientSecret, Clock clock) {
        JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        http.setReadTimeout(Duration.ofSeconds(15));
        this.rest = RestClient.builder().baseUrl(keycloakUrl).requestFactory(http).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public List<Account> accounts() {
        Map<String, String> roles = new HashMap<>();
        for (String role : ROLES) {
            for (Map<String, Object> user : get("/roles/" + role + "/users?first=0&max=2000")) {
                roles.putIfAbsent((String) user.get("id"), role);
            }
        }
        List<Account> accounts = new ArrayList<>();
        for (Map<String, Object> user : get("/users?first=0&max=2000&briefRepresentation=false")) {
            String username = (String) user.get("username");
            if (username == null || username.startsWith("service-account-")) {
                continue;
            }
            String id = (String) user.get("id");
            // Roles of directory users come from LDAP groups: they are only visible on the user itself
            String role = roles.get(id);
            if (role == null && user.get("federationLink") != null) {
                role = roleOf(id);
            }
            accounts.add(account(user, role));
        }
        return accounts;
    }

    @Override
    public Optional<Account> account(String id) {
        try {
            Map<String, Object> user = rest.get().uri(admin("/users/{id}"), id).headers(h -> h.setBearerAuth(token()))
                    .retrieve().body(MAP);
            return Optional.ofNullable(user).map(u -> account(u, roleOf(id)));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Account> accountsWith(String attribute, String value) {
        return get("/users?briefRepresentation=false&q=" + attribute + ":" + value).stream()
                .map(user -> account(user, null))
                .toList();
    }

    @Override
    public String create(AccountData data, String temporaryPassword) {
        Map<String, Object> body = representation(data);
        body.put("emailVerified", true);
        body.put("credentials", List.of(Map.of("type", "password", "value", temporaryPassword, "temporary", true)));
        URI location;
        try {
            location = rest.post().uri(admin("/users")).headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity().getHeaders()
                    .getLocation();
        } catch (HttpClientErrorException.Conflict e) {
            throw new ConflictException("USER_EMAIL_TAKEN", "An account already uses " + data.email());
        }
        if (location == null) {
            throw new IllegalStateException("Keycloak did not return the id of the new user");
        }
        String path = location.getPath();
        String id = path.substring(path.lastIndexOf('/') + 1);
        setRole(id, data.role());
        return id;
    }

    @Override
    public void update(String id, AccountData data) {
        rest.put().uri(admin("/users/{id}"), id).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON).body(representation(data)).retrieve().toBodilessEntity();
        if (data.role() != null) {
            setRole(id, data.role());
        }
    }

    @Override
    public void resetPassword(String id, String temporaryPassword) {
        rest.put().uri(admin("/users/{id}/reset-password"), id).headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", temporaryPassword, "temporary", true))
                .retrieve().toBodilessEntity();
    }

    // ---------------------------------------------------------------- helpers

    /** Keeps one FleetPilot role on the account: the new one is added, the others removed. */
    private void setRole(String id, String role) {
        List<Map<String, Object>> current = get("/users/" + id + "/role-mappings/realm");
        List<Map<String, Object>> obsolete = current.stream()
                .filter(r -> ROLES.contains((String) r.get("name")) && !role.equals(r.get("name")))
                .toList();
        if (!obsolete.isEmpty()) {
            rest.method(HttpMethod.DELETE).uri(admin("/users/{id}/role-mappings/realm"), id)
                    .headers(h -> h.setBearerAuth(token())).contentType(MediaType.APPLICATION_JSON).body(obsolete)
                    .retrieve().toBodilessEntity();
        }
        if (current.stream().noneMatch(r -> role.equals(r.get("name")))) {
            Map<String, Object> representation = rest.get().uri(admin("/roles/{role}"), role)
                    .headers(h -> h.setBearerAuth(token())).retrieve().body(MAP);
            rest.post().uri(admin("/users/{id}/role-mappings/realm"), id).headers(h -> h.setBearerAuth(token()))
                    .contentType(MediaType.APPLICATION_JSON).body(List.of(representation)).retrieve().toBodilessEntity();
        }
    }

    private String roleOf(String id) {
        return get("/users/" + id + "/role-mappings/realm/composite").stream()
                .map(r -> (String) r.get("name"))
                .filter(ROLES::contains)
                .min((a, b) -> Integer.compare(ROLES.indexOf(a), ROLES.indexOf(b)))
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static Account account(Map<String, Object> user, String role) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (user.get("attributes") instanceof Map<?, ?> map) {
            map.forEach((key, values) -> {
                if (values instanceof List<?> list && !list.isEmpty()) {
                    attributes.put((String) key, String.valueOf(list.getFirst()));
                }
            });
        }
        Object created = user.get("createdTimestamp");
        return new Account((String) user.get("id"), (String) user.get("username"), (String) user.get("email"),
                (String) user.get("firstName"), (String) user.get("lastName"), Boolean.TRUE.equals(user.get("enabled")),
                role, attributes, user.get("federationLink") != null,
                created instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : null);
    }

    private static Map<String, Object> representation(AccountData data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", data.username());
        body.put("email", data.email());
        body.put("firstName", data.firstName());
        body.put("lastName", data.lastName());
        body.put("enabled", data.enabled());
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (String name : List.of("company_id", "driver_id", "garage_id", "lessor")) {
            attributes.put(name, data.attributes().containsKey(name) ? List.of(data.attributes().get(name)) : List.of());
        }
        body.put("attributes", attributes);
        return body;
    }

    private List<Map<String, Object>> get(String path) {
        List<Map<String, Object>> result = rest.get().uri(admin(path)).headers(h -> h.setBearerAuth(token()))
                .retrieve().body(LIST);
        return result == null ? List.of() : result;
    }

    private String admin(String path) {
        return "/admin/realms/" + realm + path;
    }

    /** A client credentials token, renewed thirty seconds before it expires. */
    private synchronized String token() {
        if (token == null || clock.instant().isAfter(tokenExpiry)) {
            LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            Map<String, Object> response = rest.post().uri("/realms/" + realm + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
            if (response == null || response.get("access_token") == null) {
                throw new IllegalStateException("Keycloak refused the token of " + clientId);
            }
            token = (String) response.get("access_token");
            long seconds = response.get("expires_in") instanceof Number n ? n.longValue() : 60;
            tokenExpiry = clock.instant().plusSeconds(Math.max(10, seconds - 30));
        }
        return token;
    }
}
