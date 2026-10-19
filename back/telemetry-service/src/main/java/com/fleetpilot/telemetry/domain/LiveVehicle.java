package com.fleetpilot.telemetry.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;

/**
 * What the telemetry service follows of one connected vehicle between two positions: the trip in progress and the
 * state of the alert rules. Pure business logic: it receives the positions in order and says what happened.
 *
 * <ul>
 *   <li>a trip starts when the ignition goes on, ends when it goes off: distance, speeds and idle time are measured;</li>
 *   <li>speeding: above the limit of the category plus 10 km/h, on three positions in a row (six seconds);</li>
 *   <li>long idle: engine running without moving for ten minutes;</li>
 *   <li>low battery: an electric vehicle under 15 % while driving.</li>
 * </ul>
 */
public final class LiveVehicle {

    static final int SPEEDING_STREAK = 3;
    static final int SPEED_TOLERANCE_KMH = 10;
    static final Duration LONG_IDLE = Duration.ofMinutes(10);
    static final int LOW_BATTERY_PERCENT = 15;
    /** A position is kept in the history every 30 seconds per vehicle (and at every start and stop). */
    static final Duration HISTORY_EVERY = Duration.ofSeconds(30);
    private static final double EARTH_RADIUS_KM = 6371;

    /** Category, energy and next service of a vehicle, from the fleet module. */
    public record VehicleInfo(String category, String energy, Integer serviceDueMileage) {

        /** Legal limit on the fastest roads for the category, in km/h. */
        public int speedLimit() {
            return switch (category == null ? "CAR" : category) {
                case "TRUCK", "MACHINE" -> 90;
                case "VAN" -> 110;
                default -> 130;
            };
        }
    }

    /** A rule fired. */
    public record AlertCandidate(Alert.Type type, Alert.Severity severity, String message, Double value) {
    }

    /** A finished trip. */
    public record TripRecord(UUID vehicleId, UUID companyId, Instant startedAt, Instant endedAt, double startLat,
            double startLon, double endLat, double endLon, double distanceKm, int maxSpeed, int averageSpeed,
            int idleMinutes) {

        public long drivingMinutes() {
            return Math.max(1, Duration.between(startedAt, endedAt).toMinutes() - idleMinutes);
        }
    }

    /** What one position caused. */
    public record Observation(boolean keepInHistory, TripRecord finishedTrip, List<AlertCandidate> alerts) {
    }

    private PositionReport last;
    private Instant lastStored;
    private Instant tripStart;
    private double tripStartLat;
    private double tripStartLon;
    private double tripKm;
    private int tripMaxSpeed;
    private long idleSeconds;
    private int speedingStreak;
    private Instant idleSince;
    private boolean idleAlerted;
    private boolean batteryAlerted;

    public Observation observe(PositionReport report, VehicleInfo info) {
        if (last != null && report.at().isBefore(last.at())) {
            // out of order (a retried message): too late to change the trip, kept as a position only
            return new Observation(false, null, List.of());
        }
        boolean ignitionChanged = last == null || last.ignition() != report.ignition();
        TripRecord finished = trip(report);
        List<AlertCandidate> alerts = new ArrayList<>();
        speeding(report, info).ifPresent(alerts::add);
        idle(report).ifPresent(alerts::add);
        battery(report).ifPresent(alerts::add);
        boolean keep = ignitionChanged || lastStored == null
                || Duration.between(lastStored, report.at()).compareTo(HISTORY_EVERY) >= 0;
        if (keep) {
            lastStored = report.at();
        }
        last = report;
        return new Observation(keep, finished, alerts);
    }

