package com.fleetpilot.quote.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteStatus;

/**
 * Payloads of the events published on the {@code quote.events} topic. Part of the public contract with the other
 * services (notifications, reporting): fields are only ever added, never renamed.
 */
public final class QuoteEvents {

    private QuoteEvents() {
    }

    /** Who the quote is about: enough for a consumer to write to the garage without calling the quote service. */
    public record GarageContact(UUID garageId, String name, String email, String city) {
        static GarageContact of(Quote quote) {
            return new GarageContact(quote.getGarage().getId(), quote.getGarage().getName(),
                    quote.getGarage().getEmail(), quote.getGarage().getCity());
        }
    }

    public record QuoteReceived(UUID quoteId, String reference, GarageContact garage, String registration,
            UUID vehicleId, BigDecimal amount, QuoteChannel channel, boolean vehicleIdentified) {

        public static QuoteReceived of(Quote q) {
            return new QuoteReceived(q.getId(), q.getReference(), GarageContact.of(q), q.getRegistration(),
                    q.getVehicle() == null ? null : q.getVehicle().getId(), q.getAmount(), q.getChannel(),
                    q.getVehicle() != null);
        }
    }

    public record QuoteAnalysed(UUID quoteId, String reference, QuoteStatus status, BigDecimal amount,
            BigDecimal referenceAmount, BigDecimal overcharge, int findingCount, boolean autoApproved) {

        public static QuoteAnalysed of(Quote q) {
            return new QuoteAnalysed(q.getId(), q.getReference(), q.getStatus(), q.getAmount(), q.getReferenceAmount(),
                    q.getOvercharge(), q.getFindingCount(), q.isAutoApproved());
        }
    }

    public record CounterOffered(UUID quoteId, String reference, GarageContact garage, String registration,
            BigDecimal amount, BigDecimal proposedAmount, String author) {

        public static CounterOffered of(Quote q, String author) {
            return new CounterOffered(q.getId(), q.getReference(), GarageContact.of(q), q.getRegistration(),
                    q.getAmount(), q.getCounterOffer(), author);
        }
    }

    /** Accepted or rejected: the notification service sends the purchase order or the refusal to the garage. */
    public record QuoteDecided(UUID quoteId, String reference, GarageContact garage, String registration,
            UUID vehicleId, String vehicle, QuoteStatus status, BigDecimal initialAmount, BigDecimal acceptedAmount,
            BigDecimal savings, boolean autoApproved, String decidedBy, String comment) {

        public static QuoteDecided of(Quote q) {
            String vehicle = q.getVehicle() == null ? null
                    : (q.getVehicle().getMake() + " " + q.getVehicle().getModel()).trim();
            return new QuoteDecided(q.getId(), q.getReference(), GarageContact.of(q), q.getRegistration(),
                    q.getVehicle() == null ? null : q.getVehicle().getId(), vehicle, q.getStatus(), q.getInitialAmount(),
                    q.getAcceptedAmount(), q.savings(), q.isAutoApproved(), q.getDecidedBy(), q.getDecisionComment());
        }
    }

    public record QuoteInvoiced(UUID quoteId, String reference, GarageContact garage, String invoiceNumber,
            BigDecimal invoiceAmount, BigDecimal acceptedAmount, BigDecimal variance, boolean disputed,
            LocalDate invoicedOn) {

        public static QuoteInvoiced of(Quote q) {
            return new QuoteInvoiced(q.getId(), q.getReference(), GarageContact.of(q), q.getInvoiceNumber(),
                    q.getInvoiceAmount(), q.getAcceptedAmount(), q.invoiceVariance(), q.isInvoiceDisputed(),
                    q.getInvoicedOn());
        }
    }
}
