package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A dated and/or mileage-based obligation of a vehicle (technical inspection, service...). Whichever limit comes
 * first applies. The urgency is never stored: it is computed against today's date and the current mileage.
 */
@Entity
@Table(name = "deadline")
public class Deadline {

    static final int DUE_SOON_DAYS = 30;
    static final int DUE_SOON_KM = 1_500;
    static final int D7_DAYS = 7;
    static final int D7_KM = 500;

    public enum Status {
        OPEN,
        DONE,
        CANCELLED
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadlineType type;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "due_mileage")
    private Integer dueMileage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "completed_mileage")
    private Integer completedMileage;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_alert")
    private AlertLevel lastAlert;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Deadline() {
    }

    public Deadline(UUID id, Vehicle vehicle, DeadlineType type, LocalDate dueDate, Integer dueMileage) {
        if (dueDate == null && dueMileage == null) {
            throw new IllegalArgumentException("A deadline needs a due date or a due mileage");
        }
        this.id = Objects.requireNonNull(id);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.type = Objects.requireNonNull(type);
        this.dueDate = dueDate;
        this.dueMileage = dueMileage;
        this.status = Status.OPEN;
    }

    public Urgency urgency(LocalDate today, int currentMileage) {
        if (isOverdue(today, currentMileage)) {
            return Urgency.OVERDUE;
        }
        if (isWithin(today, currentMileage, DUE_SOON_DAYS, DUE_SOON_KM)) {
            return Urgency.DUE_SOON;
        }
        return Urgency.UPCOMING;
    }

    /** The most severe reminder level reached today, if it has not been sent yet. */
    public Optional<AlertLevel> alertToSend(LocalDate today, int currentMileage) {
        if (status != Status.OPEN) {
            return Optional.empty();
        }
        AlertLevel reached;
        if (isOverdue(today, currentMileage)) {
            reached = AlertLevel.OVERDUE;
        } else if (isWithin(today, currentMileage, D7_DAYS, D7_KM)) {
            reached = AlertLevel.D7;
        } else if (isWithin(today, currentMileage, DUE_SOON_DAYS, DUE_SOON_KM)) {
            reached = AlertLevel.D30;
        } else {
            return Optional.empty();
        }
        if (lastAlert != null && lastAlert.compareTo(reached) >= 0) {
            return Optional.empty();
        }
        return Optional.of(reached);
    }

    public void markAlertSent(AlertLevel level) {
        this.lastAlert = level;
    }

    public void complete(LocalDate on, int mileage) {
        if (status != Status.OPEN) {
            throw new BusinessRuleException("DEADLINE_NOT_OPEN", "This deadline is already " + status);
        }
        this.status = Status.DONE;
        this.completedOn = on;
        this.completedMileage = mileage;
    }

    public void cancel() {
        if (status == Status.OPEN) {
            this.status = Status.CANCELLED;
        }
    }

    /** Days until the due date (negative when late), empty for mileage-only deadlines. */
    public Optional<Long> daysRemaining(LocalDate today) {
        return Optional.ofNullable(dueDate).map(due -> ChronoUnit.DAYS.between(today, due));
    }

    /** Kilometres until the due mileage (negative when passed), empty for date-only deadlines. */
    public Optional<Integer> kilometresRemaining(int currentMileage) {
        return Optional.ofNullable(dueMileage).map(due -> due - currentMileage);
    }

    private boolean isOverdue(LocalDate today, int currentMileage) {
        boolean dateReached = dueDate != null && dueDate.isBefore(today);
        boolean mileageReached = dueMileage != null && currentMileage >= dueMileage;
        return dateReached || mileageReached;
    }

    private boolean isWithin(LocalDate today, int currentMileage, int days, int kilometres) {
        boolean dateClose = dueDate != null && !dueDate.isAfter(today.plusDays(days));
        boolean mileageClose = dueMileage != null && dueMileage - currentMileage <= kilometres;
        return dateClose || mileageClose;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public DeadlineType getType() {
        return type;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Integer getDueMileage() {
        return dueMileage;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public Integer getCompletedMileage() {
        return completedMileage;
    }

    public AlertLevel getLastAlert() {
        return lastAlert;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
