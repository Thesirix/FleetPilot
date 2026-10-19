package com.fleetpilot.telemetry.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.telemetry.application.TelemetryViews.Activity;
import com.fleetpilot.telemetry.application.TelemetryViews.FaultView;
import com.fleetpilot.telemetry.application.TelemetryViews.LiveState;
import com.fleetpilot.telemetry.application.TelemetryViews.Summary;
import com.fleetpilot.telemetry.application.TelemetryViews.TelemetryReader;
import com.fleetpilot.telemetry.application.TelemetryViews.TrackPoint;
import com.fleetpilot.telemetry.application.TelemetryViews.TripView;
import com.fleetpilot.telemetry.application.TelemetryViews.UsageDay;
import com.fleetpilot.telemetry.application.TelemetryViews.UsageProfile;
import com.fleetpilot.telemetry.application.TelemetryViews.VehicleUsageDay;
import com.fleetpilot.telemetry.domain.Alert;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The telemetry read in SQL: live states with their open alerts, tracks, trips, usage and its statistics. */
@Component
public class JdbcTelemetryReader implements TelemetryReader {

    /** A vehicle silent for longer is offline. */
    static final Duration OFFLINE_AFTER = Duration.ofMinutes(10);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static final String STATE = """
            select s.*, (select count(*) from alert a where a.vehicle_id = s.vehicle_id and a.status <> 'RESOLVED')
                as open_alerts
            from vehicle_state s""";

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcTelemetryReader(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public List<LiveState> live(UUID companyId) {
        return jdbc.sql(STATE + " where (cast(:company as uuid) is null or s.company_id = :company) order by s.registration")
                .param("company", companyId).query((rs, row) -> state(rs)).list();
    }

    @Override
    public Optional<LiveState> state(UUID vehicleId) {
        return jdbc.sql(STATE + " where s.vehicle_id = :id").param("id", vehicleId).query((rs, row) -> state(rs))
                .optional();
    }

    @Override
    public Optional<UUID> companyOf(UUID vehicleId) {
        return jdbc.sql("""
                select company_id from vehicle_state where vehicle_id = :id
                union all
                select company_id from (select company_id from daily_usage where vehicle_id = :id limit 1) usage
                limit 1""")
                .param("id", vehicleId).query(UUID.class).optional();
    }

    @Override
    public List<TrackPoint> track(UUID vehicleId, Instant since) {
        return jdbc.sql("""
                select at, latitude, longitude, speed_kmh, ignition from position_history
                where vehicle_id = :id and at >= :since order by at""")
                .param("id", vehicleId).param("since", Timestamp.from(since))
                .query((rs, row) -> new TrackPoint(rs.getTimestamp("at").toInstant(), rs.getDouble("latitude"),
                        rs.getDouble("longitude"), rs.getInt("speed_kmh"), rs.getBoolean("ignition")))
                .list();
    }

    @Override
    public List<TripView> trips(UUID vehicleId, int limit) {
        return jdbc.sql("select * from trip where vehicle_id = :id order by started_at desc limit :limit")
                .param("id", vehicleId).param("limit", limit)
                .query((rs, row) -> new TripView(rs.getObject("id", UUID.class), rs.getTimestamp("started_at").toInstant(),
                        rs.getTimestamp("ended_at").toInstant(), rs.getDouble("start_lat"), rs.getDouble("start_lon"),
                        rs.getDouble("end_lat"), rs.getDouble("end_lon"), rs.getBigDecimal("distance_km"),
                        rs.getInt("max_speed"), rs.getInt("average_speed"), rs.getInt("idle_minutes")))
                .list();
    }

    @Override
    public List<FaultView> faults(UUID vehicleId, int limit) {
        return jdbc.sql("select * from fault where vehicle_id = :id order by at desc limit :limit")
                .param("id", vehicleId).param("limit", limit)
                .query((rs, row) -> new FaultView(rs.getObject("id", UUID.class), rs.getTimestamp("at").toInstant(),
                        rs.getString("code"), rs.getString("description"), rs.getString("severity")))
                .list();
    }

    @Override
    public List<UsageDay> usage(UUID vehicleId, LocalDate from) {
        return jdbc.sql("select * from daily_usage where vehicle_id = :id and day >= :from order by day")
                .param("id", vehicleId).param("from", Date.valueOf(from))
                .query((rs, row) -> new UsageDay(rs.getDate("day").toLocalDate(), rs.getBigDecimal("distance_km"),
                        rs.getInt("trips"), rs.getBigDecimal("longest_trip_km"), rs.getInt("driving_minutes"),
                        rs.getInt("max_speed")))
                .list();
    }

    @Override
    public List<VehicleUsageDay> usageDays(LocalDate from, int page, int size) {
        return jdbc.sql("""
                select * from daily_usage where day >= :from order by day, vehicle_id limit :limit offset :offset""")
                .param("from", Date.valueOf(from)).param("limit", size).param("offset", (long) page * size)
                .query((rs, row) -> new VehicleUsageDay(rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("company_id", UUID.class), rs.getDate("day").toLocalDate(),
                        rs.getBigDecimal("distance_km"), rs.getInt("trips"), rs.getBigDecimal("longest_trip_km"),
                        rs.getInt("driving_minutes"), rs.getInt("max_speed")))
                .list();
    }

    @Override
    public long countUsageDays(LocalDate from) {
        return jdbc.sql("select count(*) from daily_usage where day >= :from").param("from", Date.valueOf(from))
                .query(Long.class).single();
    }

    @Override
    public Summary summary(UUID companyId, LocalDate today) {
        List<LiveState> states = live(companyId);
        Map<Activity, Long> counts = new EnumMap<>(Activity.class);
        states.forEach(s -> counts.merge(s.activity(), 1L, Long::sum));
        BigDecimal km = jdbc.sql("""
                select coalesce(sum(distance_km), 0) from daily_usage
                where day = :today and (cast(:company as uuid) is null or company_id = :company)""")
                .param("today", Date.valueOf(today)).param("company", companyId).query(BigDecimal.class).single();
        Map<Alert.Type, Integer> byType = new EnumMap<>(Alert.Type.class);
        jdbc.sql("""
                select type, count(*) as n from alert
                where status <> 'RESOLVED' and (cast(:company as uuid) is null or company_id = :company) group by type""")
                .param("company", companyId)
                .query((rs, row) -> byType.put(Alert.Type.valueOf(rs.getString("type")), rs.getInt("n"))).list();
        return new Summary(states.size(), count(counts, Activity.MOVING), count(counts, Activity.IDLING),
                count(counts, Activity.PARKED), count(counts, Activity.OFFLINE), km,
                byType.values().stream().mapToInt(Integer::intValue).sum(), byType);
    }

    @Override
    public List<UsageProfile> usageProfiles(UUID companyId, LocalDate from) {
        int observed = (int) ChronoUnit.DAYS.between(from, LocalDate.now(clock.withZone(PARIS)));
        return jdbc.sql("""
                select vehicle_id, company_id, count(*) as active_days, sum(distance_km) as total_km,
                       avg(distance_km) as average_km,
                       percentile_cont(0.95) within group (order by distance_km) as p95_km,
                       max(distance_km) as max_km, max(longest_trip_km) as longest_trip,
                       count(*) filter (where distance_km > 150) as long_days
                from daily_usage
                where day >= :from and distance_km > 0 and (cast(:company as uuid) is null or company_id = :company)
                group by vehicle_id, company_id""")
                .param("from", Date.valueOf(from)).param("company", companyId)
                .query((rs, row) -> new UsageProfile(rs.getObject("vehicle_id", UUID.class),
                        rs.getObject("company_id", UUID.class), observed, rs.getInt("active_days"),
                        scale(rs, "total_km"), scale(rs, "average_km"), scale(rs, "p95_km"), scale(rs, "max_km"),
                        scale(rs, "longest_trip"), rs.getInt("long_days")))
                .list();
    }

    private LiveState state(ResultSet rs) throws SQLException {
        Instant last = rs.getTimestamp("last_position_at").toInstant();
        boolean ignition = rs.getBoolean("ignition");
        int speed = rs.getInt("speed_kmh");
        Activity activity = Duration.between(last, clock.instant()).compareTo(OFFLINE_AFTER) > 0 ? Activity.OFFLINE
                : !ignition ? Activity.PARKED : speed > 3 ? Activity.MOVING : Activity.IDLING;
        Integer battery = (Integer) rs.getObject("battery_percent");
        Integer odometer = (Integer) rs.getObject("odometer_km");
        return new LiveState(rs.getObject("vehicle_id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getString("registration"), rs.getDouble("latitude"), rs.getDouble("longitude"), speed,
                rs.getInt("heading"), ignition, battery, odometer, last, activity, rs.getInt("open_alerts"));
    }

    private static int count(Map<Activity, Long> counts, Activity activity) {
        return counts.getOrDefault(activity, 0L).intValue();
    }

    private static BigDecimal scale(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP);
    }
}
