package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.domain.repository.VehicleAlertRepository;
import com.fleetpilot.vehicle.infrastructure.messaging.VehicleAlertListener;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Driver app: fill-ups, inspections, photos and notifications, replayed safely by the offline queue")
class DriverAppIT extends IntegrationTestBase {

    private static final String NEOLIA = "474a1f7d-c59a-3321-a0a2-729ec42501d5";
    private static final String METROPOLE = "29d53fba-827a-3e2b-bec1-e74f22fc6de9";
    /** A few bytes are enough: the service stores the image as it is sent. */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Autowired
    private VehicleAlertRepository alertRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcClient jdbc;

    private static RequestPostProcessor camille() {
        return user("camille.martin@neolia-energies.fr", Roles.DRIVER,
                Map.of("company_id", NEOLIA, "driver_id", "87162d7c-e396-30cd-b37c-a6278869bd78"));
    }

    private static RequestPostProcessor jean() {
        return user("jean.dupont@neolia-energies.fr", Roles.DRIVER,
                Map.of("company_id", NEOLIA, "driver_id", "23f33fb7-f4c2-371c-93f9-e1f915b8bd15"));
    }

    private static RequestPostProcessor neoliaManager() {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER, Map.of("company_id", NEOLIA));
    }

    private static RequestPostProcessor metropoleManager() {
        return user("p.bianchi@azur-provence-metropole.fr", Roles.CLIENT_MANAGER, Map.of("company_id", METROPOLE));
    }

    private <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.parse(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).read(path);
    }

    private ResultActions putJson(String url, RequestPostProcessor who, String body) throws Exception {
        return mvc.perform(put(url).with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void the_demo_receipts_and_inspections_follow_the_odometer_of_their_vehicle() {
        assertThat(jdbc.sql("select count(*) from fuel_entry").query(Long.class).single()).isGreaterThan(1000);
        // No odometer of a receipt or an inspection contradicts a reading taken before or after it
        assertThat(jdbc.sql("""
                select count(*) from fuel_entry f where exists (select 1 from mileage_reading r
                  where r.vehicle_id = f.vehicle_id and (r.read_at < f.filled_at and r.mileage > f.mileage
                     or r.read_at > f.filled_at and r.mileage < f.mileage))""").query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("""
                select count(*) from inspection i where exists (select 1 from mileage_reading r
                  where r.vehicle_id = i.vehicle_id and (r.read_at < i.performed_at and r.mileage > i.mileage
                     or r.read_at > i.performed_at and r.mileage < i.mileage))""").query(Long.class).single())
                .isZero();
    }

    @Test
    void a_fill_up_sent_twice_is_recorded_once_and_moves_the_odometer() throws Exception {
        ResultActions me = mvc.perform(get("/api/v1/me").with(camille())).andExpect(status().isOk());
        String vehicle = read(me, "$.vehicle.id");
        int mileage = read(me, "$.vehicle.mileage");
        String id = UUID.randomUUID().toString();
        String body = """
                {"filledAt": "%s", "kind": "FUEL", "quantity": 40.12, "amount": 72.50, "mileage": %d,
                 "station": "TotalEnergies Lyon Gerland", "payment": "FUEL_CARD"}""".formatted(NOW, mileage + 50);

        String first = putJson("/api/v1/me/fuel-entries/" + id, camille(), body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(1.807))
                .andExpect(jsonPath("$.driver.fullName").value("Camille Martin"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String replay = putJson("/api/v1/me/fuel-entries/" + id, camille(), body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(replay).isEqualTo(first);

        List<String> ids = read(mvc.perform(get("/api/v1/me/fuel-entries").with(camille())), "$[*].id");
        assertThat(ids).containsOnlyOnce(id);
        mvc.perform(get("/api/v1/me").with(camille())).andExpect(jsonPath("$.vehicle.mileage").value(mileage + 50));

        // the id of somebody else's record cannot be reused
        putJson("/api/v1/me/fuel-entries/" + id, jean(), body)
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("ID_ALREADY_USED"));

        // the fleet manager sees it on the vehicle; another company does not see the vehicle at all
        List<String> seen = read(mvc.perform(get("/api/v1/vehicles/" + vehicle + "/fuel-entries").with(neoliaManager()))
                .andExpect(status().isOk()), "$[*].id");
        assertThat(seen).contains(id);
        mvc.perform(get("/api/v1/vehicles/" + vehicle + "/fuel-entries").with(metropoleManager()))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_hybrid_car_cannot_be_charged() throws Exception {
        putJson("/api/v1/me/fuel-entries/" + UUID.randomUUID(), camille(), """
                {"filledAt": "%s", "kind": "CHARGE", "quantity": 20, "amount": 8, "mileage": 1, "payment": "PERSONAL"}"""
                .formatted(NOW))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("WRONG_ENERGY"));
    }

    @Test
    void an_inspection_with_a_damage_and_its_photo_reach_the_fleet_manager_only() throws Exception {
        int mileage = read(mvc.perform(get("/api/v1/me").with(camille())), "$.vehicle.mileage");
        String id = UUID.randomUUID().toString();
        String checks = String.join(",", List.of("BODYWORK", "TYRES", "LIGHTS", "INTERIOR", "CLEANLINESS", "SAFETY_KIT",
                "DOCUMENTS").stream().map(item -> "{\"item\": \"" + item + "\", \"condition\": \"OK\"}").toList());
        putJson("/api/v1/me/inspections/" + id, camille(), """
                {"kind": "PERIODIC", "performedAt": "%s", "mileage": %d, "fuelLevel": 65,
                 "checks": [%s, {"item": "WINDSCREEN", "condition": "DAMAGED", "comment": "Impact côté passager"}]}"""
                .formatted(NOW, mileage, checks))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defects").value(1))
                .andExpect(jsonPath("$.checks[1].item").value("WINDSCREEN"));

        String photo = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/v1/me/inspections/" + id + "/photos/" + photo).with(camille())
                            .contentType(MediaType.IMAGE_JPEG).content(JPEG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sizeBytes").value(JPEG.length));
        }
        mvc.perform(get("/api/v1/me/inspections").with(camille()))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].photos.length()").value(1));

        mvc.perform(get("/api/v1/photos/" + photo).with(camille()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG));
        mvc.perform(get("/api/v1/photos/" + photo).with(neoliaManager())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/photos/" + photo).with(jean())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/photos/" + photo).with(metropoleManager())).andExpect(status().isNotFound());

        // a driver cannot add photos to someone else's inspection
        mvc.perform(put("/api/v1/me/inspections/" + id + "/photos/" + UUID.randomUUID()).with(jean())
                        .contentType(MediaType.IMAGE_JPEG).content(JPEG))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_claim_declared_offline_is_declared_once_with_its_photos() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = """
                {"type": "COLLISION", "occurredAt": "2026-10-13T17:20:00Z", "location": "Parking du site, Lyon Gerland",
                 "latitude": 45.7316, "longitude": 4.8302, "description": "Rétroviseur arraché en manœuvre",
                 "thirdPartyInvolved": false, "injuries": false}""";
        String reference = read(putJson("/api/v1/me/incidents/" + id, camille(), body).andExpect(status().isOk()),
                "$.reference");
        putJson("/api/v1/me/incidents/" + id, camille(), body).andExpect(jsonPath("$.reference").value(reference));

        mvc.perform(put("/api/v1/me/incidents/" + id + "/photos/" + UUID.randomUUID()).with(camille())
                        .contentType(MediaType.IMAGE_JPEG).content(JPEG))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/incidents/" + id + "/photos").with(neoliaManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/me/incidents/" + id + "/photos/" + UUID.randomUUID()).with(camille())
                        .contentType(MediaType.APPLICATION_PDF).content(JPEG))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void the_alerts_of_the_box_reach_the_driver_of_the_vehicle_once() throws Exception {
        String vehicle = read(mvc.perform(get("/api/v1/me").with(camille())), "$.vehicle.id");
        String alertId = UUID.randomUUID().toString();
        String event = """
                {"type": "alert.raised", "source": "telemetry-service", "eventId": "%s", "occurredAt": "%s",
                 "companyId": "%s", "payload": {"type": "ENGINE_FAULT", "alertId": "%s", "vehicleId": "%s",
                 "registration": "GZ-980-BN", "severity": "CRITICAL", "message": "P0217 : Surchauffe du moteur",
                 "raisedAt": "%s"}}""".formatted(UUID.randomUUID(), NOW, NEOLIA, alertId, vehicle, NOW.minusSeconds(600));
        VehicleAlertListener listener = new VehicleAlertListener(alertRepository, jsonMapper);
        listener.onEvent(event);
        listener.onEvent(event);
        listener.onEvent("not json");

        ResultActions notifications = mvc.perform(get("/api/v1/me/notifications").with(camille()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].level").value("CRITICAL"));
        List<String> ids = read(notifications, "$[*].id");
        assertThat(ids).containsOnlyOnce("alert:" + alertId);
        List<String> details = read(notifications, "$[?(@.kind == 'VEHICLE_ALERT')].detail");
        assertThat(details).contains("P0217 : Surchauffe du moteur");

        // Jean drives another vehicle: he does not get the alert
        List<String> others = read(mvc.perform(get("/api/v1/me/notifications").with(jean())), "$[*].id");
        assertThat(others).doesNotContain("alert:" + alertId);
    }

    @Test
    void a_mileage_sent_twice_by_the_phone_is_one_reading() throws Exception {
        String vehicle = read(mvc.perform(get("/api/v1/me").with(jean())), "$.vehicle.id");
        int mileage = read(mvc.perform(get("/api/v1/me").with(jean())), "$.vehicle.mileage");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/me/mileage").with(jean()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mileage\": %d}".formatted(mileage + 31))).andExpect(status().isOk());
        }
        List<Integer> readings = read(mvc.perform(get("/api/v1/vehicles/" + vehicle + "/mileage")), "$[*].mileage");
        assertThat(readings).containsOnlyOnce(mileage + 31);
    }
}
