package com.fleetpilot.integration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.integration.fleet.BusinessServices.CreatedQuote;
import com.fleetpilot.integration.fleet.BusinessServices.IncidentModule;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteModule;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteSubmission;
import com.fleetpilot.integration.fleet.BusinessServices.Refused;
import com.fleetpilot.integration.fleet.FleetDirectory;
import com.jayway.jsonpath.JsonPath;
import org.apache.camel.ProducerTemplate;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The bus with its real Camel routes, a real PostgreSQL and a real inbox folder: files are dropped as a partner would,
 * the business services are replaced by recording stubs, and the insurer is the simulated one, called over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
    "fleetpilot.demo-data.enabled=false",
    "fleetpilot.integration.redelivery-delay=20",
    "fleetpilot.integration.fuel-events-enabled=false",
    "fleetpilot.integration.insurer.declarations-enabled=false",
    "fleetpilot.integration.insurer.follow-up-delay=500",
    "fleetpilot.integration.insurer.follow-up-interval=500",
    "fleetpilot.integration.insurer.expertise-after=0",
    "fleetpilot.integration.insurer.agreement-after=0",
    "fleetpilot.integration.insurer.settlement-after=0"
})
@AutoConfigureMockMvc
@Import(IntegrationFlowsIT.Stubs.class)
@DisplayName("Partner flows of the integration bus")
class IntegrationFlowsIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");
    static final Path INBOX;
    static final int PORT;

    static {
        POSTGRES.start();
        try {
            INBOX = Files.createTempDirectory("fleetpilot-inbox");
            try (ServerSocket socket = new ServerSocket(0)) {
                PORT = socket.getLocalPort();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final UUID NEOLIA = UUID.nameUUIDFromBytes("company-neolia".getBytes());
    static final UUID METROPOLE = UUID.nameUUIDFromBytes("company-metropole".getBytes());

    @DynamicPropertySource
    static void bus(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("fleetpilot.integration.inbox", INBOX::toString);
        registry.add("fleetpilot.integration.insurer.url", () -> "http://localhost:" + PORT + "/partners/insurer");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ProducerTemplate camel;
    @Autowired
    private Stubs stubs;

    /** The business services, recorded; the quote module can be switched off to test the retries. */
    @TestConfiguration
    static class Stubs {

        final List<QuoteSubmission> quotes = new CopyOnWriteArrayList<>();
        final AtomicReference<String> quoteModule = new AtomicReference<>("UP");
        final Map<UUID, List<String>> incidentMoves = new ConcurrentHashMap<>();
        final Map<UUID, String> assessments = new ConcurrentHashMap<>();

        @Bean
        @Primary
        FleetDirectory stubFleet() {
            return new FleetDirectory() {
                @Override
                public List<FleetVehicle> vehicles() {
                    return List.of(
                            new FleetVehicle(UUID.nameUUIDFromBytes("gh".getBytes()), NEOLIA, "GH-482-KM", "CAR",
                                    "Peugeot", "308 SW", "PETROL", "ACTIVE", 82_140),
                            new FleetVehicle(UUID.nameUUIDFromBytes("ft".getBytes()), NEOLIA, "FT-118-BC", "VAN",
                                    "Renault", "Trafic", "DIESEL", "ACTIVE", 61_300),
                            new FleetVehicle(UUID.nameUUIDFromBytes("ev".getBytes()), METROPOLE, "GE-907-RT", "CAR",
                                    "Renault", "Mégane E-Tech", "ELECTRIC", "ACTIVE", 18_400));
                }

                @Override
                public List<LeaseContract> contracts(String lessor) {
                    return List.of(new LeaseContract(UUID.randomUUID(), "Arval", "ARV-2023-104280",
                            UUID.nameUUIDFromBytes("gh".getBytes()), "GH-482-KM", LocalDate.of(2023, 7, 1),
                            LocalDate.of(2027, 7, 1), new BigDecimal("474.89"), "ACTIVE", null));
                }
            };
        }

        @Bean
        @Primary
        QuoteModule stubQuotes() {
            return quote -> {
                if ("DOWN".equals(quoteModule.get())) {
                    throw new ResourceAccessException("Connection refused: quote-service");
                }
                if ("00000000000000".equals(quote.garageSiret())) {
                    throw new Refused("UNKNOWN_GARAGE", "No garage of the network has the SIRET 00000000000000");
                }
                quotes.add(quote);
                return new CreatedQuote(UUID.randomUUID(), "DEV-2026-%06d".formatted(quotes.size()), "ANALYSED",
                        new BigDecimal("231.40"), new CreatedQuote.Garage(UUID.randomUUID(), "Garage du Prado"),
                        new CreatedQuote.Vehicle(UUID.randomUUID(), quote.registration()));
            };
        }

        @Bean
        @Primary
        IncidentModule stubIncidents() {
            return new IncidentModule() {
                @Override
                public String status(UUID incidentId) {
                    List<String> moves = incidentMoves.getOrDefault(incidentId, List.of());
                    return moves.isEmpty() ? "DECLARED" : moves.getLast();
                }

                @Override
                public void assess(UUID incidentId, String responsibility, BigDecimal estimatedCost) {
                    assessments.put(incidentId, responsibility + " " + estimatedCost);
                }

                @Override
                public void moveTo(UUID incidentId, String status) {
                    incidentMoves.computeIfAbsent(incidentId, id -> new CopyOnWriteArrayList<>()).add(status);
                }
            };
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

    private static String quoteXml(String reference, String registration, String siret, String total) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <devis xmlns="urn:fleetpilot:devis-garage:1" version="1.0">
                  <emetteur siret="%s">Garage du Prado</emetteur>
                  <reference>%s</reference>
                  <dateEmission>2026-10-14</dateEmission>
                  <vehicule immatriculation="%s" kilometrage="82140"/>
                  <objet>Remplacement des disques et plaquettes avant</objet>
                  <lignes>
                    <ligne nature="PIECE" code="DISQ-AV">
                      <libelle>Disques de frein avant (paire)</libelle>
                      <quantite>1</quantite>
                      <prixUnitaireHT>95.00</prixUnitaireHT>
                    </ligne>
                    <ligne nature="MAIN_OEUVRE" code="FREIN-AV-DISQ">
                      <libelle>Remplacement des disques et plaquettes avant</libelle>
                      <quantite>1.30</quantite>
                      <prixUnitaireHT>69.00</prixUnitaireHT>
                    </ligne>
                    <ligne nature="INGREDIENTS">
                      <libelle>Nettoyant freins</libelle>
                      <quantite>1</quantite>
                      <prixUnitaireHT>8.50</prixUnitaireHT>
                    </ligne>
                  </lignes>
                  <totalHT>%s</totalHT>
                </devis>
                """.formatted(siret, reference, registration, total);
    }

    private static void drop(String folder, String name, String content) throws IOException {
        Path dir = Files.createDirectories(INBOX.resolve(folder));
        Path temporary = dir.resolve("." + name + ".part");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        Files.move(temporary, dir.resolve(name));
    }

    /** The last exchange journaled for a source, once it is no longer running. */
    private Map<String, Object> exchange(String source) throws InterruptedException {
        waitUntil(() -> jdbc.sql("select count(*) from exchange where source = :s and status <> 'RUNNING'")
                .param("s", source).query(Long.class).single() > 0);
        return jdbc.sql("select * from exchange where source = :s order by received_at desc limit 1")
                .param("s", source).query().singleRow();
    }

    private List<QuoteSubmission> sentQuotes(String garageReference) {
        return stubs.quotes.stream().filter(q -> garageReference.equals(q.garageReference())).toList();
    }

    private List<String> issueCodes(Object exchangeId) {
        return jdbc.sql("select code from exchange_issue where exchange_id = :id order by line nulls first, id")
                .param("id", exchangeId).query(String.class).list();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition reached in time").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    @Test
    void a_garage_quote_is_validated_translated_and_sent_once_even_if_the_garage_sends_it_twice() throws Exception {
        String xml = quoteXml("PRD-2026-1042", "GH-482-KM", "45601270032976", "193.20");
        drop("garages", "PRD-1042.xml", xml);
        Map<String, Object> first = exchange("PRD-1042.xml");
        assertThat(first).containsEntry("status", "PROCESSED").containsEntry("partner", "Garage du Prado");
        assertThat((String) first.get("result_ref")).matches("DEV-2026-\\d{6}");
        assertThat((String) first.get("summary")).matches("Devis DEV-2026-\\d{6} créé : GH-482-KM, 231,40.*");
        QuoteSubmission sent = sentQuotes("PRD-2026-1042").getFirst();
        assertThat(sent.garageSiret()).isEqualTo("45601270032976");
        assertThat(sent.mileage()).isEqualTo(82_140);
        assertThat(sent.lines()).extracting(l -> l.kind() + " " + l.code())
                .containsExactly("PART DISQ-AV", "LABOUR FREIN-AV-DISQ", "SUPPLIES null");
        waitUntil(() -> Files.exists(INBOX.resolve("garages/.done/" + LocalDate.now().toString().replace("-", "")
                + "/PRD-1042.xml")));

        drop("garages", "PRD-1042_renvoi.xml", xml);
        assertThat(exchange("PRD-1042_renvoi.xml")).containsEntry("status", "DUPLICATE")
                .containsEntry("replay_of", first.get("id"));
        assertThat(sentQuotes("PRD-2026-1042")).hasSize(1);
    }

    @Test
    void a_file_breaking_its_schema_or_with_a_wrong_total_is_refused_with_the_reason() throws Exception {
        drop("garages", "GCH-2231.xml", quoteXml("GCH-2026-2231", "gh482km", "45601270032976", "193.20"));
        Map<String, Object> invalid = exchange("GCH-2231.xml");
        assertThat(invalid).containsEntry("status", "REJECTED");
        assertThat(issueCodes(invalid.get("id"))).containsExactly("INVALID_FILE");
        assertThat(jdbc.sql("select message from exchange_issue where exchange_id = :id").param("id", invalid.get("id"))
                .query(String.class).single()).contains("gh482km");

        drop("garages", "GLF-0415.xml", quoteXml("GLF-2026-0415", "FT-118-BC", "45601270032976", "1180.00"));
        Map<String, Object> wrongTotal = exchange("GLF-0415.xml");
        assertThat(wrongTotal).containsEntry("status", "REJECTED");
        assertThat(issueCodes(wrongTotal.get("id"))).containsExactly("TOTAL_MISMATCH");

        drop("garages", "XXX-0001.xml", quoteXml("XXX-2026-0001", "FT-118-BC", "00000000000000", "193.20"));
        Map<String, Object> refused = exchange("XXX-0001.xml");
        assertThat(refused).containsEntry("status", "REJECTED");
        assertThat(issueCodes(refused.get("id"))).containsExactly("UNKNOWN_GARAGE");
    }

    @Test
    void a_quote_module_down_fails_the_exchange_after_the_retries_and_the_file_can_be_replayed() throws Exception {
        stubs.quoteModule.set("DOWN");
        drop("garages", "PRD-1077.xml", quoteXml("PRD-2026-1077", "FT-118-BC", "45601270032976", "193.20"));
        Map<String, Object> failed = exchange("PRD-1077.xml");
        assertThat(failed).containsEntry("status", "FAILED");
        assertThat((String) failed.get("summary")).contains("Connection refused");

        stubs.quoteModule.set("UP");
        mvc.perform(post("/api/v1/integration/exchanges/{id}/replay", failed.get("id")).with(staff()))
                .andExpect(status().isAccepted());
        waitUntil(() -> jdbc.sql("select count(*) from exchange where replay_of = :id and status = 'PROCESSED'")
                .param("id", failed.get("id")).query(Long.class).single() == 1);
    }

    @Test
    void a_lessor_invoice_is_checked_against_the_contracts() throws Exception {
        drop("lessors", "arval_factures_2026-09.csv", """
                loueur;numero_facture;date_facture;numero_contrat;immatriculation;periode;loyer_ht;prestations_ht;total_ht
                Arval;ARV-F-202609-0412;2026-10-03;ARV-2023-104280;GH-482-KM;2026-09;498,63;38,00;536,63
                Arval;ARV-F-202609-0412;2026-10-03;ARV-2024-199871;GA-731-PL;2026-09;512,40;38,00;550,40
                """);
        Map<String, Object> invoice = exchange("arval_factures_2026-09.csv");
        assertThat(invoice).containsEntry("status", "REJECTED").containsEntry("records", 2).containsEntry("accepted", 0);
        assertThat(issueCodes(invoice.get("id"))).containsExactly("RENT_MISMATCH", "UNKNOWN_CONTRACT");
    }

    @Test
    void fuel_card_transactions_are_matched_with_the_fleet_and_their_anomalies_are_searchable() throws Exception {
        drop("fuelcards", "cbp_transactions.csv", """
                fournisseur;reference;date_heure;carte;immatriculation;station;ville;produit;volume_l;prix_litre_ttc;montant_ttc;kilometrage
                Carbu Pro;CBP-1;2026-10-06 08:12;7089000000000001;GH-482-KM;Relais Prado;Marseille;SP95-E10;41,20;1,809;74,53;82100
                Carbu Pro;CBP-2;2026-10-06 09:02;7089000000000001;GH-482-KM;Relais Prado;Marseille;SP95-E10;12,00;1,809;21,71;82110
                Carbu Pro;CBP-3;2026-10-07 18:40;7089000000000002;FT-118-BC;Relais Les Milles;Aix;SP98;95,00;1,919;182,31;61290
                Carbu Pro;CBP-4;2026-10-08 12:03;7089000000000003;GE-907-RT;Station Vitrolles A7;Vitrolles;GAZOLE;40,00;1,719;68,76;18400
                Carbu Pro;CBP-5;2026-10-08 13:31;7089000000000004;GK-118-TR;Station Vitrolles A7;Vitrolles;GAZOLE;46,30;1,719;79,59;61220
                """);
        Map<String, Object> file = exchange("cbp_transactions.csv");
        assertThat(file).containsEntry("status", "PARTIAL").containsEntry("records", 5).containsEntry("accepted", 1);
        assertThat(issueCodes(file.get("id"))).containsExactlyInAnyOrder("TOO_FREQUENT", "WRONG_FUEL", "OVER_CAPACITY",
                "WRONG_FUEL", "UNKNOWN_VEHICLE");

        mvc.perform(get("/api/v1/fuel/transactions").param("onlyAnomalies", "true").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].reference", hasItem("CBP-3")));
        mvc.perform(get("/api/v1/fuel/summary").param("from", "2026-10-01").param("to", "2026-10-31").with(staff()))
                .andExpect(jsonPath("$.transactions").value(5))
                .andExpect(jsonPath("$.withAnomaly").value(4))
                .andExpect(jsonPath("$.anomaliesByType.WRONG_FUEL").value(2));
        // a client manager sees the transactions of their company only
        mvc.perform(get("/api/v1/fuel/transactions").with(clientManager(METROPOLE)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].registration").value("GE-907-RT"));
    }

    @Test
    void a_declared_claim_goes_to_the_insurer_and_its_decisions_come_back_step_by_step() throws Exception {
        UUID incident = UUID.randomUUID();
        camel.sendBody("direct:declare-claim", """
                {"eventId":"%s","type":"incident.declared","occurredAt":"%s","companyId":"%s","payload":{
                 "incidentId":"%s","reference":"SIN-2026-000777","vehicleId":"%s","registration":"GH-482-KM",
                 "type":"GLASS_BREAKAGE","occurredAt":"2026-10-14T07:30:00Z","injuries":false}}"""
                .formatted(UUID.randomUUID(), Instant.now(), NEOLIA, incident, UUID.randomUUID()));

        String claimNumber = jdbc.sql("select claim_number from insurer_claim where incident_id = :id")
                .param("id", incident).query(String.class).single();
        assertThat(claimNumber).startsWith("AF-");
        assertThat(exchange("SIN-2026-000777")).containsEntry("status", "PROCESSED").containsEntry("result_ref", claimNumber);

        waitUntil(() -> stubs.incidentMoves.getOrDefault(incident, List.of()).contains("CLOSED"));
        assertThat(stubs.incidentMoves.get(incident)).containsExactly("UNDER_REVIEW", "IN_REPAIR", "CLOSED");
        assertThat(stubs.assessments.get(incident)).startsWith("NONE ");
        assertThat((String) exchange(claimNumber).get("summary")).contains("expertise reçue").contains("indemnisé");

        // the same event delivered twice does not declare the claim twice
        camel.sendBody("direct:declare-claim", """
                {"type":"incident.declared","companyId":"%s","payload":{"incidentId":"%s","reference":"SIN-2026-000777"}}"""
                .formatted(NEOLIA, incident));
        assertThat(jdbc.sql("select count(*) from simulated_insurer_claim where incident_ref = 'SIN-2026-000777'")
                .query(Long.class).single()).isOne();
    }

    @Test
    void the_console_shows_the_routes_and_accepts_a_file_dropped_by_hand() throws Exception {
        mvc.perform(get("/api/v1/integration/flows").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].flow").value("GARAGE_QUOTES"))
                .andExpect(jsonPath("$[0].routes[0].status").value("Started"))
                .andExpect(jsonPath("$[3].routes[0].status").value("Stopped"));

        String created = mvc.perform(multipart("/api/v1/integration/inbox/{flow}", "GARAGE_QUOTES")
                        .file(new MockMultipartFile("file", "devis prado.xml", "application/xml",
                                quoteXml("PRD-2026-1090", "GH-482-KM", "45601270032976", "193.20")
                                        .getBytes(StandardCharsets.UTF_8)))
                        .with(staff()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String name = JsonPath.read(created, "$.fileName");
        assertThat(name).endsWith("_devis_prado.xml");
        assertThat(exchange(name)).containsEntry("status", "PROCESSED");

        mvc.perform(multipart("/api/v1/integration/inbox/{flow}", "GARAGE_QUOTES")
                        .file(new MockMultipartFile("file", "devis.pdf", "application/pdf", new byte[] {1}))
                        .with(staff()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_EXTENSION"));
    }

    @Test
    void the_console_is_for_fleetpilot_staff_and_the_insurer_api_wants_its_key() throws Exception {
        mvc.perform(get("/api/v1/integration/flows").with(clientManager(NEOLIA))).andExpect(status().isForbidden());
        mvc.perform(get("/partners/insurer/claims/AF-2026-000001")).andExpect(status().isUnauthorized());
    }
}
