package com.fleetpilot.vehicle.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Check of a vehicle by its driver (état des lieux): when they take it, when they give it back, or once a month. Every
 * point of the checklist is answered; a damaged or missing point says what is wrong, and photos can be added.
 */
@Entity
@Table(name = "inspection")
public class Inspection {

    public enum Kind {
        PICKUP,
        RETURN,
        PERIODIC
    }

    /** The checklist, in the order the driver walks around the vehicle. */
    public enum Item {
        BODYWORK,
        WINDSCREEN,
        TYRES,
        LIGHTS,
        INTERIOR,
        CLEANLINESS,
        SAFETY_KIT,
        DOCUMENTS
    }

    public enum Condition {
        OK,
        DAMAGED,
        MISSING
    }

    /** One answered point of the checklist. */
    @Embeddable
    public static class Check {

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private Item item;

        @Enumerated(EnumType.STRING)
        @Column(name = "condition", nullable = false)
        private Condition condition;

        private String comment;

        protected Check() {
        }

        public Check(Item item, Condition condition, String comment) {
            this.item = Objects.requireNonNull(item, "item");
            this.condition = Objects.requireNonNull(condition, "condition");
            this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        }

        public Item getItem() {
            return item;
        }

        public Condition getCondition() {
            return condition;
        }

        public String getComment() {
            return comment;
        }
    }

    static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);
    static final Duration MAX_AGE = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "driver_id", nullable = false, updatable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(nullable = false)
    private int mileage;

    @Column(name = "fuel_level", nullable = false)
    private int fuelLevel;

    private String remarks;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "inspection_item", joinColumns = @JoinColumn(name = "inspection_id"))
    private List<Check> checks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Inspection() {
    }

    public Inspection(UUID id, UUID vehicleId, UUID driverId, Kind kind, Instant performedAt, int mileage, int fuelLevel,
            String remarks, List<Check> checks, Instant now) {
        if (performedAt.isAfter(now.plus(CLOCK_TOLERANCE)) || performedAt.isBefore(now.minus(MAX_AGE))) {
            throw new BusinessRuleException("INSPECTION_DATE", "An inspection is entered within 7 days, never ahead");
        }
        if (fuelLevel < 0 || fuelLevel > 100) {
            throw new BusinessRuleException("FUEL_LEVEL", "The fuel or battery level is a percentage");
        }
        Set<Item> answered = checks.stream()
                .map(Check::getItem)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Item.class)));
        if (answered.size() != checks.size()) {
            throw new BusinessRuleException("CHECKLIST_DUPLICATE", "A point of the checklist is answered twice");
        }
        if (!answered.equals(EnumSet.allOf(Item.class))) {
            EnumSet<Item> missing = EnumSet.allOf(Item.class);
            missing.removeAll(answered);
            throw new BusinessRuleException("CHECKLIST_INCOMPLETE", "Points of the checklist are not answered: " + missing);
        }
        checks.stream()
                .filter(check -> check.getCondition() != Condition.OK && check.getComment() == null)
                .findFirst()
                .ifPresent(check -> {
                    throw new BusinessRuleException("DEFECT_WITHOUT_COMMENT",
                            "Say what is wrong with " + check.getItem());
                });
        this.id = Objects.requireNonNull(id);
        this.vehicleId = Objects.requireNonNull(vehicleId);
        this.driverId = Objects.requireNonNull(driverId);
        this.kind = Objects.requireNonNull(kind);
        this.performedAt = performedAt;
        this.mileage = mileage;
        this.fuelLevel = fuelLevel;
        this.remarks = remarks == null || remarks.isBlank() ? null : remarks.trim();
        this.checks = checks.stream()
                .sorted(Comparator.comparing(Check::getItem))
                .collect(Collectors.toCollection(ArrayList::new));
        this.createdAt = now;
    }

    /** Points found damaged or missing: what the fleet manager has to look at. */
    public List<Check> defects() {
        return checks.stream().filter(check -> check.getCondition() != Condition.OK).toList();
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

    public Kind getKind() {
        return kind;
    }

    public Instant getPerformedAt() {
        return performedAt;
    }

    public int getMileage() {
        return mileage;
    }

    public int getFuelLevel() {
        return fuelLevel;
    }

    public String getRemarks() {
        return remarks;
    }

    public List<Check> getChecks() {
        return List.copyOf(checks);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
