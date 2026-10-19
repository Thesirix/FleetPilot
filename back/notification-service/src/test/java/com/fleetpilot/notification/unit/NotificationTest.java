package com.fleetpilot.notification.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fleetpilot.notification.application.MailFormats;
import com.fleetpilot.notification.domain.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification attempts and formats")
class NotificationTest {

    static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");

    private static Notification notification() {
        return new Notification(UUID.randomUUID(), "request-1", new Notification.Target("quote-accepted",
                "atelier@garage-du-prado.fr", "Garage du Prado", null, "quote", "42", "PURCHASE_ORDER"), "{}", NOW);
    }

    @Test
    void a_failed_e_mail_is_retried_then_given_up_after_five_attempts() {
        Notification n = notification();
        for (int attempt = 1; attempt < Notification.MAX_ATTEMPTS; attempt++) {
            assertThat(n.failed("SMTP unreachable")).isTrue();
            assertThat(n.getStatus()).isEqualTo(Notification.Status.QUEUED);
        }
        assertThat(n.failed("SMTP unreachable")).isFalse();
        assertThat(n.getStatus()).isEqualTo(Notification.Status.FAILED);
        assertThat(n.getAttempts()).isEqualTo(5);

        n.requeue();
        assertThat(n.getStatus()).isEqualTo(Notification.Status.QUEUED);
        assertThat(n.getAttempts()).isZero();
        assertThat(n.getLastError()).isNull();
    }

    @Test
    void a_sent_e_mail_keeps_its_rendering() {
        Notification n = notification();
        n.failed("x".repeat(800));
        assertThat(n.getLastError()).hasSize(500);
        n.sent("Bon de commande", "<html></html>", "<id@fleetpilot.dev>", NOW);
        assertThat(n.isSent()).isTrue();
        assertThat(n.getLastError()).isNull();
        assertThat(n.getAttempts()).isEqualTo(2);
        assertThat(n.getSentAt()).isEqualTo(NOW);
    }

    @Test
    void formats_follow_french_usage() {
        MailFormats fmt = MailFormats.INSTANCE;
        assertThat(fmt.money(1250)).isEqualTo("1 250,00 €");
        assertThat(fmt.money(null)).isEqualTo("-");
        assertThat(fmt.number(118000L)).isEqualTo("118 000");
        assertThat(fmt.date("2026-11-02")).isEqualTo("2 novembre 2026");
        assertThat(fmt.dateTime("2026-10-14T08:30:00Z")).isEqualTo("14 octobre 2026 à 10h30");
        assertThat(fmt.label("TECHNICAL_INSPECTION")).isEqualTo("contrôle technique");
        assertThat(fmt.label("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(fmt.withVat("100")).isEqualTo("120,00 €");
        assertThat(fmt.vat(100.0)).isEqualTo("20,00 €");
    }
}
