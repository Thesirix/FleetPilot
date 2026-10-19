package com.fleetpilot.integration.fleet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The calls the bus makes to the business services of FleetPilot, as itself. */
public final class BusinessServices {

    private BusinessServices() {
    }

    /** A quote read from a garage's file, in the format of the quote module. */
    public record QuoteSubmission(String garageSiret, String registration, String garageReference, Integer mileage,
            String description, List<QuoteLine> lines) {
    }

    public record QuoteLine(String kind, String code, String label, BigDecimal quantity, BigDecimal unitPrice) {
    }

    /** The quote created in the quote module. */
    public record CreatedQuote(UUID id, String reference, String status, BigDecimal amount, Garage garage,
            Vehicle vehicle) {

        public record Garage(UUID id, String name) {
        }

        public record Vehicle(UUID id, String registration) {
        }
    }

    /**
     * A business service refused the request (4xx): the file is wrong, retrying would not help.
     *
     * @param code the error code of the service (UNKNOWN_GARAGE, QUOTE_ALREADY_RECEIVED...)
     */
    public static class Refused extends RuntimeException {

        private final String code;

        public Refused(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public interface QuoteModule {

        /** @throws Refused when the quote module refuses the quote */
        CreatedQuote submit(QuoteSubmission quote);
    }

    /** Reference data of the quote module, for the demo files of the garages. */
    public interface QuoteReference {

        record Garage(UUID id, String code, String name, String siret, String city, BigDecimal hourlyRate) {
        }

        record Part(String code, String label, BigDecimal priceCar, BigDecimal priceVan) {
        }

        record Operation(String code, String label, String labourCategory, BigDecimal hoursCar, BigDecimal hoursVan) {
        }

        record Tariff(List<Part> parts, List<Operation> operations) {
        }

        Optional<Garage> garage(String code);

        Tariff tariff();
    }

    public interface IncidentModule {

        /** Current status of a claim in FleetPilot. */
        String status(UUID incidentId);

        void assess(UUID incidentId, String responsibility, BigDecimal estimatedCost);

        void moveTo(UUID incidentId, String status);
    }
}