    private TripRecord trip(PositionReport report) {
        if (report.ignition()) {
            if (tripStart == null) {
                tripStart = report.at();
                tripStartLat = report.latitude();
                tripStartLon = report.longitude();
                tripKm = 0;
                tripMaxSpeed = 0;
                idleSeconds = 0;
            } else if (last != null && last.ignition()) {
                tripKm += distanceKm(last.latitude(), last.longitude(), report.latitude(), report.longitude());
                if (report.speedKmh() == 0) {
                    idleSeconds += Duration.between(last.at(), report.at()).toSeconds();
                }
            }
            tripMaxSpeed = Math.max(tripMaxSpeed, report.speedKmh());
            return null;
        }
        if (tripStart == null) {
            return null;
        }
        if (last != null) {
            tripKm += distanceKm(last.latitude(), last.longitude(), report.latitude(), report.longitude());
        }
        long minutes = Math.max(1, Duration.between(tripStart, report.at()).toMinutes());
        int idleMinutes = (int) (idleSeconds / 60);
        long drivingMinutes = Math.max(1, minutes - idleMinutes);
        TripRecord trip = new TripRecord(report.vehicleId(), report.companyId(), tripStart, report.at(), tripStartLat,
                tripStartLon, report.latitude(), report.longitude(), Math.round(tripKm * 10) / 10.0, tripMaxSpeed,
                (int) Math.round(tripKm / drivingMinutes * 60), idleMinutes);
        tripStart = null;
        return trip.distanceKm() >= 0.2 ? trip : null;
    }

    private Optional<AlertCandidate> speeding(PositionReport report, VehicleInfo info) {
        int threshold = info.speedLimit() + SPEED_TOLERANCE_KMH;
        if (!report.ignition() || report.speedKmh() <= threshold) {
            speedingStreak = 0;
            return Optional.empty();
        }
        speedingStreak++;
        if (speedingStreak != SPEEDING_STREAK) {
            return Optional.empty();
        }
        return Optional.of(new AlertCandidate(Alert.Type.SPEEDING, Alert.Severity.WARNING,
                "%d km/h pour une limite de %d km/h".formatted(report.speedKmh(), info.speedLimit()),
                (double) report.speedKmh()));
    }

    private Optional<AlertCandidate> idle(PositionReport report) {
        if (!report.ignition() || report.speedKmh() > 0) {
            idleSince = null;
            idleAlerted = false;
            return Optional.empty();
        }
        if (idleSince == null) {
            idleSince = report.at();
            return Optional.empty();
        }
        Duration idle = Duration.between(idleSince, report.at());
        if (idleAlerted || idle.compareTo(LONG_IDLE) < 0) {
            return Optional.empty();
        }
        idleAlerted = true;
        return Optional.of(new AlertCandidate(Alert.Type.LONG_IDLE, Alert.Severity.INFO,
                "Moteur tournant à l'arrêt depuis %d minutes".formatted(idle.toMinutes()), (double) idle.toMinutes()));
    }

    private Optional<AlertCandidate> battery(PositionReport report) {
        Integer percent = report.batteryPercent();
        if (percent == null) {
            return Optional.empty();
        }
        if (percent > LOW_BATTERY_PERCENT + 10) {
            batteryAlerted = false;
        }
        if (batteryAlerted || !report.ignition() || percent >= LOW_BATTERY_PERCENT) {
            return Optional.empty();
        }
        batteryAlerted = true;
        return Optional.of(new AlertCandidate(Alert.Type.LOW_BATTERY, Alert.Severity.WARNING,
                "Batterie à %d %% en roulant".formatted(percent), (double) percent));
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    /** Service due within 500 km, checked on every odometer reading. */
    public static Optional<AlertCandidate> serviceDue(int odometerKm, VehicleInfo info) {
        Integer due = info.serviceDueMileage();
        if (due == null || odometerKm < due - 500) {
            return Optional.empty();
        }
        int remaining = due - odometerKm;
        String message = remaining > 0 ? String.format(Locale.FRANCE, "Révision dans %,d km (à %,d km)", remaining, due)
                : String.format(Locale.FRANCE, "Révision dépassée de %,d km (prévue à %,d km)", -remaining, due);
        return Optional.of(new AlertCandidate(Alert.Type.SERVICE_DUE,
                remaining > 0 ? Alert.Severity.INFO : Alert.Severity.WARNING, message, (double) remaining));
    }
}
