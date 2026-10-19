package com.fleetpilot.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fleetpilot.common.event.EventEnvelope;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.notification.application.Ports.FleetDirectory;
import com.fleetpilot.notification.application.Ports.NotificationQueue;
import com.fleetpilot.notification.application.Ports.Person;
import com.fleetpilot.notification.application.Ports.QuoteDirectory;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The whole chain on real infrastructure: an event on Kafka becomes a request on the ActiveMQ queue, which becomes an
 * e-mail received by a real SMTP server (Mailpit), with its PDF attachment.
 */
@SpringBootTest(properties = "fleetpilot.notifications.redelivery-delay=200")
@AutoConfigureMockMvc
@Import(NotificationFlowIT.Stubs.class)
@DisplayName("From a business event to the mailbox")
class NotificationFlowIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    @ServiceConnection
    static final ActiveMQContainer ACTIVEMQ = new ActiveMQContainer("apache/activemq-classic:6.2.0");

    @SuppressWarnings("resource")
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.31.6")
            .withExposedPorts(1025, 8025);

    static final String QUOTE_ID = UUID.randomUUID().toString();

    static {
        POSTGRES.start();
        KAFKA.start();
        ACTIVEMQ.start();
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private NotificationQueue queue;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor staff() {
        return jwt().jwt(token -> token.claim("preferred_username", "claire.morel")
                        .claim("realm_access", Map.of("roles", List.of("fleet-manager"))))
                .authorities(new SimpleGrantedAuthority("ROLE_fleet-manager"));
    }

    @Test
    void an_accepted_quote_reaches_the_garage_with_its_purchase_order() throws Exception {
        EventEnvelope<Object> event = EventEnvelope.of(EventTypes.QUOTE_ACCEPTED, Instant.now(), "quote-service",
                UUID.randomUUID(), "corr-1", Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042",
                        "registration", "GH-482-KM", "vehicle", "Peugeot 308 SW", "initialAmount", 284.0,
                        "acceptedAmount", 190.0, "autoApproved", false, "comment", "Alignement sur le tarif",
                        "garage", Map.of("name", "Garage du Prado", "email", "atelier@garage-du-prado.fr",
                                "city", "Marseille")));
        kafka.send(EventTypes.TOPIC_QUOTES, QUOTE_ID, jsonMapper.writeValueAsString(event)).get();

        String message = waitForMessage("Bon de commande : devis DEV-2026-000042 accepté");
        assertThat(JsonPath.<String>read(message, "$.To[0].Address")).isEqualTo("atelier@garage-du-prado.fr");
        assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("noreply@fleetpilot.dev");
        assertThat(JsonPath.<String>read(message, "$.HTML")).contains("190,00 €", "Alignement sur le tarif");
        assertThat(JsonPath.<List<String>>read(message, "$.Attachments[*].FileName"))
                .containsExactly("bon-de-commande-DEV-2026-000042.pdf");

        mvc.perform(get("/api/v1/notifications").param("q", "garage-du-prado").with(staff()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].template").value("quote-accepted"))
                .andExpect(jsonPath("$.content[0].attachments").value("PURCHASE_ORDER"));
    }

    @Test
    void a_request_received_twice_is_sent_once() throws Exception {
        EmailRequest request = new EmailRequest(UUID.randomUUID().toString(), "report-ready", "m.roux@neolia-energies.fr",
                "Mathilde Roux", null, "report", "r1", Map.of("title", "Inventaire du parc " + UUID.randomUUID(),
                        "fileName", "inventaire.xlsx", "rows", 1500), List.of());
        queue.enqueue(request);
        queue.enqueue(request);

        waitForMessage("Votre rapport est prêt : " + request.model().get("title"));
        waitUntil(() -> count("select count(*) from notification where request_id = '" + request.requestId()
                + "' and status = 'SENT'") == 1);
        Thread.sleep(1_000);
        assertThat(mailCount((String) request.model().get("title"))).isOne();
    }

    @Test
    void a_failing_e_mail_is_retried_then_kept_as_failed_and_can_be_sent_again() throws Exception {
        EmailRequest broken = new EmailRequest(UUID.randomUUID().toString(), "template-that-does-not-exist",
                "x@fleetpilot.dev", null, null, null, null, Map.of(), List.of());
        queue.enqueue(broken);

        waitUntil(() -> count("select count(*) from notification where request_id = '" + broken.requestId()
                + "' and status = 'FAILED' and attempts = 5") == 1);
        String id = jdbc.sql("select id from notification where request_id = :r").param("r", broken.requestId())
                .query(String.class).single();
        mvc.perform(get("/api/v1/notifications/stats").with(staff())).andExpect(jsonPath("$.failed").value(1));
        mvc.perform(post("/api/v1/notifications/" + id + "/retry").with(staff()))
                .andExpect(jsonPath("$.summary.status").value("QUEUED"))
                .andExpect(jsonPath("$.summary.attempts").value(0));
    }

    @Test
    void the_journal_is_for_staff_only() throws Exception {
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(token -> token.claim("realm_access",
                        Map.of("roles", List.of("driver")))).authorities(new SimpleGrantedAuthority("ROLE_driver"))))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- helpers

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static String mailpit(String path) {
        try (HttpClient client = HttpClient.newHttpClient()) {
            URI uri = URI.create("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + path);
            return client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long mailCount(String subjectPart) {
        List<String> subjects = JsonPath.read(mailpit("/api/v1/messages?limit=200"), "$.messages[*].Subject");
        return subjects.stream().filter(s -> s.contains(subjectPart)).count();
    }

    /** Waits for an e-mail with this subject and returns it in full (body, attachments). */
    private static String waitForMessage(String subject) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            List<Map<String, Object>> messages = JsonPath.read(mailpit("/api/v1/messages?limit=200"), "$.messages");
            Optional<String> id = messages.stream().filter(m -> subject.equals(m.get("Subject")))
                    .map(m -> (String) m.get("ID")).findFirst();
            if (id.isPresent()) {
                return mailpit("/api/v1/message/" + id.get());
            }
            Thread.sleep(300);
        }
        throw new AssertionError("No e-mail with the subject: " + subject);
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition reached in time").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    @TestConfiguration
    static class Stubs {

        @Bean
        @Primary
        QuoteDirectory stubQuotes() {
            return id -> Map.of("id", QUOTE_ID, "reference", "DEV-2026-000042", "registration", "GH-482-KM",
                    "garage", Map.of("name", "Garage du Prado", "city", "Marseille"), "description", "Freins avant",
                    "decidedAt", "2026-10-14T08:00:00Z", "decidedBy", "claire.morel", "amount", 284.0,
                    "acceptedAmount", 190.0, "lines", List.of(Map.of("position", 1, "kind", "PART", "code", "DISQ-AV",
                            "label", "Disques avant", "quantity", 1.0, "unitPrice", 140.0, "amount", 140.0)));
        }

        @Bean
        @Primary
        FleetDirectory stubFleet() {
            return new FleetDirectory() {
                @Override
                public Optional<Person> currentDriver(UUID vehicleId) {
                    return Optional.empty();
                }

                @Override
                public Optional<Person> driver(UUID driverId) {
                    return Optional.empty();
                }
            };
        }
    }
}
