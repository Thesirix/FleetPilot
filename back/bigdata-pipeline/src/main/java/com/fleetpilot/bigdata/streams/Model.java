package com.fleetpilot.bigdata.streams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Messages read, aggregates kept and documents written by the analytics topology. */
public final class Model {

    private Model() {
    }

    // ------------------------------------------------------------------------------------------------ reference

    /** A vehicle of the fleet, published on the compacted topic {@code reference.vehicles}. */
    public record VehicleRef(UUID vehicleId, UUID companyId, String companyName, String registration, String category,
            String energy, String make, String model) {
    }

    // ------------------------------------------------------------------------------------------------ inputs

    /** An alert raised by the telemetry service, inside the event envelope of {@code telemetry.alerts}. */
    public record AlertEvent(UUID eventId, String type, Instant occurredAt, UUID companyId, AlertPayload payload) {
    }

    public record AlertPayload(UUID alertId, UUID vehicleId, String registration, String type, String severity,
            String message, Double latitude, Double longitude, Instant raisedAt) {
    }

    /** A fuel card transaction, as the integration bus publishes it on {@code fuel.transactions}. */
    public record FuelTransaction(UUID id, String provider, String reference, Instant occurredAt, String station,
            String city, String product, BigDecimal litres, BigDecimal unitPrice, BigDecimal amount, Integer odometer,
            String registration, UUID vehicleId, UUID companyId, String energy, String anomalies) {
    }

    // ------------------------------------------------------------------------------------------------ aggregates

    /** What happened to a vehicle during one day (Paris time), built from its odometer readings and positions. */
    public static final class VehicleDay {
        public UUID vehicleId;
        public UUID companyId;
        public LocalDate day;
        public Integer firstOdometer;
        public Integer lastOdometer;
        public int maxSpeed;
        public long movingSeconds;
        public long positions;
        public Instant lastAt;

        public VehicleDay odometer(UUID vehicle, UUID company, LocalDate date, int odometer, Instant at) {
            init(vehicle, company, date);
            firstOdometer = firstOdometer == null ? odometer : Math.min(firstOdometer, odometer);
            lastOdometer = lastOdometer == null ? odometer : Math.max(lastOdometer, odometer);
            lastAt = lastAt == null || at.isAfter(lastAt) ? at : lastAt;
            return this;
        }

        /** A position while moving counts for the interval of the box (two seconds). */
        public VehicleDay position(UUID vehicle, UUID company, LocalDate date, int speed, boolean ignition, Instant at,
                long intervalSeconds) {
            init(vehicle, company, date);
            positions++;
            maxSpeed = Math.max(maxSpeed, speed);
            if (ignition && speed > 3) {
                movingSeconds += intervalSeconds;
            }
            lastAt = lastAt == null || at.isAfter(lastAt) ? at : lastAt;
            return this;
        }

        /** Merges what another message taught about the same vehicle and day. */
        public VehicleDay merge(VehicleDay other) {
            init(other.vehicleId, other.companyId, other.day);
            if (other.firstOdometer != null) {
                firstOdometer = firstOdometer == null ? other.firstOdometer : Math.min(firstOdometer, other.firstOdometer);
                lastOdometer = lastOdometer == null ? other.lastOdometer : Math.max(lastOdometer, other.lastOdometer);
            }
            maxSpeed = Math.max(maxSpeed, other.maxSpeed);
            movingSeconds += other.movingSeconds;
            positions += other.positions;
            lastAt = lastAt == null || (other.lastAt != null && other.lastAt.isAfter(lastAt)) ? other.lastAt : lastAt;
            return this;
        }

        private void init(UUID vehicle, UUID company, LocalDate date) {
            vehicleId = vehicle;
            companyId = company;
            day = date;
        }

        public int distanceKm() {
            return firstOdometer == null ? 0 : lastOdometer - firstOdometer;
        }
    }

    /** Activity of a company's fleet during a window of five minutes. */
    public static final class ActivityWindow {
        public UUID companyId;
        public String companyName;
        public Set<String> connected = new HashSet<>();
        public Set<String> moving = new HashSet<>();
        public Set<String> idling = new HashSet<>();
        public long speedSum;
        public long movingReports;

        public ActivityWindow add(EnrichedPosition p) {
            companyId = p.companyId();
            companyName = p.companyName();
            String id = p.vehicleId().toString();
            connected.add(id);
            if (p.ignition() && p.speedKmh() > 3) {
                moving.add(id);
                speedSum += p.speedKmh();
                movingReports++;
            } else if (p.ignition()) {
                idling.add(id);
            }
            return this;
        }

        public int averageSpeed() {
            return movingReports == 0 ? 0 : (int) (speedSum / movingReports);
        }
    }

    /** The last fill of a vehicle, to compute the consumption of the next one. */
    public static final class FuelState {
        public Integer lastOdometer;
        public Instant lastAt;
        /** The document of the transaction just added. */
        public FuelDoc current;
    }

    // ------------------------------------------------------------------------------------------------ documents

    /** A position kept for the maps of Kibana (one per vehicle and minute). */
    public record EnrichedPosition(UUID vehicleId, UUID companyId, String companyName, String registration,
            String category, String energy, Instant at, GeoPoint location, int speedKmh, boolean ignition,
            Integer batteryPercent) {
    }

    public record GeoPoint(double lat, double lon) {
    }

    public record ActivityDoc(UUID companyId, String companyName, Instant windowStart, Instant windowEnd,
            int connectedVehicles, int movingVehicles, int idlingVehicles, int averageSpeed) {
    }

    public record VehicleDayDoc(UUID vehicleId, UUID companyId, String companyName, String registration,
            String category, String energy, String makeModel, LocalDate day, int distanceKm, int drivingMinutes,
            int maxSpeed, String source) {
    }

    public record FaultDoc(UUID vehicleId, UUID companyId, String companyName, String registration, String category,
            String energy, String makeModel, Instant at, String code, String description, String severity) {
    }

    public record AlertDoc(UUID alertId, UUID vehicleId, UUID companyId, String companyName, String registration,
            String category, String type, String severity, String message, Instant raisedAt, GeoPoint location) {
    }

    public record FuelDoc(UUID transactionId, String provider, UUID vehicleId, UUID companyId, String companyName,
            String registration, String category, String energy, Instant at, String station, String city,
            String product, BigDecimal litres, BigDecimal amount, BigDecimal unitPrice, Integer odometer,
            Integer distanceSincePreviousKm, BigDecimal consumptionL100, List<String> anomalies) {
    }

    static BigDecimal consumption(BigDecimal litres, int km) {
        return litres.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(km), 1, RoundingMode.HALF_UP);
    }

    static String makeModel(VehicleRef ref) {
        return ref == null || ref.make() == null ? null : (ref.make() + " " + ref.model()).trim();
    }
}
