package com.fleetpilot.telemetry.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.telemetry.application.TelemetryViews.LiveState;
import com.fleetpilot.telemetry.application.TelemetryViews.Summary;
import com.fleetpilot.telemetry.application.TelemetryViews.TelemetryReader;
import com.fleetpilot.telemetry.application.TelemetryViews.TrackPoint;
import com.fleetpilot.telemetry.application.TelemetryViews.UsageProfile;
import com.fleetpilot.telemetry.application.TelemetryViews.VehicleTelemetry;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the portal and the other services read of the telemetry, restricted to the companies the caller may see. */
@Service
@Transactional(readOnly = true)
public class TelemetryQueries {

    /** Usage analysed over the last 90 days. */
    static final int USAGE_DAYS = 90;

    private final TelemetryReader reader;
    private final AlertService alerts;
    private final TelemetryScope scope;
    private final Clock clock;

    public TelemetryQueries(TelemetryReader reader, AlertService alerts, TelemetryScope scope, Clock clock) {
        this.reader = reader;
        this.alerts = alerts;
        this.scope = scope;
        this.clock = clock;
    }

    public List<LiveState> live(UUID companyId) {
        return reader.live(scope.companyFilter(companyId));
    }

    public VehicleTelemetry vehicle(UUID vehicleId) {
        checkAccess(vehicleId);
        return new VehicleTelemetry(reader.state(vehicleId).orElse(null), reader.trips(vehicleId, 10),
                reader.faults(vehicleId, 10), reader.usage(vehicleId, LocalDate.now(clock).minusDays(30)),
                alerts.search(null, null, null, null, vehicleId, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC,
                        "raisedAt"))).getContent());
    }

    public List<TrackPoint> track(UUID vehicleId, int minutes) {
        checkAccess(vehicleId);
        return reader.track(vehicleId, clock.instant().minus(Duration.ofMinutes(Math.clamp(minutes, 5, 24 * 60))));
    }

    /** A vehicle never seen, or of another company, is "not found". */
    private void checkAccess(UUID vehicleId) {
        UUID company = reader.companyOf(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Connected vehicle", vehicleId));
        scope.checkAccess(company, "Connected vehicle", vehicleId);
    }

    public Summary summary(UUID companyId) {
        return reader.summary(scope.companyFilter(companyId), LocalDate.now(clock));
    }

    public List<UsageProfile> usageProfiles(UUID companyId) {
        return reader.usageProfiles(scope.companyFilter(companyId), LocalDate.now(clock).minusDays(USAGE_DAYS));
    }
}
