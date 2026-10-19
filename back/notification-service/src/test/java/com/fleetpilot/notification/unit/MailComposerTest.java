package com.fleetpilot.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.documents.PdfRenderer;
import com.fleetpilot.notification.application.MailComposer;
import com.fleetpilot.notification.application.Ports.OutgoingMail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@DisplayName("E-mails and purchase order rendering")
class MailComposerTest {

    static final String QUOTE_ID = UUID.randomUUID().toString();
    static final Map<String, Object> GARAGE = Map.of("name", "Garage du Prado", "email", "atelier@garage-du-prado.fr",
            "city", "Marseille");

    private final MailComposer composer = new MailComposer(engine(), new PdfRenderer(engine()), id -> quote(),
            "http://localhost");

    static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    static Map<String, Object> quote() {
        return Map.ofEntries(Map.entry("id", QUOTE_ID), Map.entry("reference", "DEV-2026-000042"),
                Map.entry("garage", GARAGE), Map.entry("registration", "GH-482-KM"),
                Map.entry("vehicle", Map.of("make", "Peugeot", "model", "308 SW")), Map.entry("mileage", 82000),
                Map.entry("description", "Disques et plaquettes avant"), Map.entry("garageReference", "D26-0042"),
                Map.entry("decidedAt", "2026-10-14T08:00:00Z"), Map.entry("decidedBy", "claire.morel"),
                Map.entry("amount", 284.0), Map.entry("acceptedAmount", 190.0),
                Map.entry("lines", List.of(
                        Map.of("position", 1, "kind", "PART", "code", "DISQ-AV", "label", "Disques avant",
                                "quantity", 1.0, "unitPrice", 140.0, "amount", 140.0),
                        Map.of("position", 2, "kind", "LABOUR", "code", "FREIN-AV-DISQ", "label", "Main-d'œuvre",
                                "quantity", 2.0, "unitPrice", 72.0, "amount", 144.0))));
    }

