package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fleetpilot.common.security.Roles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("Access rules: roles and isolation of the client companies")
class FleetSecurityIT extends IntegrationTestBase {

    /** Deterministic ids of the demo data set (and of the Keycloak demo accounts). */
    private static final String NEOLIA = "474a1f7d-c59a-3321-a0a2-729ec42501d5";
    private static final String METROPOLE = "29d53fba-827a-3e2b-bec1-e74f22fc6de9";
    private static final String CAMILLE_MARTIN = "87162d7c-e396-30cd-b37c-a6278869bd78";

    private static RequestPostProcessor neoliaManager() {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER, Map.of("company_id", NEOLIA));
    }

    private static RequestPostProcessor camille() {
        return user("camille.martin@neolia-energies.fr", Roles.DRIVER,
                Map.of("company_id", NEOLIA, "driver_id", CAMILLE_MARTIN));
    }

    private static RequestPostProcessor supplier() {
        return user("service-account-supplier-arval", Roles.API_SUPPLIER, Map.of());
    }

    private static RequestPostProcessor arvalExtranet() {
        return user("extranet@arval.fr", Roles.API_SUPPLIER, Map.of("lessor", "Arval"));
    }

    private <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read(path);
    }

    @Test
    void the_api_requires_a_token_but_health_and_documentation_are_public() throws Exception {
        mvc.perform(get("/api/v1/vehicles").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/actuator/health/readiness").with(anonymous())).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"));
    }

    @Test
    void each_role_reaches_its_own_operations_only() throws Exception {
        mvc.perform(get("/api/v1/vehicles").with(camille()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/me").with(neoliaManager())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/fleet/sweep").with(neoliaManager())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/vehicles").with(supplier())).andExpect(status().isForbidden());

        String registration = read(mvc.perform(get("/api/v1/vehicles?size=1")), "$.content[0].registration");
        mvc.perform(get("/api/v1/vehicles/by-registration/" + registration).with(supplier())).andExpect(status().isOk());
    }

    @Test
    void a_client_manager_only_sees_the_fleet_of_their_company() throws Exception {
        int neoliaFleet = read(mvc.perform(get("/api/v1/fleet/summary?companyId=" + NEOLIA)), "$.inFleet");
        ResultActions summary = mvc.perform(get("/api/v1/fleet/summary?companyId=" + METROPOLE).with(neoliaManager()));
        assertThat(this.<Integer>read(summary, "$.inFleet")).as("the requested company is ignored").isEqualTo(neoliaFleet);

        ResultActions vehicles = mvc.perform(get("/api/v1/vehicles?size=100").with(neoliaManager()));
        assertThat(this.<List<String>>read(vehicles, "$.content[*].companyId")).containsOnly(NEOLIA);
        mvc.perform(get("/api/v1/companies").with(neoliaManager()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Néolia Énergies"));

        String metropoleVehicle = read(mvc.perform(get("/api/v1/vehicles?size=1&companyId=" + METROPOLE)), "$.content[0].id");
        mvc.perform(get("/api/v1/vehicles/" + metropoleVehicle).with(neoliaManager()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
        mvc.perform(post("/api/v1/vehicles").with(neoliaManager()).contentType(MediaType.APPLICATION_JSON).content("""
                {"companyId": "%s", "registration": "ZY-001-AA", "vin": "VF1ZY000000000001", "category": "CAR",
                 "make": "Renault", "model": "Clio", "energy": "PETROL", "critAir": 1, "co2GramsPerKm": 118,
                 "firstRegistrationDate": "2026-01-10", "acquisition": "OWNED", "mileage": 10}""".formatted(METROPOLE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    void a_client_user_without_company_sees_nothing() throws Exception {
        mvc.perform(get("/api/v1/vehicles").with(user("orphan@example.org", Roles.CLIENT_MANAGER, Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void a_driver_sees_their_vehicle_and_reports_from_the_app() throws Exception {
        ResultActions me = mvc.perform(get("/api/v1/me").with(camille())).andExpect(status().isOk());
        assertThat(this.<String>read(me, "$.driver.driver.fullName")).isEqualTo("Camille Martin");
        String vehicle = read(me, "$.vehicle.id");
        int mileage = read(me, "$.vehicle.mileage");

        mvc.perform(post("/api/v1/me/mileage").with(camille()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mileage\": " + (mileage + 120) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vehicle))
                .andExpect(jsonPath("$.mileage").value(mileage + 120))
                .andExpect(jsonPath("$.mileageHistory[0].source").value("DRIVER"));

        mvc.perform(post("/api/v1/me/incidents").with(camille()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"driverId": "00000000-0000-0000-0000-000000000000", "type": "GLASS_BREAKAGE",
                         "occurredAt": "2026-10-13T17:20:00Z", "location": "A7, sortie Marseille Saint-Charles",
                         "description": "Impact sur le pare-brise"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicle.id").value(vehicle))
                .andExpect(jsonPath("$.driver.id").value(CAMILLE_MARTIN));
    }

    @Test
    void a_driver_account_without_a_driver_link_is_refused() throws Exception {
        mvc.perform(get("/api/v1/me").with(user("x@neolia-energies.fr", Roles.DRIVER, Map.of("company_id", NEOLIA))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void a_lessor_only_reaches_its_own_contracts_and_the_vehicles_it_leases() throws Exception {
        // Whatever it asks for, Arval gets the contracts of Arval
        ResultActions contracts = mvc.perform(get("/api/v1/contracts?lessor=Ayvens&status=ACTIVE&size=200")
                .with(arvalExtranet())).andExpect(status().isOk());
        List<String> lessors = read(contracts, "$.content[*].lessor");
        assertThat(lessors).isNotEmpty().containsOnly("Arval");
        String vehicleId = read(contracts, "$.content[0].vehicle.id");
        String registration = read(contracts, "$.content[0].vehicle.registration");

        String ayvens = read(mvc.perform(get("/api/v1/contracts?lessor=Ayvens&status=ACTIVE&size=1")),
                "$.content[0].id");
        String ayvensVehicle = read(mvc.perform(get("/api/v1/contracts/" + ayvens)), "$.vehicle.id");
        mvc.perform(get("/api/v1/contracts/" + ayvens).with(arvalExtranet())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/vehicles/" + ayvensVehicle + "/mileage").with(arvalExtranet()))
                .andExpect(status().isNotFound());

        // A reading at the return of its own vehicle is recorded, with the lessor as source
        mvc.perform(get("/api/v1/vehicles/by-registration/" + registration).with(arvalExtranet()))
                .andExpect(status().isOk());
        int mileage = read(mvc.perform(get("/api/v1/vehicles/" + vehicleId)), "$.mileage");
        mvc.perform(post("/api/v1/vehicles/" + vehicleId + "/mileage").with(arvalExtranet())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mileage\": %d, \"source\": \"LESSOR\"}".formatted(mileage + 120)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(mileage + 120));
        mvc.perform(get("/api/v1/vehicles/" + vehicleId + "/mileage").with(arvalExtranet()))
                .andExpect(jsonPath("$[0].source").value("LESSOR"));
        mvc.perform(post("/api/v1/vehicles/" + ayvensVehicle + "/mileage").with(arvalExtranet())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\": 999999}"))
                .andExpect(status().isNotFound());
    }
}
