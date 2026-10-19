package com.fleetpilot.notification.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.messaging.EmailRequest;

/** What the notification use cases need from the outside world, implemented in the infrastructure layer. */
public final class Ports {

    private Ports() {
    }

    /** The JMS work queue of the e-mails to send. */
    public interface NotificationQueue {
        void enqueue(EmailRequest request);
    }

    /** A file attached to an e-mail. */
    public record Attachment(String fileName, String contentType, byte[] content) {
    }

    /** A rendered e-mail, ready to send. */
    public record OutgoingMail(String to, String toName, String subject, String html, List<Attachment> attachments) {
    }

    /** The SMTP server. */
    public interface Mailer {
        /** @return the Message-ID of the sent e-mail */
        String send(OutgoingMail mail);
    }

    /** Someone who receives an e-mail. */
    public record Person(String name, String email) {
    }

    /** The fleet module: who drives which vehicle, and their e-mail. */
    public interface FleetDirectory {
        Optional<Person> currentDriver(UUID vehicleId);

        Optional<Person> driver(UUID driverId);
    }

    /** The quote module: a quote with its lines, for the purchase order. */
    public interface QuoteDirectory {
        Map<String, Object> quote(UUID quoteId);
    }
}
