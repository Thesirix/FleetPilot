package com.fleetpilot.energy;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.energy.application.Sources.FleetData;
import com.fleetpilot.energy.application.Sources.UsageData;
import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The energy service on a real PostgreSQL, with a fleet of two companies and their usage of the last 90 days. */
@SpringBootTest(properties = "fleetpilot.energy.jobs-enabled=false")
@AutoConfigureMockMvc
@Import(EnergyApiIT.Fleet.class)
@DisplayName("Energy transition API")
class EnergyApiIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    static {
        POSTGRES.start();
    }

    /** 16 October 2026. */
    static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
    static final UUID NEOLIA = UUID.nameUUIDFromBytes("company-neolia".getBytes());
    static final UUID METROPOLE = UUID.nameUUIDFromBytes("company-metropole".getBytes());
    static final UUID CITY_CAR = UUID.nameUUIDFromBytes("city-car".getBytes());

    @Autowired
    private MockMvc mvc;

    @TestConfiguration
    static class Fleet {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Paris"));
        }

        /** Neolia: a city car, a van doing long rounds, an electric car, an owned van without box; Metropole: a car. */
        @Bean
        @Primary
        FleetData stubFleet() {
            return () -> List.of(
                    facts(CITY_CAR, NEOLIA, "GH-482-KM", "CAR|DIESEL|Clio V|118", "Marseille", 2,
                            LocalDate.of(2027, 2, 28), "339.00"),
                    facts(UUID.nameUUIDFromBytes("long-van".getBytes()), NEOLIA, "FT-118-BC", "VAN|DIESEL|Master|210",
                            "Lyon", 2, LocalDate.of(2027, 6, 30), "690.00"),
                    facts(UUID.nameUUIDFromBytes("ev".getBytes()), NEOLIA, "GE-907-RT", "CAR|ELECTRIC|Mégane E-Tech|0",
                            "Lyon", 0, LocalDate.of(2028, 1, 31), "449.00"),
                    facts(UUID.nameUUIDFromBytes("old-van".getBytes()), NEOLIA, "EA-301-PL", "VAN|DIESEL|Kangoo|140",
                            "Marseille", 3, null, null),
                    facts(UUID.nameUUIDFromBytes("metropole-car".getBytes()), METROPOLE, "FZ-902-AH",
                            "CAR|PETROL|308|128", "Marseille", 1, LocalDate.of(2026, 12, 31), "359.00"));
        }

        @Bean
        @Primary
        UsageData stubUsage() {
            return days -> {
                Map<UUID, Usage> usage = new HashMap<>();
                usage.put(CITY_CAR, usage(60, 45));
                usage.put(UUID.nameUUIDFromBytes("long-van".getBytes()), usageWithLongDays(60, 130, 400, 3));
                usage.put(UUID.nameUUIDFromBytes("metropole-car".getBytes()), usage(55, 60));
                return usage;
            };
        }

        /** @param spec category, energy, model and CO2 in g/km, separated by "|" */
        static VehicleFacts facts(UUID id, UUID company, String registration, String spec, String city, int critAir,
                LocalDate contractEnd, String rent) {
            String[] parts = spec.split("\\|");
            String category = parts[0];
            String energy = parts[1];
            String model = parts[2];
            int co2 = Integer.parseInt(parts[3]);
            return new VehicleFacts(id, company, company.equals(NEOLIA) ? "Néolia Énergies" : "Métropole Azur Provence",
                    registration, "Renault", model, category, energy, critAir, co2, city, 64_000,
                    LocalDate.of(2020, 6, 1), contractEnd == null ? "OWNED" : "LEASED", contractEnd,
                    rent == null ? null : new BigDecimal(rent));
        }

        static Usage usage(int days, int km) {
            return usageWithLongDays(days, km, km, 1);
        }

        /** Every {@code every}-th day is a long day of {@code longKm}. */
        static Usage usageWithLongDays(int days, int km, int longKm, int every) {
            List<BigDecimal> distances = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                distances.add(BigDecimal.valueOf(i % every == 0 && every > 1 ? longKm : km));
            }
            return new Usage(90, distances);
        }
    }

    private static RequestPostProcessor staff() {
        return user("claire.morel", Roles.FLEET_MANAGER, Map.of());
    }

    private static RequestPostProcessor clientManager(UUID company) {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER, Map.of(CurrentUser.COMPANY_CLAIM, company.toString()));
    }

    private static RequestPostProcessor user(String username, String role, Map<String, Object> claims) {
        return jwt().jwt(token -> {
            token.claim("preferred_username", username).claim("realm_access", Map.of("roles", List.of(role)));
            claims.forEach(token::claim);
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @BeforeEach
    void assess() throws Exception {
        mvc.perform(post("/api/v1/energy/assessments/refresh").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles").value(5));
    }

    @Test
    void each_vehicle_gets_its_assessment_and_the_fleet_its_summary() throws Exception {
        mvc.perform(get("/api/v1/energy/assessments/{id}", CITY_CAR).with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.evModel.code").value("R5-ETECH"))
                .andExpect(jsonPath("$.evModel.realisticRangeKm").value(287))
                .andExpect(jsonPath("$.dataSource").value("TELEMETRY"))
                .andExpect(jsonPath("$.renewalDate").value("2027-02-28"));

        mvc.perform(get("/api/v1/energy/summary").with(staff()))
                .andExpect(jsonPath("$.vehicles").value(5))
                .andExpect(jsonPath("$.zeroEmission").value(1))
                .andExpect(jsonPath("$.eligible").value(2))
                .andExpect(jsonPath("$.notYet").value(1))
                .andExpect(jsonPath("$.toConfirm").value(1))
                .andExpect(jsonPath("$.zeroEmissionShare").value(20.0))
                .andExpect(jsonPath("$.potentialShare").value(60.0))
                .andExpect(jsonPath("$.zfeExposed").value(1))
                .andExpect(jsonPath("$.zfeBannedNow").value(1));

        mvc.perform(get("/api/v1/energy/assessments").param("status", "NOT_YET").with(staff()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].registration").value("FT-118-BC"))
                .andExpect(jsonPath("$.content[0].daysBeyondRange").value(20));
    }

    @Test
    void the_plan_switches_the_eligible_vehicles_when_their_lease_ends_and_checks_the_quotas() throws Exception {
        mvc.perform(get("/api/v1/energy/plan").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quarters[0].quarter").value("T4 2026"))
                .andExpect(jsonPath("$.quarters[0].vehicles").value(1))
                .andExpect(jsonPath("$.quarters[1].quarter").value("T1 2027"))
                .andExpect(jsonPath("$.quarters[1].vehicles").value(1))
                .andExpect(jsonPath("$.quarters[1].zeroEmissionShareAfter").value(60.0))
                .andExpect(jsonPath("$.quotas[0].year").value(2026))
                .andExpect(jsonPath("$.quotas[0].objective").value(20))
                .andExpect(jsonPath("$.quotas[1].year").value(2027))
                .andExpect(jsonPath("$.quotas[1].objective").value(40));
    }

    @Test
    void a_client_manager_only_sees_their_company() throws Exception {
        mvc.perform(get("/api/v1/energy/assessments").with(clientManager(METROPOLE)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[*].companyName", everyItem(is("Métropole Azur Provence"))));
        mvc.perform(get("/api/v1/energy/assessments/{id}", CITY_CAR).with(clientManager(METROPOLE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/energy/summary").param("companyId", NEOLIA.toString()).with(clientManager(METROPOLE)))
                .andExpect(jsonPath("$.vehicles").value(1));
        mvc.perform(post("/api/v1/energy/assessments/refresh").with(clientManager(METROPOLE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/energy/catalog").with(clientManager(METROPOLE)))
                .andExpect(jsonPath("$[*].code", hasItem("KANGOO-ET")));
    }

    private static RequestPostProcessor admin() {
        return user("theo.lambert", Roles.PLATFORM_ADMIN, Map.of());
    }

    @Test
    void an_administrator_adds_and_removes_an_electric_model_and_a_low_emission_zone() throws Exception {
        String model = """
                {"make": "Cypress", "model": "Test 90 kWh", "category": "VAN", "segment": "MEDIUM", "batteryKwh": 90,
                 "wltpRangeKm": 520, "consumptionKwh100km": 21.5, "fastChargeKw": 150, "monthlyRent": 9999}""";
        mvc.perform(put("/api/v1/energy/catalog/{code}", "CY-TEST").with(staff())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(model))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/energy/catalog/{code}", "CY-TEST").with(admin())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(model.replace("520", "20")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/energy/catalog/{code}", "CY-TEST").with(admin())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(model))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicles").isNumber());
        mvc.perform(get("/api/v1/energy/catalog").with(staff()))
                .andExpect(jsonPath("$[?(@.code == 'CY-TEST')].realisticRangeKm", hasItem(364)));
        mvc.perform(delete("/api/v1/energy/catalog/{code}", "CY-TEST").with(admin())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/energy/catalog").with(staff())).andExpect(jsonPath("$[*].code", not(hasItem("CY-TEST"))));

        mvc.perform(put("/api/v1/energy/zones/{city}", "Cypressville").with(admin())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"zone\": \"ZFE de Cypressville\", \"maxCritAir\": 3, \"effectiveFrom\": \"2027-01-01\", "
                                + "\"status\": \"ANNOUNCED\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/energy/zones").with(staff()))
                .andExpect(jsonPath("$[*].city", hasItem("Cypressville")));
        mvc.perform(delete("/api/v1/energy/zones/{city}", "Cypressville").with(admin())).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/energy/zones/{city}", "Cypressville").with(admin())).andExpect(status().isNotFound());
    }
}
