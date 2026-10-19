package com.fleetpilot.telemetry.infrastructure.persistence;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.telemetry.application.TelemetryStore;
import com.fleetpilot.telemetry.domain.LiveVehicle.TripRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The telemetry tables written in batches with plain JDBC (one round trip per batch, no entity per row). */
@Component
public class JdbcTelemetryStore implements TelemetryStore {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final JdbcTemplate jdbc;

    public JdbcTelemetryStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveStates(Collection<PositionReport> latest) {
        jdbc.batchUpdate("""
                insert into vehicle_state (vehicle_id, company_id, registration, latitude, longitude, speed_kmh, heading,
                    ignition, battery_percent, last_position_at, last_moving_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (vehicle_id) do update set latitude = excluded.latitude, longitude = excluded.longitude,
                    speed_kmh = excluded.speed_kmh, heading = excluded.heading, ignition = excluded.ignition,
                    battery_percent = coalesce(excluded.battery_percent, vehicle_state.battery_percent),
                    registration = excluded.registration, last_position_at = excluded.last_position_at,
                    last_moving_at = coalesce(excluded.last_moving_at, vehicle_state.last_moving_at)
                where vehicle_state.last_position_at <= excluded.last_position_at""",
                latest.stream().map(p -> new Object[] {p.vehicleId(), p.companyId(), p.registration(), p.latitude(),
                    p.longitude(), p.speedKmh(), p.heading(), p.ignition(), p.batteryPercent(), Timestamp.from(p.at()),
                    p.speedKmh() > 0 ? Timestamp.from(p.at()) : null}).toList());
    }

    @Override
    public void savePositions(List<PositionReport> positions) {
        jdbc.batchUpdate("""
                insert into position_history (vehicle_id, at, latitude, longitude, speed_kmh, ignition)
                values (?, ?, ?, ?, ?, ?) on conflict do nothing""",
                positions.stream().map(p -> new Object[] {p.vehicleId(), Timestamp.from(p.at()), p.latitude(),
                    p.longitude(), p.speedKmh(), p.ignition()}).toList());
    }

    @Override
    public void saveTrips(List<TripRecord> trips) {
        jdbc.batchUpdate("""
                insert into trip (id, vehicle_id, company_id, started_at, ended_at, start_lat, start_lon, end_lat, end_lon,
                    distance_km, max_speed, average_speed, idle_minutes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                trips.stream().map(t -> new Object[] {UUID.randomUUID(), t.vehicleId(), t.companyId(),
                    Timestamp.from(t.startedAt()), Timestamp.from(t.endedAt()), t.startLat(), t.startLon(), t.endLat(),
                    t.endLon(), t.distanceKm(), t.maxSpeed(), t.averageSpeed(), t.idleMinutes()}).toList());
        jdbc.batchUpdate("""
                insert into daily_usage (vehicle_id, company_id, day, distance_km, trips, longest_trip_km, driving_minutes,
                    max_speed) values (?, ?, ?, ?, 1, ?, ?, ?)
                on conflict (vehicle_id, day) do update set distance_km = daily_usage.distance_km + excluded.distance_km,
                    trips = daily_usage.trips + 1,
                    longest_trip_km = greatest(daily_usage.longest_trip_km, excluded.longest_trip_km),
                    driving_minutes = daily_usage.driving_minutes + excluded.driving_minutes,
                    max_speed = greatest(daily_usage.max_speed, excluded.max_speed)""",
                trips.stream().map(t -> new Object[] {t.vehicleId(), t.companyId(), Date.valueOf(day(t.startedAt())),
                    t.distanceKm(), t.distanceKm(), t.drivingMinutes(), t.maxSpeed()}).toList());
    }

    @Override
    public void saveOdometers(Collection<MileageReport> latest) {
        jdbc.batchUpdate("update vehicle_state set odometer_km = greatest(coalesce(odometer_km, 0), ?) where vehicle_id = ?",
                latest.stream().map(m -> new Object[] {m.odometerKm(), m.vehicleId()}).toList());
    }

    @Override
    public void saveFault(FaultReport fault) {
        jdbc.update("""
                insert into fault (id, vehicle_id, company_id, registration, at, code, description, severity)
                values (?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), fault.vehicleId(), fault.companyId(),
                fault.registration(), Timestamp.from(fault.at()), fault.code(), fault.description(),
                fault.severity().name());
    }

    @Override
    public int purgePositions(Instant before) {
        return jdbc.update("delete from position_history where at < ?", Timestamp.from(before));
    }

    private static LocalDate day(Instant at) {
        return LocalDate.ofInstant(at, PARIS);
    }
}
