package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@DisplayName("Fleet lifecycle, end to end through the REST API")
class FleetLifecycleIT extends IntegrationTestBase {

    @Autowired
    private JdbcClient jdbc;

    private String companyId() throws Exception {
        return this.<List<String>>read(mvc.perform(get("/api/v1/companies")), "$[?(@.segment == 'LARGE_ACCOUNT')].id")
                .getFirst();
    }

    private static String randomRegistration() {
        String letters = "ABCDEFGHJKLMNPRSTVWXYZ";
        // No S in the last letter: the pair SS is never issued and would be refused
        String lastLetters = letters.replace("S", "");
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "ZZ-%03d-".formatted(1 + r.nextInt(999))
                + letters.charAt(r.nextInt(letters.length())) + lastLetters.charAt(r.nextInt(lastLetters.length()));
    }

    private static String randomVin() {
        return "VF1ZZ%012d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    private String createVehicle(String companyId, String category, String energy, String acquisition) throws Exception {
        String body = """
                {"companyId": "%s", "registration": "%s", "vin": "%s", "category": "%s", "make": "Renault",
                 "model": "Megane E-Tech", "energy": "%s", "critAir": %d, "co2GramsPerKm": %d, "fiscalPower": 4,
                 "firstRegistrationDate": "2026-09-01", "acquisition": "%s", "mileage": 120}
                """.formatted(companyId, randomRegistration(), randomVin(), category, energy,
                "ELECTRIC".equals(energy) ? 0 : 1, "ELECTRIC".equals(energy) ? 0 : 118, acquisition);
        return read(json(post("/api/v1/vehicles"), body).andExpect(status().isCreated())
                .andExpect(header().exists("Location")), "$.id");
    }

    private String createDriver(String companyId) throws Exception {
        long n = ThreadLocalRandom.current().nextLong(1_000_000);
        String body = """
                {"companyId": "%s", "employeeNumber": "IT-%06d", "firstName": "Inès", "lastName": "Testeur",
                 "email": "ines.testeur.%d@neolia-energies.fr", "phone": "06 12 34 56 78", "licenseNumber": "120399999001",
                 "licenseCategories": ["B"], "licenseIssuedOn": "2012-03-01", "licenseExpiresOn": "2033-01-19"}
                """.formatted(companyId, n, n);
        return read(json(post("/api/v1/drivers"), body).andExpect(status().isCreated()), "$.driver.id");
    }

