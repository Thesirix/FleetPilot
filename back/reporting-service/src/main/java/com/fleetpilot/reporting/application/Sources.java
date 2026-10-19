package com.fleetpilot.reporting.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The data the reports are made of, read from the fleet and quote services with the identity of the reporting
 * service. Only the fields the reports use are declared: the services may add others freely.
 */
public final class Sources {

    private Sources() {
    }

    public record Named(UUID id, String name) {
    }

    public record Labelled(String code, String label) {
    }

    public record VehicleRef(UUID id, String registration, String make, String model) {
    }

    public record Driver(UUID id, String fullName) {
    }

    public record Vehicle(UUID id, UUID companyId, String registration, String category, String make, String model,
            String energy, Integer critAir, Integer mileage, String status, Named site, Labelled costCenter,
            Driver currentDriver) {
    }

    public record Projection(Integer projectedMileage, Integer projectedOverrunKm, Double dailyAverageKm,
            Boolean overrunRisk) {
    }

    public record Contract(UUID id, VehicleRef vehicle, String lessor, String contractNumber, LocalDate startDate,
            LocalDate endDate, Integer contractualMileage, BigDecimal monthlyRent, String status,
            Projection projection) {
    }

    public record Incident(UUID id, String reference, VehicleRef vehicle, String type, Instant occurredAt,
            BigDecimal estimatedCost, String status) {
    }

    public record Deadline(UUID id, VehicleRef vehicle, String type, LocalDate dueDate, Integer dueMileage,
            String urgency, Long daysRemaining, Integer kilometresRemaining) {
    }

    public record GarageRef(UUID id, String name, String city) {
    }

    public record QuoteVehicle(UUID id) {
    }

    public record Quote(UUID id, String reference, String status, GarageRef garage, String registration,
            QuoteVehicle vehicle, Instant submittedAt, BigDecimal amount, BigDecimal referenceAmount,
            BigDecimal overcharge, BigDecimal acceptedAmount, BigDecimal savings, Integer findingCount,
            Boolean autoApproved) {
    }

    /** The fleet module (vehicle-service). */
    public interface FleetSource {

        String companyName(UUID companyId);

        List<Vehicle> vehicles(UUID companyId);

        List<Contract> activeContracts(UUID companyId);

        List<Incident> incidents(UUID companyId);

        /** Deadlines overdue or due within 30 days. */
        List<Deadline> urgentDeadlines(UUID companyId);
    }

    /** The savings module (quote-service). */
    public interface QuoteSource {

        List<Quote> quotes(UUID companyId, Instant from, Instant to);

        /** The savings indicators, as returned by the quote service. */
        Map<String, Object> savings(UUID companyId);
    }
}
