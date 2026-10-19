package com.fleetpilot.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GraphQL gateway")
class GatewayTest {

    private static final StubVehicleService VEHICLES;
    private static final StubVehicleService QUOTES;
    private static final String NEOLIA = "474a1f7d-c59a-3321-a0a2-729ec42501d5";

    static {
        try {
            VEHICLES = new StubVehicleService();
            QUOTES = new StubVehicleService();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("fleetpilot.services.vehicle-url", VEHICLES::url);
        registry.add("fleetpilot.services.quote-url", QUOTES::url);
        registry.add("fleetpilot.services.notification-url", QUOTES::url);
        registry.add("fleetpilot.services.reporting-url", QUOTES::url);
        registry.add("fleetpilot.services.telemetry-url", QUOTES::url);
        registry.add("fleetpilot.services.integration-url", QUOTES::url);
        registry.add("fleetpilot.services.energy-url", QUOTES::url);
    }

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void reset() {
        VEHICLES.reset();
        QUOTES.reset();
    }

    private static RequestPostProcessor manager() {
        return jwt().jwt(token -> token.tokenValue("token-of-mathilde")
                        .claim("preferred_username", "m.roux@neolia-energies.fr")
                        .claim("name", "Mathilde Roux")
                        .claim("company_id", NEOLIA)
                        .claim("realm_access", Map.of("roles", List.of("client-manager", "offline_access"))))
                .authorities(new SimpleGrantedAuthority("ROLE_client-manager"), new SimpleGrantedAuthority("ROLE_offline_access"));
    }

    /** GraphQL over HTTP may answer asynchronously: the result is then read after the async dispatch. */
    private ResultActions graphql(String query) throws Exception {
        ResultActions request = mvc.perform(post("/graphql").with(manager()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\": " + quote(query) + "}"));
        MvcResult started = request.andReturn();
        return started.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(started)) : request;
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\"";
    }

    @Test
    void the_viewer_is_read_from_the_token() throws Exception {
        graphql("{ viewer { username name roles staff companyId driverId } }")
                .andExpect(jsonPath("$.data.viewer.username").value("m.roux@neolia-energies.fr"))
                .andExpect(jsonPath("$.data.viewer.name").value("Mathilde Roux"))
                .andExpect(jsonPath("$.data.viewer.roles.length()").value(1))
                .andExpect(jsonPath("$.data.viewer.roles[0]").value("client-manager"))
                .andExpect(jsonPath("$.data.viewer.staff").value(false))
                .andExpect(jsonPath("$.data.viewer.companyId").value(NEOLIA));
        assertThat(VEHICLES.calls()).isEmpty();
    }

    @Test
    void queries_call_the_service_with_the_user_token_and_the_filters() throws Exception {
        VEHICLES.reply("GET", "/api/v1/vehicles", """
                {"content": [{"id": "v1", "companyId": "%s", "registration": "GH-482-KM", "category": "CAR",
                  "make": "Renault", "model": "Megane E-Tech", "energy": "ELECTRIC", "critAir": 0, "mileage": 15000,
                  "status": "ACTIVE", "site": null, "costCenter": null, "currentDriver": null}],
                 "page": 2, "size": 10, "totalElements": 31, "totalPages": 4}""".formatted(NEOLIA));

        graphql("{ vehicles(filter: {energy: ELECTRIC, q: \"megane\"}, page: 2, size: 10, sort: [\"mileage,desc\"]) "
                + "{ totalElements content { registration energy critAir } } }")
                .andExpect(jsonPath("$.data.vehicles.totalElements").value(31))
                .andExpect(jsonPath("$.data.vehicles.content[0].registration").value("GH-482-KM"))
                .andExpect(jsonPath("$.data.vehicles.content[0].energy").value("ELECTRIC"));

        StubVehicleService.Call call = VEHICLES.calls().getFirst();
        assertThat(call.authorization()).isEqualTo("Bearer token-of-mathilde");
        assertThat(call.query()).contains("energy=ELECTRIC", "q=megane", "page=2", "size=10", "sort=mileage,desc");
    }

