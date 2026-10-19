package com.fleetpilot.quote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fleetpilot.common.security.Roles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("Quote API, end to end")
class QuoteApiIT extends IntegrationTestBase {

    /** A car of Métropole Azur Provence based in Marseille. */
    static final String CAR = "AB-101-CD";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** Brake discs at 140 EUR (ref 95) and 2 h of labour (standard 1.3 h): 95.40 EUR of overcharge. */
    static final String OVERPRICED_LINES = """
            [{"kind":"PART","code":"DISQ-AV","label":"Disques avant","quantity":1,"unitPrice":140.00},
             {"kind":"LABOUR","code":"FREIN-AV-DISQ","label":"Main-d'oeuvre freins","quantity":2.0,"unitPrice":72.00}]""";

    static final String COMPLIANT_LINES = """
            [{"kind":"PART","code":"PLAQ-AV","label":"Plaquettes avant","quantity":1,"unitPrice":45.00},
             {"kind":"LABOUR","code":"FREIN-AV","label":"Plaquettes avant","quantity":0.8,"unitPrice":72.00}]""";

    @Autowired
    private JdbcClient jdbc;

    private ResultActions submit(RequestPostProcessor who, String registration, String lines) throws Exception {
        String body = """
                {"garageId":"%s","registration":"%s","garageReference":"IT-%d","mileage":82000,
                 "description":"Freins avant","lines":%s}""".formatted(PRADO, registration,
                SEQUENCE.incrementAndGet(), lines);
        return mvc.perform(post("/api/v1/quotes").with(who).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String submitted(String registration, String lines) throws Exception {
        String json = submit(garage(PRADO), registration, lines).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private ResultActions action(RequestPostProcessor who, String id, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/quotes/" + id + path).with(who).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Nested
    @DisplayName("submission and analysis")
    class Submission {

        @Test
        void an_overpriced_quote_is_flagged_line_by_line_and_published() throws Exception {
            String json = submit(garage(PRADO), "ab 101 cd", OVERPRICED_LINES)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reference").value(startsWith("DEV-2026-")))
                    .andExpect(jsonPath("$.status").value("TO_NEGOTIATE"))
                    .andExpect(jsonPath("$.registration").value(CAR))
                    .andExpect(jsonPath("$.companyId").value(METROPOLE.toString()))
                    .andExpect(jsonPath("$.channel").value("PORTAL"))
                    .andExpect(jsonPath("$.amount").value(284.00))
                    .andExpect(jsonPath("$.referenceAmount").value(188.60))
                    .andExpect(jsonPath("$.overcharge").value(95.40))
                    .andExpect(jsonPath("$.lines[0].finding").value("OVERPRICED_PART"))
                    .andExpect(jsonPath("$.lines[0].referenceUnitPrice").value(95.00))
                    .andExpect(jsonPath("$.lines[1].finding").value("EXCESSIVE_TIME"))
                    .andExpect(jsonPath("$.lines[1].referenceQuantity").value(1.30))
                    .andExpect(jsonPath("$.actions", hasItem("REVISE")))
                    .andReturn().getResponse().getContentAsString();
            String id = JsonPath.read(json, "$.id");
            List<String> events = jdbc.sql("select event_type from outbox_event where aggregate_id = cast(:id as uuid) "
                    + "order by position").param("id", id).query(String.class).list();
            assertThat(events).containsExactly("quote.received", "quote.analysed");
        }

        @Test
        void a_small_compliant_quote_is_approved_at_once() throws Exception {
            String json = submit(garage(PRADO), CAR, COMPLIANT_LINES)
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.autoApproved").value(true))
                    .andExpect(jsonPath("$.acceptedAmount").value(102.60))
                    .andExpect(jsonPath("$.actions", hasItem("INVOICE")))
                    .andReturn().getResponse().getContentAsString();
            List<String> events = jdbc.sql("select event_type from outbox_event where aggregate_id = cast(:id as uuid)")
                    .param("id", JsonPath.<String>read(json, "$.id")).query(String.class).list();
            assertThat(events).contains("quote.accepted");
        }

        @Test
        void a_vehicle_just_created_in_the_fleet_is_found_in_the_fleet_module() throws Exception {
            submit(garage(PRADO), "gh482km", COMPLIANT_LINES)
                    .andExpect(jsonPath("$.vehicle.make").value("Peugeot"))
                    .andExpect(jsonPath("$.companyId").value(NEOLIA.toString()));
            assertThat(jdbc.sql("select count(*) from vehicle_ref where registration = :r").param("r", LATE_CAR)
                    .query(Long.class).single()).isOne();
        }

        @Test
        void a_quote_for_an_unknown_vehicle_waits_until_staff_identifies_it() throws Exception {
            String id = submitted("ZZ-999-ZZ", OVERPRICED_LINES);
            mvc.perform(get("/api/v1/quotes/" + id))
                    .andExpect(jsonPath("$.status").value("RECEIVED"))
                    .andExpect(jsonPath("$.actions", hasItem("IDENTIFY_VEHICLE")));
            action(admin(), id, "/vehicle", "{\"registration\":\"AB-999-ZZ\"}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VEHICLE_UNKNOWN"));
            action(admin(), id, "/vehicle", "{\"registration\":\"AB-101-CD\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("TO_NEGOTIATE"))
                    .andExpect(jsonPath("$.history[1].action").value("VEHICLE_IDENTIFIED"));
        }

        @Test
        void the_same_garage_reference_cannot_be_received_twice() throws Exception {
            String body = """
                    {"registration":"%s","garageReference":"DUP-1","description":"Révision","lines":%s}"""
                    .formatted(CAR, COMPLIANT_LINES);
            mvc.perform(post("/api/v1/quotes").with(garage(PRADO)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/v1/quotes").with(garage(PRADO)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_RECEIVED"));
        }

        @Test
        void the_integration_bus_names_the_garage_by_its_siret_and_the_quote_is_marked_as_received_by_file()
                throws Exception {
            RequestPostProcessor bus = jwt().jwt(token -> token.claim("azp", "fleetpilot-services")
                            .claim("preferred_username", "service-account-fleetpilot-services")
                            .claim("realm_access", Map.of("roles", List.of(Roles.PLATFORM_ADMIN))))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + Roles.PLATFORM_ADMIN));
            String siret = jdbc.sql("select siret from garage where id = :id").param("id", PRADO)
                    .query(String.class).single();
            String body = """
                    {"garageSiret":"%s","registration":"%s","garageReference":"XML-77","description":"Révision",
                     "lines":%s}""".formatted(siret, CAR, COMPLIANT_LINES);
            mvc.perform(post("/api/v1/quotes").with(bus).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.garage.id").value(PRADO.toString()))
                    .andExpect(jsonPath("$.channel").value("ESB"));

            mvc.perform(post("/api/v1/quotes").with(bus).contentType(MediaType.APPLICATION_JSON)
                            .content(body.replace(siret, "00000000000000").replace("XML-77", "XML-78")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_GARAGE"));
        }

        @Test
        void invalid_input_is_reported_field_by_field() throws Exception {
            mvc.perform(post("/api/v1/quotes").with(garage(PRADO)).contentType(MediaType.APPLICATION_JSON).content("""
                    {"registration":"12345","description":"","lines":[{"kind":"PART","label":"x","quantity":0,
                     "unitPrice":10}]}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field", hasItem("registration")))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("description")))
                    .andExpect(jsonPath("$.errors[*].field", hasItem("lines[0].quantity")));
        }
    }

    @Nested
    @DisplayName("negotiation and decision")
    class Negotiation {

        @Test
        void the_garage_accepts_the_counter_offer_and_the_saving_is_recorded() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            action(garage(PRADO), id, "/counter-offer", "{\"amount\":190}").andExpect(status().isForbidden());
            action(manager(), id, "/counter-offer", "{\"amount\":190,\"message\":\"Tarif de référence\"}")
                    .andExpect(jsonPath("$.counterOffer").value(190.00));
            action(garage(PRADO), id, "/counter-offer/answer", "{\"accept\":true}")
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.acceptedAmount").value(190.00))
                    .andExpect(jsonPath("$.savings").value(94.00))
                    .andExpect(jsonPath("$.history[*].action", hasItem("COUNTER_ACCEPTED")));
        }

        @Test
        void the_garage_sends_a_new_version_at_the_reference_tariff_which_is_analysed_again() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            action(manager(), id, "/counter-offer", "{\"amount\":190}").andExpect(jsonPath("$.counterOffer").value(190.00));
            action(garage(PRADO), id, "/revisions", "{\"lines\":%s,\"message\":\"Prix du tarif\"}".formatted(COMPLIANT_LINES))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revision").value(2))
                    .andExpect(jsonPath("$.counterOffer").doesNotExist())
                    .andExpect(jsonPath("$.lines.length()").value(greaterThan(0)))
                    .andExpect(jsonPath("$.history[*].action", hasItem("REVISED")));
            action(garage(PRADO), id, "/revisions", "{\"lines\":[]}").andExpect(status().is4xxClientError());
        }

        @Test
        void accepting_with_overcharges_needs_a_justification() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            action(manager(), id, "/accept", "{}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("JUSTIFICATION_REQUIRED"));
            action(manager(), id, "/accept", "{\"comment\":\"Véhicule immobilisé\"}")
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        void above_the_approval_limit_only_an_administrator_accepts() throws Exception {
            String id = submitted(CAR, """
                    [{"kind":"PART","code":"DISQ-AV","label":"Disques avant","quantity":60,"unitPrice":95.00}]""");
            mvc.perform(get("/api/v1/quotes/" + id).with(manager()))
                    .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                    .andExpect(jsonPath("$.actions", not(hasItem("ACCEPT"))));
            action(manager(), id, "/accept", "{\"comment\":\"ok\"}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("APPROVAL_LIMIT_EXCEEDED"));
            action(admin(), id, "/accept", "{\"comment\":\"Double validation\"}")
                    .andExpect(jsonPath("$.status").value("ACCEPTED"))
                    .andExpect(jsonPath("$.decidedBy").value("theo.lambert"));
        }

        @Test
        void a_rejection_needs_a_reason() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            action(manager(), id, "/reject", "{\"reason\":\"\"}").andExpect(status().isBadRequest());
            action(manager(), id, "/reject", "{\"reason\":\"Couvert par le contrat du loueur\"}")
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.actions", hasSize(0)));
        }

        @Test
        void an_invoice_above_the_accepted_amount_is_disputed() throws Exception {
            String id = submitted(CAR, COMPLIANT_LINES);
            action(garage(PRADO), id, "/invoice", "{\"number\":\"F26-0099\",\"amount\":130.00}")
                    .andExpect(jsonPath("$.status").value("INVOICED"))
                    .andExpect(jsonPath("$.invoiceVariance").value(27.40))
                    .andExpect(jsonPath("$.invoiceDisputed").value(true))
                    .andExpect(jsonPath("$.completedOn").value("2026-10-14"));
        }
    }

    @Nested
    @DisplayName("who sees what")
    class Access {

        @Test
        void a_garage_only_sees_its_own_quotes() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            mvc.perform(get("/api/v1/quotes/" + id).with(garage(VIEUX_PORT))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/quotes?size=100").with(garage(VIEUX_PORT)))
                    .andExpect(jsonPath("$.content[*].garage.id",
                            everyItem(is(VIEUX_PORT.toString()))));
        }

        @Test
        void a_client_manager_sees_the_quotes_of_their_company_without_deciding() throws Exception {
            String id = submitted(CAR, OVERPRICED_LINES);
            mvc.perform(get("/api/v1/quotes/" + id).with(clientManager(NEOLIA))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/quotes/" + id).with(clientManager(METROPOLE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.actions", hasSize(0)));
            action(clientManager(METROPOLE), id, "/accept", "{\"comment\":\"ok\"}").andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/quotes?size=100").with(clientManager(NEOLIA)))
                    .andExpect(jsonPath("$.content[*].companyId",
                            everyItem(is(NEOLIA.toString()))));
        }

        @Test
        void a_driver_has_no_access_to_quotes() throws Exception {
            mvc.perform(get("/api/v1/quotes").with(user("camille.martin", "driver", Map.of())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("garages, tariff and savings")
    class Network {

        @Test
        void the_demo_network_and_quotes_are_loaded() throws Exception {
            // 169 demo garages, plus those created by other tests
            mvc.perform(get("/api/v1/garages?size=1")).andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(169)));
            mvc.perform(get("/api/v1/quotes/savings"))
                    .andExpect(jsonPath("$.quotes").value(greaterThan(700)))
                    .andExpect(jsonPath("$.savings").value(greaterThan(1000.0)))
                    .andExpect(jsonPath("$.months", hasSize(12)))
                    .andExpect(jsonPath("$.findings[*].finding", hasItem("OVERPRICED_PART")))
                    .andExpect(jsonPath("$.garagesToWatch", not(hasSize(0))));
        }

        @Test
        void a_driver_finds_the_approved_garages_near_them_nearest_first() throws Exception {
            // Marseille, Vieux-Port
            String body = mvc.perform(get("/api/v1/garages/nearby?latitude=43.2951&longitude=5.3741&radiusKm=8&limit=10")
                            .with(user("camille.martin", Roles.DRIVER, Map.of())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThan(2))))
                    .andExpect(jsonPath("$[*].city", everyItem(is("Marseille"))))
                    .andReturn().getResponse().getContentAsString();
            List<Double> distances = JsonPath.<List<Number>>read(body, "$[*].distanceKm").stream()
                    .map(Number::doubleValue)
                    .toList();
            assertThat(distances).isSorted().allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(8.0));
            List<String> ids = JsonPath.read(body, "$[*].id");
            for (String id : ids) {
                Boolean approved = jdbc.sql("select approved from garage where id = cast(:id as uuid)")
                        .param("id", id).query(Boolean.class).single();
                assertThat(approved).isTrue();
            }
            // Nothing in the middle of the sea; a latitude out of range is refused
            mvc.perform(get("/api/v1/garages/nearby?latitude=42.0&longitude=5.0&radiusKm=10")
                            .with(user("camille.martin", Roles.DRIVER, Map.of())))
                    .andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(get("/api/v1/garages/nearby?latitude=95&longitude=5.0")
                            .with(user("camille.martin", Roles.DRIVER, Map.of())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void a_garage_reads_its_own_file_with_its_track_record() throws Exception {
            mvc.perform(get("/api/v1/garages/me").with(garage(PRADO)))
                    .andExpect(jsonPath("$.name").value("Garage du Prado"))
                    .andExpect(jsonPath("$.region").value("PAC"))
                    .andExpect(jsonPath("$.stats.quotes").value(greaterThan(5)));
            mvc.perform(get("/api/v1/garages/" + VIEUX_PORT).with(garage(PRADO))).andExpect(status().isNotFound());
        }

        @Test
        void only_an_administrator_changes_a_negotiated_rate_and_the_next_analysis_uses_it() throws Exception {
            mvc.perform(get("/api/v1/tariff").with(garage(PRADO)))
                    .andExpect(jsonPath("$.parts", hasSize(greaterThan(30))))
                    .andExpect(jsonPath("$.rates", hasSize(32)));
            String body = "{\"hourlyRate\":60.00,\"truckHourlyRate\":80.00}";
            mvc.perform(put("/api/v1/tariff/rates/OCC/MECH").with(manager()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/tariff/rates/OCC/MECH").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.hourlyRate").value(60.00));
            assertThat(jdbc.sql("select hourly_rate from labour_rate where region = 'OCC' and labour_category = 'MECH'")
                    .query(BigDecimal.class).single()).isEqualByComparingTo("60.00");
        }

        @Test
        void a_garage_cannot_be_registered_twice() throws Exception {
            String body = """
                    {"name":"Garage Test","network":"INDEPENDENT","siret":"%s","address":"1 rue de la Gare",
                     "postalCode":"13001","city":"Marseille","email":"test@garage.fr","hourlyRate":70}"""
                    .formatted("9876543210" + String.format("%04d", SEQUENCE.incrementAndGet()));
            mvc.perform(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.region").value("PAC"))
                    .andExpect(jsonPath("$.approved").value(false));
            mvc.perform(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void quotes_can_be_found_by_registration_and_status() throws Exception {
        submitted(CAR, OVERPRICED_LINES);
        mvc.perform(get("/api/v1/quotes").param("q", "ab101").param("status", "TO_NEGOTIATE"))
                .andExpect(jsonPath("$.content[0].registration").value(CAR))
                .andExpect(jsonPath("$.content[*].status",
                        everyItem(is("TO_NEGOTIATE"))));
        mvc.perform(get("/api/v1/quotes").param("sort", "unknownField")).andExpect(status().isBadRequest());
    }
}
