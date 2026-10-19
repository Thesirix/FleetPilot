package com.fleetpilot.telemetry.api.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.telemetry.application.AlertService;
import com.fleetpilot.telemetry.application.TelemetryQueries;
import com.fleetpilot.telemetry.application.TelemetryScope;
import com.fleetpilot.telemetry.application.TelemetryViews.AlertView;
import com.fleetpilot.telemetry.application.TelemetryViews.LiveState;
import com.fleetpilot.telemetry.application.TelemetryViews.Summary;
import com.fleetpilot.telemetry.application.TelemetryViews.TrackPoint;
import com.fleetpilot.telemetry.application.TelemetryViews.TelemetryReader;
import com.fleetpilot.telemetry.application.TelemetryViews.UsageProfile;
import com.fleetpilot.telemetry.application.TelemetryViews.VehicleTelemetry;
import com.fleetpilot.telemetry.application.TelemetryViews.VehicleUsageDay;
import com.fleetpilot.telemetry.domain.Alert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Telemetry", description = "Connected vehicles: live positions, trips, usage, alerts")
public class TelemetryController {

    private final TelemetryQueries queries;
    private final AlertService alerts;
    private final LiveStream stream;
    private final TelemetryScope scope;
    private final TelemetryReader reader;

    public TelemetryController(TelemetryQueries queries, AlertService alerts, LiveStream stream, TelemetryScope scope,
            TelemetryReader reader) {
        this.queries = queries;
        this.alerts = alerts;
        this.stream = stream;
        this.scope = scope;
        this.reader = reader;
    }

    public record Resolution(@Size(max = 500) String comment) {
    }

    @GetMapping("/telemetry/live")
    @Operation(summary = "Where the connected vehicles are now", description = "Position, speed, activity (moving, "
            + "idling, parked, offline), battery and open alerts of each vehicle")
    public List<LiveState> live(@RequestParam(required = false) UUID companyId) {
        return queries.live(companyId);
    }

    @GetMapping(path = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live positions, pushed every two seconds", description = "Server-Sent Events: an event "
            + "\"states\" carrying the same list as /telemetry/live")
    public SseEmitter stream(@RequestParam(required = false) UUID companyId) {
        return stream.subscribe(scope.companyFilter(companyId));
    }

    @GetMapping("/telemetry/summary")
    @Operation(summary = "Connected vehicles by activity, kilometres of the day, open alerts by type")
    public Summary summary(@RequestParam(required = false) UUID companyId) {
        return queries.summary(companyId);
    }

    @GetMapping("/telemetry/vehicles/{id}")
    @Operation(summary = "Telemetry file of a vehicle", description = "Live state, last trips, trouble codes, usage "
            + "of the last 30 days and alerts")
    public VehicleTelemetry vehicle(@PathVariable UUID id) {
        return queries.vehicle(id);
    }

    @GetMapping("/telemetry/vehicles/{id}/track")
    @Operation(summary = "Positions of the last minutes (one every 30 seconds)")
    public List<TrackPoint> track(@PathVariable UUID id, @RequestParam(defaultValue = "60") int minutes) {
        return queries.track(id, minutes);
    }

    @GetMapping("/telemetry/usage-profiles")
    @Operation(summary = "How each vehicle is used over 90 days", description = "Active days, average and 95th "
            + "percentile of the daily distance, longest trip: the basis of the energy transition study")
    public List<UsageProfile> usageProfiles(@RequestParam(required = false) UUID companyId) {
        return queries.usageProfiles(companyId);
    }

    @GetMapping("/telemetry/usage-days")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Daily usage of every vehicle", description = "A page at a time, for the analytics pipeline "
            + "that loads the history into Elasticsearch")
    public PageResponse<VehicleUsageDay> usageDays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "1000") int size) {
        int pageSize = Math.clamp(size, 1, 5000);
        return PageResponse.of(new PageImpl<>(reader.usageDays(from, page, pageSize), PageRequest.of(page, pageSize),
                reader.countUsageDays(from)));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alert centre", description = "Most recent first")
    public PageResponse<AlertView> alerts(@RequestParam(required = false) Alert.Status status,
            @RequestParam(required = false) Alert.Type type, @RequestParam(required = false) Alert.Severity severity,
            @RequestParam(required = false) UUID companyId, @RequestParam(required = false) UUID vehicleId,
            @ParameterObject @PageableDefault(size = 20, sort = "raisedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(alerts.search(status, type, severity, companyId, vehicleId, pageable));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @Operation(summary = "Take an alert in charge")
    public AlertView acknowledge(@PathVariable UUID id) {
        return alerts.acknowledge(id);
    }

    @PostMapping("/alerts/{id}/resolve")
    @Operation(summary = "Close an alert, with what was done")
    public AlertView resolve(@PathVariable UUID id, @Valid @RequestBody Resolution resolution) {
        return alerts.resolve(id, resolution.comment());
    }
}
