package com.fleetpilot.quote.application.view;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.Finding;
import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.LabourOperation;
import com.fleetpilot.quote.domain.model.LabourRate;
import com.fleetpilot.quote.domain.model.LineKind;
import com.fleetpilot.quote.domain.model.PartReference;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteHistoryEntry;
import com.fleetpilot.quote.domain.model.QuoteLine;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.VehicleRef;
import io.swagger.v3.oas.annotations.media.Schema;

/** Read models of the quote module, built inside the service transaction and exposed as-is by the REST API. */
public final class QuoteViews {

    private QuoteViews() {
    }

    /** What the current user may do on a quote; the portal shows exactly these buttons. */
    public enum QuoteAction {
        IDENTIFY_VEHICLE,
        COUNTER_OFFER,
        ACCEPT,
        REJECT,
        ANSWER_COUNTER_OFFER,
        REVISE,
        COMPLETE,
        INVOICE
    }

    // ---------------------------------------------------------------- references

    public record GarageRef(UUID id, String name, String city, GarageNetwork network) {
        public static GarageRef of(Garage g) {
            return new GarageRef(g.getId(), g.getName(), g.getCity(), g.getNetwork());
        }
    }

    public record VehicleView(UUID id, UUID companyId, String registration, String make, String model, String category,
            String energy) {
        public static VehicleView of(VehicleRef v) {
            return v == null ? null : new VehicleView(v.getId(), v.getCompanyId(), v.getRegistration(), v.getMake(),
                    v.getModel(), v.getCategory(), v.getEnergy());
        }
    }

    // ---------------------------------------------------------------- quotes

    public record QuoteSummary(UUID id, String reference, QuoteStatus status, GarageRef garage, String registration,
            VehicleView vehicle, UUID companyId, QuoteChannel channel, Instant submittedAt, BigDecimal amount,
            BigDecimal referenceAmount, BigDecimal overcharge, BigDecimal acceptedAmount, BigDecimal savings,
            int findingCount, boolean autoApproved, BigDecimal counterOffer, int revision, boolean invoiceDisputed) {

        public static QuoteSummary of(Quote q) {
            return new QuoteSummary(q.getId(), q.getReference(), q.getStatus(), GarageRef.of(q.getGarage()),
                    q.getRegistration(), VehicleView.of(q.getVehicle()), q.getCompanyId(), q.getChannel(),
                    q.getSubmittedAt(), q.getAmount(), q.getReferenceAmount(), q.getOvercharge(), q.getAcceptedAmount(),
                    q.savings(), q.getFindingCount(), q.isAutoApproved(), q.getCounterOffer(), q.getRevision(),
                    q.isInvoiceDisputed());
        }
    }

    public record LineView(UUID id, int position, LineKind kind, String code, String label, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal amount, BigDecimal referenceQuantity, BigDecimal referenceUnitPrice,
            BigDecimal referenceAmount, BigDecimal overcharge, Finding finding, String findingDetail) {

        public static LineView of(QuoteLine l) {
            return new LineView(l.getId(), l.getPosition(), l.getKind(), l.getCode(), l.getLabel(), l.getQuantity(),
                    l.getUnitPrice(), l.getAmount(), l.getReferenceQuantity(), l.getReferenceUnitPrice(),
                    l.getReferenceAmount(), l.overcharge(), l.getFinding(), l.getFindingDetail());
        }
    }

    public record HistoryView(Instant at, QuoteHistoryEntry.Party party, String author, QuoteHistoryEntry.Action action,
            BigDecimal amount, String message) {

        public static HistoryView of(QuoteHistoryEntry e) {
            return new HistoryView(e.getAt(), e.getParty(), e.getAuthor(), e.getAction(), e.getAmount(), e.getMessage());
        }
    }

    public record QuoteDetail(UUID id, String reference, QuoteStatus status, GarageRef garage, String registration,
            VehicleView vehicle, UUID companyId, QuoteChannel channel, String garageReference, Integer mileage,
            String description, int revision, Instant submittedAt, Instant analysedAt, BigDecimal amount,
            BigDecimal initialAmount, BigDecimal referenceAmount, BigDecimal overcharge, int findingCount,
            BigDecimal counterOffer, BigDecimal acceptedAmount, BigDecimal savings, boolean autoApproved,
            Instant decidedAt, String decidedBy, String decisionComment, LocalDate completedOn, String invoiceNumber,
            BigDecimal invoiceAmount, LocalDate invoicedOn, BigDecimal invoiceVariance, boolean invoiceDisputed,
            List<LineView> lines, List<HistoryView> history, Set<QuoteAction> actions, long version) {

        public static QuoteDetail of(Quote q, Set<QuoteAction> actions) {
            return new QuoteDetail(q.getId(), q.getReference(), q.getStatus(), GarageRef.of(q.getGarage()),
                    q.getRegistration(), VehicleView.of(q.getVehicle()), q.getCompanyId(), q.getChannel(),
                    q.getGarageReference(), q.getMileage(), q.getDescription(), q.getRevision(), q.getSubmittedAt(),
                    q.getAnalysedAt(), q.getAmount(), q.getInitialAmount(), q.getReferenceAmount(), q.getOvercharge(),
                    q.getFindingCount(), q.getCounterOffer(), q.getAcceptedAmount(), q.savings(), q.isAutoApproved(),
                    q.getDecidedAt(), q.getDecidedBy(), q.getDecisionComment(), q.getCompletedOn(), q.getInvoiceNumber(),
                    q.getInvoiceAmount(), q.getInvoicedOn(), q.invoiceVariance(), q.isInvoiceDisputed(),
                    q.getLines().stream().map(LineView::of).toList(),
                    q.getHistory().stream().map(HistoryView::of).toList(), actions,
                    q.getVersion() == null ? 0 : q.getVersion());
        }
    }

