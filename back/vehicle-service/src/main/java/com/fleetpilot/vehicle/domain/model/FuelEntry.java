package com.fleetpilot.vehicle.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A fill-up or a charge typed by the driver in the app, with the receipt amount. Fuel cards report their transactions
 * through the ESB; this entry also covers what is paid personally and claimed back as an expense.
 */
@Entity
@Table(name = "fuel_entry")
public class FuelEntry {

    public enum Kind {
        /** Litres of petrol, diesel, LPG... */
        FUEL,
        /** kWh of electricity. */
        CHARGE
    }

    public enum Payment {
        FUEL_CARD,
        /** Paid by the driver, to be refunded as an expense. */
        PERSONAL
    }

    /** Entries typed later than this after the fill-up are refused: the receipt is lost or the date is wrong. */
    static final Duration MAX_AGE = Duration.ofDays(90);
    /** Phones are not always on time: a few minutes ahead is accepted. */
    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Column(name = "filled_at", nullable = false)
    private Instant filledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private int mileage;

    private String station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Payment payment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FuelEntry() {
    }

    /** What the driver types, from the receipt. */
    public record Receipt(Instant filledAt, Kind kind, BigDecimal quantity, BigDecimal amount, int mileage,
            String station, Payment payment) {

        public Receipt {
            Objects.requireNonNull(filledAt, "filledAt");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(quantity, "quantity");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(payment, "payment");
        }
    }

    public FuelEntry(UUID id, Vehicle vehicle, UUID driverId, Receipt receipt, Instant now) {
        if (receipt.filledAt().isAfter(now.plus(CLOCK_TOLERANCE))) {
            throw new BusinessRuleException("FILL_UP_IN_FUTURE", "A fill-up cannot be in the future");
        }
        if (receipt.filledAt().isBefore(now.minus(MAX_AGE))) {
            throw new BusinessRuleException("FILL_UP_TOO_OLD", "A fill-up older than 90 days cannot be entered");
        }
        if (!accepts(vehicle.getEnergy(), receipt.kind())) {
            throw new BusinessRuleException("WRONG_ENERGY", receipt.kind() == Kind.CHARGE
                    ? "Vehicle " + vehicle.getRegistration() + " cannot be charged"
                    : "Vehicle " + vehicle.getRegistration() + " is electric: enter a charge");
        }
        if (receipt.quantity().signum() <= 0 || receipt.amount().signum() < 0) {
            throw new BusinessRuleException("INVALID_RECEIPT", "The quantity must be positive and the amount not negative");
        }
        BigDecimal unitPrice = receipt.amount().divide(receipt.quantity(), 3, RoundingMode.HALF_UP);
        // Charging at the depot can be free; fuel is never free nor at 4 euros a litre.
        boolean plausible = receipt.kind() == Kind.CHARGE
                ? unitPrice.compareTo(new BigDecimal("1.50")) <= 0
                : unitPrice.compareTo(new BigDecimal("0.50")) >= 0 && unitPrice.compareTo(new BigDecimal("4.00")) <= 0;
        if (!plausible) {
            throw new BusinessRuleException("IMPLAUSIBLE_PRICE",
                    "The price per " + (receipt.kind() == Kind.CHARGE ? "kWh" : "litre") + " (" + unitPrice
                            + " euros) is not plausible: check the quantity and the amount");
        }
        this.id = Objects.requireNonNull(id);
        this.vehicleId = vehicle.getId();
        this.driverId = Objects.requireNonNull(driverId);
        this.filledAt = receipt.filledAt();
        this.kind = receipt.kind();
        this.quantity = receipt.quantity();
        this.amount = receipt.amount();
        this.mileage = receipt.mileage();
        this.station = receipt.station() == null || receipt.station().isBlank() ? null : receipt.station().trim();
        this.payment = receipt.payment();
        this.createdAt = now;
    }

    /** A plug-in hybrid takes both; an electric vehicle only charges; every other vehicle only fills up. */
    public static boolean accepts(Energy energy, Kind kind) {
        return switch (energy) {
            case PLUG_IN_HYBRID -> true;
            case ELECTRIC -> kind == Kind.CHARGE;
            default -> kind == Kind.FUEL;
        };
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getDriverId() {
        return driverId;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public Kind getKind() {
        return kind;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getMileage() {
        return mileage;
    }

    public String getStation() {
        return station;
    }

    public Payment getPayment() {
        return payment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