    @Test
    void indicators_are_exposed_as_ordered_counts() throws Exception {
        VEHICLES.reply("GET", "/api/v1/fleet/summary", """
                {"companyId": null, "asOf": "2026-10-14", "inFleet": 1449, "byStatus": {"ACTIVE": 1353, "IN_REPAIR": 58},
                 "byCategory": {"CAR": 697}, "byEnergy": {"DIESEL": 672, "ELECTRIC": 410}, "byCritAir": {"2": 637},
                 "averageAgeMonths": 37.1, "averageCo2GramsPerKm": 143.8, "activeDrivers": 1162, "assignedVehicles": 1154,
                 "overdueDeadlines": 330, "dueSoonDeadlines": 416, "contractsEndingSoon": 42, "contractsOverrunRisk": 121,
                 "monthlyRentTotal": 761233.43, "openIncidents": 50, "incidentsLast12Months": 196}""");

        graphql("{ fleetSummary { inFleet asOf byEnergy { key count } byStatus { key count } monthlyRentTotal } }")
                .andExpect(jsonPath("$.data.fleetSummary.inFleet").value(1449))
                .andExpect(jsonPath("$.data.fleetSummary.asOf").value("2026-10-14"))
                .andExpect(jsonPath("$.data.fleetSummary.byEnergy[0].key").value("DIESEL"))
                .andExpect(jsonPath("$.data.fleetSummary.byEnergy[1].count").value(410))
                .andExpect(jsonPath("$.data.fleetSummary.monthlyRentTotal").value(761233.43));
    }