    // ---------------------------------------------------------------- garages

    public record GarageSummary(UUID id, String code, String name, GarageNetwork network, String brand, String city,
            String postalCode, Region region, BigDecimal hourlyRate, boolean approved, BigDecimal rating,
            BigDecimal latitude, BigDecimal longitude) {

        public static GarageSummary of(Garage g) {
            return new GarageSummary(g.getId(), g.getCode(), g.getName(), g.getNetwork(), g.getBrand(), g.getCity(),
                    g.getPostalCode(), g.getRegion(), g.getHourlyRate(), g.isApproved(), g.getRating(), g.getLatitude(),
                    g.getLongitude());
        }
    }

    /**
     * Track record of a garage, computed from its quotes.
     *
     * @param overchargeRate  overcharges detected / amounts quoted, in percent
     * @param averageDecisionHours average time between the submission and the decision
     */
    public record GarageStats(long quotes, long openQuotes, long acceptedQuotes, long rejectedQuotes,
            BigDecimal amountQuoted, BigDecimal overchargeDetected, BigDecimal overchargeRate, BigDecimal savings,
            BigDecimal averageDecisionHours, long disputedInvoices, Instant lastQuoteAt) {
    }

    @Schema(description = "An approved garage near the driver, with what the app needs to call it or go there")
    public record NearbyGarage(UUID id, String name, GarageNetwork network, String brand, String address,
            String postalCode, String city, String phone, BigDecimal hourlyRate, BigDecimal rating, BigDecimal latitude,
            BigDecimal longitude, @Schema(description = "As the crow flies, in kilometres", example = "2.4")
            BigDecimal distanceKm) {

        public static NearbyGarage of(Garage g, double distanceKm) {
            return new NearbyGarage(g.getId(), g.getName(), g.getNetwork(), g.getBrand(), g.getAddress(),
                    g.getPostalCode(), g.getCity(), g.getPhone(), g.getHourlyRate(), g.getRating(), g.getLatitude(),
                    g.getLongitude(), BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP));
        }
    }

    public record GarageDetail(UUID id, String code, String name, GarageNetwork network, String brand, String siret,
            String address, String postalCode, String city, Region region, String regionLabel, BigDecimal latitude,
            BigDecimal longitude, String email, String phone, BigDecimal hourlyRate, boolean approved,
            LocalDate approvedSince, BigDecimal rating, GarageStats stats, long version) {

        public static GarageDetail of(Garage g, GarageStats stats) {
            return new GarageDetail(g.getId(), g.getCode(), g.getName(), g.getNetwork(), g.getBrand(), g.getSiret(),
                    g.getAddress(), g.getPostalCode(), g.getCity(), g.getRegion(), g.getRegion().label(),
                    g.getLatitude(), g.getLongitude(), g.getEmail(), g.getPhone(), g.getHourlyRate(), g.isApproved(),
                    g.getApprovedSince(), g.getRating(), stats, g.getVersion() == null ? 0 : g.getVersion());
        }
    }

    // ---------------------------------------------------------------- reference tariff

    public record PartView(String code, String label, String family, BigDecimal priceCar, BigDecimal priceVan,
            BigDecimal priceTruck) {
        public static PartView of(PartReference p) {
            return new PartView(p.getCode(), p.getLabel(), p.getFamily(), p.getPriceCar(), p.getPriceVan(),
                    p.getPriceTruck());
        }
    }

    public record OperationView(String code, String label, LabourCategory labourCategory, BigDecimal hoursCar,
            BigDecimal hoursVan, BigDecimal hoursTruck) {
        public static OperationView of(LabourOperation o) {
            return new OperationView(o.getCode(), o.getLabel(), o.getLabourCategory(), o.getHoursCar(), o.getHoursVan(),
                    o.getHoursTruck());
        }
    }

    public record RateView(Region region, String regionLabel, LabourCategory labourCategory, BigDecimal hourlyRate,
            BigDecimal truckHourlyRate) {
        public static RateView of(LabourRate r) {
            return new RateView(r.getKey().region(), r.getKey().region().label(), r.getKey().labourCategory(),
                    r.getHourlyRate(), r.getTruckHourlyRate());
        }
    }

    public record TariffView(List<PartView> parts, List<OperationView> operations, List<RateView> rates) {
    }

    // ---------------------------------------------------------------- savings dashboard

    public record MonthlySavings(String month, long quotes, BigDecimal quoted, BigDecimal accepted, BigDecimal savings) {
    }

    public record FindingCount(Finding finding, long lines, BigDecimal overcharge) {
    }

    public record GarageRanking(UUID garageId, String name, String city, long quotes, BigDecimal overchargeRate,
            BigDecimal savings) {
    }

    /**
     * Indicators of the savings module.
     *
     * @param savings          what negotiation saved on the decided quotes (initial amount minus accepted amount)
     * @param pendingOvercharge overcharges detected on the quotes still open: what is left to negotiate
     * @param autoApprovalRate share of the accepted quotes approved without human action, in percent
     */
    public record SavingsSummary(long quotes, long openQuotes, long toNegotiate, long toReview, long received,
            BigDecimal amountQuoted, BigDecimal amountAccepted, BigDecimal savings, BigDecimal savingsRate,
            BigDecimal pendingOvercharge, BigDecimal autoApprovalRate, long disputedInvoices,
            List<MonthlySavings> months, List<FindingCount> findings, List<GarageRanking> garagesToWatch) {
    }
}