    static Stream<Arguments> templates() {
        String vehicle = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of("quote-accepted", Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042",
                        "registration", "GH-482-KM", "vehicle", "Peugeot 308 SW", "initialAmount", 284.0,
                        "acceptedAmount", 190.0, "autoApproved", false, "garage", GARAGE),
                        "Bon de commande : devis DEV-2026-000042 accepté"),
                Arguments.of("quote-rejected", Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042",
                        "registration", "GH-482-KM", "comment", "Couvert par le contrat du loueur"),
                        "Votre devis DEV-2026-000042 n'est pas retenu"),
                Arguments.of("quote-counter-offer", Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042",
                        "registration", "GH-482-KM", "amount", 284.0, "proposedAmount", 190.0),
                        "Contre-proposition sur votre devis DEV-2026-000042"),
                Arguments.of("invoice-disputed", Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042",
                        "invoiceNumber", "F26-0099", "garage", GARAGE, "acceptedAmount", 102.6, "invoiceAmount", 130.0,
                        "variance", 27.4, "invoicedOn", "2026-10-14"), "Facture contestée : F26-0099 (devis DEV-2026-000042)"),
                Arguments.of("deadline-reminder", Map.of("vehicleId", vehicle, "registration", "GH-482-KM",
                        "type", "TECHNICAL_INSPECTION", "level", "D7", "dueDate", "2026-10-20", "currentMileage", 82000),
                        "Rappel : contrôle technique du véhicule GH-482-KM"),
                Arguments.of("vehicle-assigned", Map.of("vehicleId", vehicle, "registration", "GH-482-KM",
                        "driverName", "Camille Martin", "kind", "PERMANENT", "startDate", "2026-10-14"),
                        "Le véhicule GH-482-KM vous est attribué"),
                Arguments.of("incident-declared", Map.of("incidentId", vehicle, "reference", "SIN-2026-000301",
                        "registration", "GH-482-KM", "type", "COLLISION", "injuries", true,
                        "occurredAt", "2026-10-14T07:30:00Z"), "Sinistre avec blessés : SIN-2026-000301 (GH-482-KM)"),
                Arguments.of("incident-received", Map.of("reference", "SIN-2026-000301", "registration", "GH-482-KM"),
                        "Votre déclaration de sinistre SIN-2026-000301 est enregistrée"),
                Arguments.of("contract-overrun", Map.of("vehicleId", vehicle, "registration", "GH-482-KM",
                        "contractNumber", "ARV-2024-00042", "lessor", "Arval", "contractualMileage", 90000,
                        "projectedMileage", 118000, "projectedOverrunKm", 28000, "endDate", "2027-12-31"),
                        "Dépassement kilométrique prévu : contrat ARV-2024-00042 (GH-482-KM)"),
                Arguments.of("vehicle-alert", Map.of("vehicleId", vehicle, "registration", "GH-482-KM",
                        "type", "ENGINE_FAULT", "severity", "CRITICAL", "message", "P0217 : Surchauffe moteur",
                        "latitude", 43.2951, "longitude", 5.374, "raisedAt", "2026-10-14T07:30:00Z"),
                        "Alerte critique : défaut grave sur GH-482-KM"),
                Arguments.of("report-ready", Map.of("title", "Inventaire du parc", "fileName", "inventaire.xlsx",
                        "rows", 1500), "Votre rapport est prêt : Inventaire du parc"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templates")
    void every_template_renders_its_subject_and_body(String template, Map<String, Object> model, String subject) {
        OutgoingMail mail = composer.compose(new EmailRequest("r", template, "x@fleetpilot.dev", "Camille Martin", null,
                "quote", QUOTE_ID, model, List.of()));
        assertThat(mail.subject()).isEqualTo(subject);
        assertThat(mail.html()).contains("FleetPilot").doesNotContain(" th:").doesNotContain("${");
    }

    @Test
    void amounts_and_dates_are_written_the_french_way() {
        OutgoingMail mail = composer.compose(new EmailRequest("r", "quote-accepted", "x@fleetpilot.dev", null, null,
                "quote", QUOTE_ID, Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042", "registration",
                        "GH-482-KM", "initialAmount", 1284.5, "acceptedAmount", 1190.0, "autoApproved", true,
                        "garage", GARAGE), List.of()));
        assertThat(mail.html()).contains("1 190,00 €", "1 428,00 €", "validé automatiquement",
                "http://localhost/devis/" + QUOTE_ID);
    }

    @Test
    void an_accepted_quote_comes_with_its_purchase_order_in_pdf() throws IOException {
        OutgoingMail mail = composer.compose(new EmailRequest("r", "quote-accepted", "x@fleetpilot.dev", null, null,
                "quote", QUOTE_ID, Map.of("quoteId", QUOTE_ID, "reference", "DEV-2026-000042", "registration",
                        "GH-482-KM", "initialAmount", 284.0, "acceptedAmount", 190.0, "autoApproved", false,
                        "garage", GARAGE), List.of(MailComposer.PURCHASE_ORDER)));
        writeForReview("bon-de-commande.pdf", mail.attachments().getFirst().content());
        assertThat(mail.attachments()).singleElement().satisfies(pdf -> {
            assertThat(pdf.fileName()).isEqualTo("bon-de-commande-DEV-2026-000042.pdf");
            assertThat(pdf.contentType()).isEqualTo("application/pdf");
            assertThat(new String(pdf.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        });
    }

    /** Keeps the document in target/documents, to look at it after the build. */
    static void writeForReview(String name, byte[] content) throws IOException {
        Path folder = Path.of("target", "documents");
        Files.createDirectories(folder);
        Files.write(folder.resolve(name), content);
    }

    @Test
    void an_unknown_attachment_is_an_error() {
        assertThatThrownBy(() -> composer.compose(new EmailRequest("r", "report-ready", "x@fleetpilot.dev", null, null,
                null, QUOTE_ID, Map.of("title", "t", "fileName", "f", "rows", 1), List.of("INVOICE"))))
                .hasMessageContaining("INVOICE");
    }
}