    @Test
    void the_vehicles_of_a_page_of_deadlines_are_loaded_in_one_call() throws Exception {
        StringBuilder deadlines = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            deadlines.append(i == 0 ? "" : ",").append("""
                    {"id": "d%d", "vehicle": {"id": "v%d", "registration": "GH-%03d-KM", "make": "Renault", "model": "Clio"},
                     "type": "SERVICE", "dueDate": "2026-11-02", "dueMileage": null, "status": "OPEN", "urgency": "DUE_SOON"}"""
                    .formatted(i, i % 5, i));
        }
        VEHICLES.reply("GET", "/api/v1/deadlines", "{\"content\": [" + deadlines + "], \"page\": 0, \"size\": 20,"
                + " \"totalElements\": 20, \"totalPages\": 1}");
        StringBuilder vehicles = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            vehicles.append(i == 0 ? "" : ",").append("""
                    {"id": "v%d", "companyId": "%s", "registration": "GH-%03d-KM", "category": "CAR", "make": "Renault",
                     "model": "Clio", "energy": "PETROL", "critAir": 1, "mileage": 1000, "status": "IN_REPAIR"}"""
                    .formatted(i, NEOLIA, i));
        }
        VEHICLES.reply("GET", "/api/v1/vehicles", "{\"content\": [" + vehicles + "], \"page\": 0, \"size\": 5,"
                + " \"totalElements\": 5, \"totalPages\": 1}");

        graphql("{ deadlines(size: 20) { content { id dueDate vehicleSummary { status } } } }")
                .andExpect(jsonPath("$.data.deadlines.content.length()").value(20))
                .andExpect(jsonPath("$.data.deadlines.content[19].vehicleSummary.status").value("IN_REPAIR"));

        List<StubVehicleService.Call> vehicleCalls = VEHICLES.calls().stream()
                .filter(call -> call.path().equals("/api/v1/vehicles")).toList();
        assertThat(vehicleCalls).hasSize(1);
        assertThat(vehicleCalls.getFirst().query()).contains("ids=v0", "ids=v4");
    }

    @Test
    void service_errors_keep_their_code_and_field_errors() throws Exception {
        VEHICLES.fail("POST", "/api/v1/vehicles/v1/assignments", 422, """
                {"type": "https://fleetpilot.dev/problems/license-not-valid", "status": 422, "code": "LICENSE_NOT_VALID",
                 "detail": "Camille Martin does not hold a valid licence for a PL vehicle", "correlationId": "abc-12345678"}""");

        graphql("mutation { assignVehicle(vehicleId: \"v1\", driverId: \"d1\") { id } }")
                .andExpect(jsonPath("$.errors[0].message").value("Camille Martin does not hold a valid licence for a PL vehicle"))
                .andExpect(jsonPath("$.errors[0].extensions.code").value("LICENSE_NOT_VALID"))
                .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.errors[0].extensions.status").value(422))
                .andExpect(jsonPath("$.errors[0].extensions.correlationId").value("abc-12345678"));

        assertThat(VEHICLES.calls().getFirst().body()).contains("\"driverId\":\"d1\"");
    }

    @Test
    void an_invisible_record_is_null_not_an_error() throws Exception {
        graphql("{ vehicle(id: \"unknown\") { registration } }")
                .andExpect(jsonPath("$.data.vehicle").doesNotExist())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void dates_are_validated_before_reaching_the_service() throws Exception {
        graphql("mutation { endContract(id: \"c1\", endedOn: \"31/12/2026\") { id } }")
                .andExpect(jsonPath("$.errors[0].message").value(containsString("Invalid Date")));
        assertThat(VEHICLES.calls()).isEmpty();
    }

    @Test
    void the_api_requires_a_token() throws Exception {
        mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content("{\"query\": \"{ viewer { username } }\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void the_vehicle_file_gathers_data_from_the_fleet_and_the_quote_services() throws Exception {
        VEHICLES.reply("GET", "/api/v1/vehicles/v1", """
                {"id": "v1", "registration": "GH-482-KM", "make": "Renault", "model": "Clio"}""");
        QUOTES.reply("GET", "/api/v1/quotes", """
                {"content": [{"id": "q1", "reference": "DEV-2026-000042", "status": "TO_NEGOTIATE",
                  "garage": {"id": "g1", "name": "Garage du Prado", "city": "Marseille", "network": "INDEPENDENT"},
                  "registration": "GH-482-KM", "channel": "PORTAL", "submittedAt": "2026-10-14T08:00:00Z",
                  "amount": 284.0, "overcharge": 95.4, "findingCount": 2, "autoApproved": false, "revision": 1,
                  "invoiceDisputed": false}], "page": 0, "size": 50, "totalElements": 1, "totalPages": 1}""");

        graphql("{ vehicle(id: \"v1\") { registration quotes { reference status overcharge garage { name } } } }")
                .andExpect(jsonPath("$.data.vehicle.registration").value("GH-482-KM"))
                .andExpect(jsonPath("$.data.vehicle.quotes[0].reference").value("DEV-2026-000042"))
                .andExpect(jsonPath("$.data.vehicle.quotes[0].overcharge").value(95.4))
                .andExpect(jsonPath("$.data.vehicle.quotes[0].garage.name").value("Garage du Prado"));

        StubVehicleService.Call call = QUOTES.calls().getFirst();
        assertThat(call.query()).contains("vehicleId=v1");
        assertThat(call.authorization()).isEqualTo("Bearer token-of-mathilde");
    }

    @Test
    void quote_mutations_send_the_decision_to_the_quote_service() throws Exception {
        QUOTES.reply("POST", "/api/v1/quotes/q1/counter-offer", """
                {"id": "q1", "status": "TO_NEGOTIATE", "counterOffer": 190.0, "actions": ["ACCEPT", "REJECT"]}""");

        graphql("mutation { counterOffer(id: \"q1\", amount: 190, message: \"Tarif\") { status counterOffer actions } }")
                .andExpect(jsonPath("$.data.counterOffer.counterOffer").value(190.0))
                .andExpect(jsonPath("$.data.counterOffer.actions[0]").value("ACCEPT"));

        assertThat(QUOTES.calls().getFirst().body()).contains("\"amount\":190.0", "\"message\":\"Tarif\"");
        assertThat(VEHICLES.calls()).isEmpty();
    }

    @Test
    void a_user_without_the_garage_role_has_no_garage() throws Exception {
        graphql("{ myGarage { name } viewer { garageId } }")
                .andExpect(jsonPath("$.data.myGarage").doesNotExist())
                .andExpect(jsonPath("$.data.viewer.garageId").doesNotExist());
        assertThat(QUOTES.calls()).isEmpty();
    }

    @Test
    void a_report_request_goes_to_the_reporting_service() throws Exception {
        QUOTES.reply("POST", "/api/v1/reports", """
                {"id": "r1", "type": "TCO", "format": "XLSX", "title": "TCO", "status": "PENDING",
                 "requestedAt": "2026-10-14T08:00:00Z"}""");

        graphql("mutation { requestReport(type: TCO) { id status title } }")
                .andExpect(jsonPath("$.data.requestReport.status").value("PENDING"));

        assertThat(QUOTES.calls().getFirst().body()).contains("\"type\":\"TCO\"");
    }

    @Test
    void the_open_alerts_by_type_become_a_list_and_an_alert_is_resolved_with_its_comment() throws Exception {
        QUOTES.reply("GET", "/api/v1/telemetry/summary", """
                {"connected": 160, "moving": 42, "idling": 6, "parked": 108, "offline": 4, "kilometresToday": 5120.4,
                 "openAlerts": 9, "openAlertsByType": {"SPEEDING": 2, "SERVICE_DUE": 6, "ENGINE_FAULT": 1}}""");
        graphql("{ telemetrySummary { moving openAlertsByType { type count } } }")
                .andExpect(jsonPath("$.data.telemetrySummary.moving").value(42))
                .andExpect(jsonPath("$.data.telemetrySummary.openAlertsByType[0].type").value("SERVICE_DUE"))
                .andExpect(jsonPath("$.data.telemetrySummary.openAlertsByType[2].count").value(1));

        QUOTES.reply("POST", "/api/v1/alerts/a1/resolve", """
                {"id": "a1", "vehicleId": "v1", "companyId": "c1", "type": "SPEEDING", "severity": "WARNING",
                 "message": "152 km/h", "status": "RESOLVED", "raisedAt": "2026-10-14T08:00:00Z",
                 "lastSeenAt": "2026-10-14T08:00:00Z", "occurrences": 1, "comment": "Vu avec le conducteur"}""");
        graphql("mutation { resolveAlert(id: \"a1\", comment: \"Vu avec le conducteur\") { status comment } }")
                .andExpect(jsonPath("$.data.resolveAlert.status").value("RESOLVED"));
        assertThat(QUOTES.calls().getLast().body()).contains("\"comment\":\"Vu avec le conducteur\"");
    }

    @Test
    void fuel_anomalies_become_lists_and_a_failed_file_is_replayed_through_the_bus() throws Exception {
        QUOTES.reply("GET", "/api/v1/fuel/transactions", """
                {"content": [{"id": "t1", "provider": "Carbu Pro", "reference": "CBP-1", "card": "7089", "occurredAt":
                 "2026-10-06T06:12:00Z", "station": "Relais Prado", "product": "GAZOLE", "litres": 41.2,
                 "unitPrice": 1.719, "amount": 70.82, "registration": "GH-482-KM",
                 "anomalies": "WRONG_FUEL,TOO_FREQUENT"}], "page": 0, "size": 25, "totalElements": 1,
                 "totalPages": 1}""");
        graphql("{ fuelTransactions(filter: {onlyAnomalies: true}) { content { reference anomalies } } }")
                .andExpect(jsonPath("$.data.fuelTransactions.content[0].anomalies[1]").value("TOO_FREQUENT"));
        assertThat(QUOTES.calls().getLast().query()).contains("onlyAnomalies=true");

        QUOTES.reply("POST", "/api/v1/integration/exchanges/e1/replay", """
                {"fileName": "replay-e1__PRD-1077.xml", "message": "Fichier remis dans la file garages"}""");
        graphql("mutation { replayExchange(id: \"e1\") { fileName } }")
                .andExpect(jsonPath("$.data.replayExchange.fileName").value("replay-e1__PRD-1077.xml"));
    }

    @Test
    void the_live_vehicles_are_filtered_by_company_and_the_energy_summary_comes_from_the_energy_service()
            throws Exception {
        QUOTES.reply("GET", "/api/v1/telemetry/live", "[]");
        graphql("{ liveVehicles(companyId: \"c1\") { vehicleId } }").andExpect(jsonPath("$.data.liveVehicles").isEmpty());
        assertThat(QUOTES.calls().getLast().query()).contains("companyId=c1");

        QUOTES.reply("GET", "/api/v1/energy/summary", """
                {"vehicles": 1442, "zeroEmission": 412, "eligible": 530, "eligibleWithCharging": 61, "notYet": 210,
                 "toConfirm": 189, "notApplicable": 40, "zeroEmissionShare": 28.6, "potentialShare": 69.6,
                 "annualSavingPotential": 412000, "co2CurrentTonnes": 3120.5, "co2AvoidableTonnes": 1480.2,
                 "zfeExposed": 37, "zfeBannedNow": 21}""");
        graphql("{ energySummary { eligible potentialShare zfeBannedNow } }")
                .andExpect(jsonPath("$.data.energySummary.eligible").value(530))
                .andExpect(jsonPath("$.data.energySummary.potentialShare").value(69.6));
    }

    @Test
    void a_fill_up_from_the_phone_is_put_with_its_id_and_the_nearby_garages_come_from_the_quote_service()
            throws Exception {
        VEHICLES.reply("PUT", "/api/v1/me/fuel-entries/f1", """
                {"id": "f1", "vehicleId": "v1", "filledAt": "2026-10-14T08:00:00Z", "kind": "FUEL", "quantity": 40.12,
                 "amount": 72.5, "unitPrice": 1.807, "mileage": 15991, "payment": "FUEL_CARD",
                 "createdAt": "2026-10-14T08:01:00Z"}""");
        graphql("""
                mutation { recordMyFuel(id: "f1", input: {filledAt: "2026-10-14T08:00:00Z", kind: FUEL,
                  quantity: 40.12, amount: 72.5, mileage: 15991, payment: FUEL_CARD}) { id unitPrice } }""")
                .andExpect(jsonPath("$.data.recordMyFuel.unitPrice").value(1.807));
        assertThat(VEHICLES.calls().getFirst().body()).contains("\"quantity\":40.12", "\"payment\":\"FUEL_CARD\"");

        QUOTES.reply("GET", "/api/v1/garages/nearby", """
                [{"id": "g1", "name": "Garage du Prado", "network": "INDEPENDENT", "city": "Marseille",
                  "distanceKm": 2.4}]""");
        graphql("{ nearbyGarages(latitude: 43.29, longitude: 5.37) { name distanceKm } }")
                .andExpect(jsonPath("$.data.nearbyGarages[0].distanceKm").value(2.4));
        assertThat(QUOTES.calls().getLast().query()).contains("latitude=43.29", "radiusKm=25", "limit=20");
    }
}
