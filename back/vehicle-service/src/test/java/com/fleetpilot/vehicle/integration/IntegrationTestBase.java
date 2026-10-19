package com.fleetpilot.vehicle.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fleetpilot.common.security.Roles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Full application against a real PostgreSQL (Testcontainers), loaded with the deterministic demo data set and a
 * fixed clock, so that every date-based rule is reproducible. The container is shared by all integration test classes;
 * tests that write create their own vehicles and drivers instead of modifying the demo ones.
 */
@SpringBootTest(properties = {
    "fleetpilot.demo-data.enabled=true",
    "fleetpilot.outbox.relay.enabled=false",
    "fleetpilot.sweep.enabled=false",
    "fleetpilot.telemetry-mileage.enabled=false",
    "fleetpilot.vehicle-alerts.enabled=false",
    "spring.kafka.admin.auto-create=false"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.FixedClock.class)
public abstract class IntegrationTestBase {

    /** 14 October 2026, 10:00 in Paris. */
    public static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    /** A FleetPilot administrator: the identity of every request that does not set its own token. */
    public static RequestPostProcessor admin() {
        return user("theo.lambert", Roles.PLATFORM_ADMIN, Map.of());
    }

    /** A token as issued by the FleetPilot Keycloak realm: one realm role and the FleetPilot claims. */
    public static RequestPostProcessor user(String username, String role, Map<String, Object> claims) {
        return jwt()
                .jwt(token -> {
                    token.claim("preferred_username", username)
                            .claim("realm_access", Map.of("roles", List.of(role)));
                    claims.forEach(token::claim);
                })
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Paris"));
        }

        @Bean
        MockMvcBuilderCustomizer authenticatedByDefault() {
            return builder -> builder.defaultRequest(get("/").with(admin()));
        }
    }
}
