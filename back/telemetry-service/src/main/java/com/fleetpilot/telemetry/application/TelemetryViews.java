package com.fleetpilot.telemetry.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.telemetry.domain.Alert;

/** Read models of the telemetry API. */
public final class TelemetryViews {

    private TelemetryViews() {
    }

    /** What a vehicle is doing now, derived from its last messages. */
    public enum Activity {
        /** Ignition on and moving. */
        MOVING,
        /** Ignition on, not moving. */
        IDLING,
        /** Ignition off. */
        PARKED,
        /** No message for ten minutes. */
        OFFLINE
    }

    public record LiveState(UUID vehicleId, UUID companyId, String registration, double latitude, double longitude,
            int speedKmh, int heading, boolean ignition, Integer batteryPercent, Integer odometerKm,
            Instant lastPositionAt, Activity activity, int openAlerts) {
    }

    public record TrackPoint(Instant at, double latitude, double longitude, int speedKmh, boolean ignition) {
    }

    public record TripView(UUID id, Instant startedAt, Instant endedAt, double startLat, double startLon, double endLat,
            double endLon, BigDecimal distanceKm, int maxSpeed, int averageSpeed, int idleMinutes) {
    }

    public record FaultView(UUID id, Instant at, String code, String description, String severity) {
    }

    public record UsageDay(LocalDate day, BigDecimal distanceKm, int trips, BigDecimal longestTripKm, int drivingMinutes,
            int maxSpeed) {
    }

    /** One day of usage of one vehicle, for the analytics pipeline. */
    public record VehicleUsageDay(UUID vehicleId, UUID companyId, LocalDate day, BigDecimal distanceKm, int trips,
            BigDecimal longestTripKm, int drivingMinutes, int maxSpeed) {
    }

    /** The telemetry tab of a vehicle file. */
    public record VehicleTelemetry(LiveState state, List<TripView> trips, List<FaultView> faults, List<UsageDay> usage,
            List<AlertView> alerts) {
    }

    public record Summary(int connected, int moving, int idling, int parked, int offline, BigDecimal kilometresToday,
            int openAlerts, Map<Alert.Type, Integer> openAlertsByType) {
    }

    /**
     * How a vehicle is used over the period observed, the basis of the energy transition study.
     *
     * @param activeDays        days with at least one trip
     * @param averageDailyKm    average distance of the active days
     * @param p95DailyKm        95 % of the active days are shorter than this
     * @param daysOver150Km     active days longer than 150 km
     */
    public record UsageProfile(UUID vehicleId, UUID companyId, int observedDays, int activeDays, BigDecimal totalKm,
            BigDecimal averageDailyKm, BigDecimal p95DailyKm, BigDecimal maxDailyKm, BigDecimal longestTripKm,
            int daysOver150Km) {
    }

    public record AlertView(UUID id, UUID vehicleId, UUID companyId, String registration, Alert.Type type,
            Alert.Severity severity, String message, BigDecimal value, BigDecimal latitude, BigDecimal longitude,
            Alert.Status status, Instant raisedAt, Instant lastSeenAt, int occurrences, String handledBy,
            Instant acknowledgedAt, Instant resolvedAt, String comment) {

        public static AlertView of(Alert a) {
            return new AlertView(a.getId(), a.getVehicleId(), a.getCompanyId(), a.getRegistration(), a.getType(),
                    a.getSeverity(), a.getMessage(), a.getValue(), a.getLatitude(), a.getLongitude(), a.getStatus(),
                    a.getRaisedAt(), a.getLastSeenAt(), a.getOccurrences(), a.getHandledBy(), a.getAcknowledgedAt(),
                    a.getResolvedAt(), a.getComment());
        }
    }

    /** Reads of the telemetry, in SQL. */
    public interface TelemetryReader {

        List<LiveState> live(UUID companyId);

        Optional<LiveState> state(UUID vehicleId);

        /** Company of a vehicle known by its live state or its usage history. */
        Optional<UUID> companyOf(UUID vehicleId);

        List<TrackPoint> track(UUID vehicleId, Instant since);

        List<TripView> trips(UUID vehicleId, int limit);

        List<FaultView> faults(UUID vehicleId, int limit);

        List<UsageDay> usage(UUID vehicleId, LocalDate from);

        /** Daily usage of every vehicle since a day, a page at a time (ordered on a unique key). */
        List<VehicleUsageDay> usageDays(LocalDate from, int page, int size);

        long countUsageDays(LocalDate from);

        Summary summary(UUID companyId, LocalDate today);

        List<UsageProfile> usageProfiles(UUID companyId, LocalDate from);
    }
}
