package com.fleetpilot.reporting.unit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.reporting.application.Sources.Contract;
import com.fleetpilot.reporting.application.Sources.Deadline;
import com.fleetpilot.reporting.application.Sources.Driver;
import com.fleetpilot.reporting.application.Sources.FleetSource;
import com.fleetpilot.reporting.application.Sources.GarageRef;
import com.fleetpilot.reporting.application.Sources.Incident;
import com.fleetpilot.reporting.application.Sources.Labelled;
import com.fleetpilot.reporting.application.Sources.Named;
import com.fleetpilot.reporting.application.Sources.Projection;
import com.fleetpilot.reporting.application.Sources.Quote;
import com.fleetpilot.reporting.application.Sources.QuoteSource;
import com.fleetpilot.reporting.application.Sources.QuoteVehicle;
import com.fleetpilot.reporting.application.Sources.Vehicle;
import com.fleetpilot.reporting.application.Sources.VehicleRef;

/**
 * A small fleet for the tests: three cars and a van, one of them at the garage, one disposed; leases, quotes, claims
 * and deadlines around 14 October 2026.
 */
public final class FakeSources implements FleetSource, QuoteSource {

    public static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");
    public static final UUID COMPANY = UUID.nameUUIDFromBytes("neolia".getBytes());

    public static final Vehicle CLIO = vehicle("GH-482-KM", "Renault", "Clio", "CAR", "PETROL", "ACTIVE");
    public static final Vehicle ZOE = vehicle("GH-483-KM", "Renault", "Zoe", "CAR", "ELECTRIC", "IN_REPAIR");
    public static final Vehicle TRAFIC = vehicle("GH-484-KM", "Renault", "Trafic", "VAN", "DIESEL", "ACTIVE");
    public static final Vehicle OLD = vehicle("AA-001-AA", "Peugeot", "206", "CAR", "DIESEL", "DISPOSED");

    static Vehicle vehicle(String plate, String make, String model, String category, String energy, String status) {
        return new Vehicle(UUID.nameUUIDFromBytes(plate.getBytes()), COMPANY, plate, category, make, model, energy, 1,
                42_000, status, new Named(UUID.randomUUID(), "Marseille - Arenc"), new Labelled("DSI", "Direction SI"),
                new Driver(UUID.randomUUID(), "Camille Martin"));
    }

    static VehicleRef ref(Vehicle v) {
        return new VehicleRef(v.id(), v.registration(), v.make(), v.model());
    }

    @Override
    public String companyName(UUID companyId) {
        return "Néolia Énergies";
    }

    @Override
    public List<Vehicle> vehicles(UUID companyId) {
        return List.of(CLIO, ZOE, TRAFIC, OLD);
    }

    /** Clio: 400 EUR a month, 40 km a day; Zoe: 350 EUR, no projection yet. The van is owned. */
    @Override
    public List<Contract> activeContracts(UUID companyId) {
        return List.of(
                new Contract(UUID.randomUUID(), ref(CLIO), "Arval", "ARV-1", LocalDate.of(2025, 1, 1),
                        LocalDate.of(2028, 1, 1), 90_000, new BigDecimal("400.00"), "ACTIVE",
                        new Projection(60_000, 0, 40.0, false)),
                new Contract(UUID.randomUUID(), ref(ZOE), "Ayvens", "AYV-2", LocalDate.of(2026, 9, 1),
                        LocalDate.of(2029, 9, 1), 60_000, new BigDecimal("350.00"), "ACTIVE", null));
    }

    /** A claim of 1,200 EUR on the Clio this year, and an old one (two years ago) that does not count. */
    @Override
    public List<Incident> incidents(UUID companyId) {
        return List.of(
                new Incident(UUID.randomUUID(), "SIN-1", ref(CLIO), "COLLISION", NOW.minusSeconds(86_400L * 30),
                        new BigDecimal("1200.00"), "CLOSED"),
                new Incident(UUID.randomUUID(), "SIN-0", ref(CLIO), "THEFT", NOW.minusSeconds(86_400L * 800),
                        new BigDecimal("9000.00"), "CLOSED"));
    }

    @Override
    public List<Deadline> urgentDeadlines(UUID companyId) {
        return List.of(new Deadline(UUID.randomUUID(), ref(CLIO), "TECHNICAL_INSPECTION", LocalDate.of(2026, 10, 1), null,
                "OVERDUE", -13L, null), new Deadline(UUID.randomUUID(), ref(TRAFIC), "SERVICE", LocalDate.of(2026, 11, 2),
                40_000, "DUE_SOON", 19L, 1_200));
    }

    /** Clio: 300 EUR accepted; van: 500 EUR accepted; a rejected quote and an open one that do not count. */
    @Override
    public List<Quote> quotes(UUID companyId, Instant from, Instant to) {
        List<Quote> quotes = new ArrayList<>();
        quotes.add(quote("DEV-1", CLIO, "INVOICED", "320.00", "300.00", NOW.minusSeconds(86_400L * 60)));
        quotes.add(quote("DEV-2", TRAFIC, "ACCEPTED", "500.00", "500.00", NOW.minusSeconds(86_400L * 10)));
        quotes.add(quote("DEV-3", TRAFIC, "REJECTED", "900.00", null, NOW.minusSeconds(86_400L * 5)));
        quotes.add(quote("DEV-4", ZOE, "TO_NEGOTIATE", "250.00", null, NOW.minusSeconds(86_400L)));
        return quotes;
    }

    private static Quote quote(String reference, Vehicle vehicle, String status, String amount, String accepted,
            Instant at) {
        BigDecimal acceptedAmount = accepted == null ? null : new BigDecimal(accepted);
        return new Quote(UUID.randomUUID(), reference, status, new GarageRef(UUID.randomUUID(), "Garage du Prado",
                "Marseille"), vehicle.registration(), new QuoteVehicle(vehicle.id()), at, new BigDecimal(amount),
                new BigDecimal(amount), BigDecimal.ZERO, acceptedAmount,
                acceptedAmount == null ? null : new BigDecimal(amount).subtract(acceptedAmount), 0, false);
    }

    @Override
    public Map<String, Object> savings(UUID companyId) {
        return Map.of("quotes", 750, "openQuotes", 28, "amountQuoted", 346_586.72, "savings", 29_504.95,
                "savingsRate", 8.5, "pendingOvercharge", 1_635.74, "autoApprovalRate", 27.0,
                "months", List.of(Map.of("month", "2026-09", "quotes", 60, "quoted", 30_000.0, "accepted", 27_000.0,
                        "savings", 3_000.0), Map.of("month", "2026-10", "quotes", 40, "quoted", 20_000.0,
                        "accepted", 19_000.0, "savings", 1_000.0)),
                "findings", List.of(Map.of("finding", "OVERPRICED_PART", "lines", 461, "overcharge", 16_609.0)),
                "garagesToWatch", List.of(Map.of("name", "Peugeot Paris Grenelle", "city", "Paris", "quotes", 13,
                        "overchargeRate", 37.0, "savings", 1_993.0)));
    }
}
