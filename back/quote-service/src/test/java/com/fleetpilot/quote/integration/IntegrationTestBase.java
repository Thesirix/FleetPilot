package com.fleetpilot.quote.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.quote.application.VehicleDirectory;
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
 * Full application against a real PostgreSQL (Testcontainers) with the demo garages and quotes, a fixed clock, and a
 * fleet module replaced by an in-memory directory. Kafka is not needed: the outbox is checked in the database.
 */
@SpringBootTest(properties = {
    "fleetpilot.demo-data.enabled=true",
    "fleetpilot.outbox.relay.enabled=false",
    "fleetpilot.fleet-events.enabled=false",
    "spring.kafka.admin.auto-create=false"
})
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestBeans.class)
public abstract class IntegrationTestBase {

    /** 14 October 2026, 10:00 in Paris. */
    public static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    public static final UUID NEOLIA = UUID.nameUUIDFromBytes("company-neolia".getBytes());
    public static final UUID METROPOLE = UUID.nameUUIDFromBytes("company-metropole".getBytes());
    /** Garage du Prado (Marseille), the garage of the demo account. */
    public static final UUID PRADO = UUID.nameUUIDFromBytes("fleetpilot-demo:garage:MRS-01".getBytes());
    /** Carrosserie du Vieux-Port (Marseille). */
    public static final UUID VIEUX_PORT = UUID.nameUUIDFromBytes("fleetpilot-demo:garage:MRS-02".getBytes());
    /** A car of Néolia Énergies based in Marseille, known by the fleet module but not yet copied locally. */
    public static final String LATE_CAR = "GH-482-KM";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    public static RequestPostProcessor admin() {
        return user("theo.lambert", Roles.PLATFORM_ADMIN, Map.of());
    }

    public static RequestPostProcessor manager() {
        return user("claire.morel", Roles.FLEET_MANAGER, Map.of());
    }

    public static RequestPostProcessor clientManager(UUID company) {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER, Map.of(CurrentUser.COMPANY_CLAIM, company.toString()));
    }

    public static RequestPostProcessor garage(UUID garage) {
        return user("atelier@garage-du-prado.fr", Roles.GARAGE, Map.of(CurrentUser.GARAGE_CLAIM, garage.toString()));
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

    /** The fleet module, in memory: 240 vehicles of two companies, plus one known only remotely. */
    static final class StubVehicleDirectory implements VehicleDirectory {

        private final List<RemoteVehicle> synced = new ArrayList<>();
        private final RemoteVehicle late = new RemoteVehicle(UUID.nameUUIDFromBytes("late".getBytes()), NEOLIA, LATE_CAR,
                "Peugeot", "308 SW", "CAR", "DIESEL", "Marseille");

        StubVehicleDirectory() {
            String[] categories = {"CAR", "CAR", "VAN", "TRUCK"};
            String[] energies = {"DIESEL", "PETROL", "ELECTRIC", "HYBRID"};
            for (int i = 0; i < 240; i++) {
                synced.add(new RemoteVehicle(UUID.nameUUIDFromBytes(("vehicle-" + i).getBytes()),
                        i % 2 == 0 ? NEOLIA : METROPOLE, "AB-%03d-CD".formatted(100 + i), "Renault", "Clio",
                        categories[i % 4], energies[i % 4], i % 3 == 0 ? "Lyon" : "Marseille"));
            }
        }

        @Override
        public Optional<RemoteVehicle> findByRegistration(String registration) {
            return LATE_CAR.equals(registration) ? Optional.of(late) : Optional.empty();
        }

        @Override
        public List<RemoteVehicle> findAll() {
            return synced;
        }
    }

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Paris"));
        }

        @Bean
        @Primary
        VehicleDirectory stubVehicleDirectory() {
            return new StubVehicleDirectory();
        }

        @Bean
        MockMvcBuilderCustomizer authenticatedByDefault() {
            return builder -> builder.defaultRequest(get("/").with(admin()));
        }
    }
}