    @Test
    void a_vehicle_from_entry_into_the_fleet_to_disposal() throws Exception {
        String company = companyId();
        String vehicle = createVehicle(company, "CAR", "ELECTRIC", "LEASE");
        mvc.perform(get("/api/v1/vehicles/" + vehicle))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.openDeadlines.length()").value(3))
                .andExpect(jsonPath("$.openDeadlines[?(@.type == 'SERVICE')].dueMileage").value(30_000));

        String driver = createDriver(company);
        String assignment = read(json(post("/api/v1/vehicles/" + vehicle + "/assignments"),
                "{\"driverId\": \"" + driver + "\"}").andExpect(status().isCreated()), "$.id");

        json(post("/api/v1/vehicles/" + vehicle + "/mileage"), "{\"mileage\": 2450, \"source\": \"DRIVER\"}")
                .andExpect(jsonPath("$.mileage").value(2450))
                .andExpect(jsonPath("$.currentAssignment.driver.fullName").value("Inès Testeur"));

        json(post("/api/v1/vehicles/" + vehicle + "/incidents"), """
                {"type": "GLASS_BREAKAGE", "occurredAt": "2026-10-13T07:45:00Z", "location": "A50, La Ciotat",
                 "description": "Impact de gravillon sur le pare-brise"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(startsWith("SIN-2026-")))
                .andExpect(jsonPath("$.driver.id").value(driver))
                .andExpect(jsonPath("$.status").value("DECLARED"));

        json(post("/api/v1/vehicles/" + vehicle + "/contracts"), """
                {"lessor": "Ayvens", "contractNumber": "AYV-IT-%d", "startDate": "2026-09-01", "durationMonths": 48,
                 "contractualMileage": 120000, "monthlyRent": 459.90, "maintenanceIncluded": true}"""
                .formatted(ThreadLocalRandom.current().nextInt(1_000_000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endDate").value("2030-09-01"))
                .andExpect(jsonPath("$.totalRent").value(22075.2));

        json(patch("/api/v1/vehicles/" + vehicle + "/status"), "{\"status\": \"DISPOSED\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VEHICLE_STILL_ASSIGNED"));

        json(post("/api/v1/assignments/" + assignment + "/end"), "{\"endMileage\": 2600}")
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.drivenKilometres").value(2480));

        String contract = read(mvc.perform(get("/api/v1/vehicles/" + vehicle)), "$.activeContract.id");
        json(patch("/api/v1/vehicles/" + vehicle + "/status"), "{\"status\": \"DISPOSED\"}")
                .andExpect(jsonPath("$.code").value("CONTRACT_STILL_ACTIVE"));
        json(post("/api/v1/contracts/" + contract + "/end"), "{}").andExpect(jsonPath("$.status").value("ENDED"));

        json(patch("/api/v1/vehicles/" + vehicle + "/status"), "{\"status\": \"DISPOSED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPOSED"))
                .andExpect(jsonPath("$.openDeadlines.length()").value(0))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(0));

        List<String> events = jdbc.sql("select event_type from outbox_event where aggregate_id = cast(:id as uuid) "
                + "order by position").param("id", vehicle).query(String.class).list();
        assertThat(events).containsExactly("vehicle.created", "driver.assigned", "incident.declared", "driver.unassigned",
                "vehicle.status-changed");
    }

    @Test
    void completing_a_deadline_schedules_the_next_one() throws Exception {
        String vehicle = createVehicle(companyId(), "VAN", "DIESEL", "OWNED");
        String inspection = read(mvc.perform(get("/api/v1/deadlines?vehicleId=" + vehicle + "&type=SERVICE")),
                "$.content[0].id");

        json(post("/api/v1/deadlines/" + inspection + "/complete"), "{\"mileage\": 4000}")
                .andExpect(jsonPath("$.completed.status").value("DONE"))
                .andExpect(jsonPath("$.next.dueDate").value("2027-10-14"))
                .andExpect(jsonPath("$.next.dueMileage").value(34_000));

        mvc.perform(get("/api/v1/vehicles/" + vehicle)).andExpect(jsonPath("$.mileage").value(4000));
    }

    @Test
    void the_rules_are_enforced_with_explicit_codes() throws Exception {
        String company = companyId();
        String truck = createVehicle(company, "TRUCK", "DIESEL", "OWNED");
        String driver = createDriver(company);

        json(post("/api/v1/vehicles/" + truck + "/assignments"), "{\"driverId\": \"" + driver + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LICENSE_NOT_VALID"));
        json(post("/api/v1/vehicles/" + truck + "/contracts"), """
                {"lessor": "Arval", "contractNumber": "X-1", "startDate": "2026-09-01", "durationMonths": 36,
                 "contractualMileage": 90000, "monthlyRent": 1990}""")
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_LEASED"));
        json(post("/api/v1/vehicles/" + truck + "/mileage"), "{\"mileage\": 10}")
                .andExpect(jsonPath("$.code").value("MILEAGE_DECREASE"));

        String registration = read(mvc.perform(get("/api/v1/vehicles/" + truck)), "$.registration");
        mvc.perform(get("/api/v1/vehicles/by-registration/" + registration.replace("-", " ").toLowerCase()))
                .andExpect(jsonPath("$.id").value(truck));
        json(put("/api/v1/vehicles/" + truck), """
                {"registration": "%s", "category": "TRUCK", "make": "Renault", "model": "D Wide", "energy": "DIESEL",
                 "critAir": 2, "co2GramsPerKm": 780, "firstRegistrationDate": "2026-09-01", "version": 42}"""
                .formatted(registration))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void a_vehicle_file_is_updated_with_optimistic_locking() throws Exception {
        String company = companyId();
        String vehicle = createVehicle(company, "CAR", "PETROL", "OWNED");
        int version = read(mvc.perform(get("/api/v1/vehicles/" + vehicle)), "$.version");
        String site = read(mvc.perform(get("/api/v1/companies/" + company + "/sites")), "$[0].id");
        String newRegistration = randomRegistration();

        json(put("/api/v1/vehicles/" + vehicle), """
                {"registration": "%s", "siteId": "%s", "category": "CAR", "make": "Peugeot", "model": "308 SW",
                 "trimLevel": "Hybrid 180", "energy": "PLUG_IN_HYBRID", "critAir": 1, "co2GramsPerKm": 26,
                 "fiscalPower": 8, "firstRegistrationDate": "2026-09-01", "version": %d}"""
                .formatted(newRegistration.toLowerCase(), site, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration").value(newRegistration))
                .andExpect(jsonPath("$.model").value("308 SW"))
                .andExpect(jsonPath("$.site.id").value(site))
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void a_driver_is_updated_and_can_only_be_deactivated_without_a_vehicle() throws Exception {
        String company = companyId();
        String driver = createDriver(company);
        String vehicle = createVehicle(company, "VAN", "ELECTRIC", "LEASE");
        String assignment = read(json(post("/api/v1/vehicles/" + vehicle + "/assignments"),
                "{\"driverId\": \"" + driver + "\"}"), "$.id");
        String update = """
                {"employeeNumber": "%s", "firstName": "Inès", "lastName": "Martin-Testeur",
                 "email": "%s", "phone": "07 98 76 54 32", "licenseNumber": "120399999001",
                 "licenseCategories": ["B", "C"], "licenseIssuedOn": "2012-03-01", "licenseExpiresOn": "2033-01-19",
                 "active": %s, "version": %d}""";
        ResultActions current = mvc.perform(get("/api/v1/drivers/" + driver));
        String employeeNumber = read(current, "$.driver.employeeNumber");
        String email = read(current, "$.driver.email");

        json(put("/api/v1/drivers/" + driver), update.formatted(employeeNumber, email, false, 0))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DRIVER_HAS_VEHICLE"));

        json(post("/api/v1/assignments/" + assignment + "/end"), "{\"endMileage\": 900}").andExpect(status().isOk());
        int version = read(mvc.perform(get("/api/v1/drivers/" + driver)), "$.version");
        json(put("/api/v1/drivers/" + driver), update.formatted(employeeNumber, email, false, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.fullName").value("Inès Martin-Testeur"))
                .andExpect(jsonPath("$.driver.active").value(false))
                .andExpect(jsonPath("$.driver.licenseCategories.length()").value(2))
                .andExpect(jsonPath("$.assignments.length()").value(1))
                .andExpect(jsonPath("$.drivenKilometres").value(780));

        mvc.perform(get("/api/v1/drivers?q=martin-testeur&active=false"))
                .andExpect(jsonPath("$.content[0].id").value(driver));
    }

    @Test
    void invalid_requests_list_every_error() throws Exception {
        json(post("/api/v1/vehicles"), "{\"registration\": \"AB-000-CD\", \"vin\": \"VF1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field == 'registration')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'vin')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'companyId')]").exists());
        mvc.perform(get("/api/v1/vehicles/6b0c7c3e-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_FOUND"));
        mvc.perform(get("/api/v1/vehicles?sort=password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void lists_are_paginated_and_filtered_in_the_database() throws Exception {
        Map<String, Object> page = read(mvc.perform(get("/api/v1/vehicles?status=ACTIVE&category=CAR&size=5&page=1")), "$");
        assertThat(page).containsEntry("page", 1).containsEntry("size", 5);
        assertThat((List<?>) page.get("content")).hasSize(5);
        assertThat(this.<List<String>>read(mvc.perform(get("/api/v1/vehicles?status=ACTIVE&category=CAR&size=100")),
                "$.content[*].category")).containsOnly("CAR");

        int all = this.<Integer>read(mvc.perform(get("/api/v1/vehicles")), "$.totalElements");
        int assigned = this.<Integer>read(mvc.perform(get("/api/v1/vehicles?assigned=true")), "$.totalElements");
        int unassigned = this.<Integer>read(mvc.perform(get("/api/v1/vehicles?assigned=false")), "$.totalElements");
        assertThat(assigned + unassigned).isEqualTo(all);

        String registration = read(mvc.perform(get("/api/v1/vehicles?size=1")), "$.content[0].registration");
        mvc.perform(get("/api/v1/vehicles?q=" + registration.replace("-", "").toLowerCase()))
                .andExpect(jsonPath("$.content[0].registration").value(registration));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read(path);
    }
}
