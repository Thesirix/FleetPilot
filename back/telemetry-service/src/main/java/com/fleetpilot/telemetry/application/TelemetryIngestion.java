package com.fleetpilot.telemetry.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.Severity;
import com.fleetpilot.telemetry.domain.Alert;
import com.fleetpilot.telemetry.domain.LiveVehicle;
import com.fleetpilot.telemetry.domain.LiveVehicle.AlertCandidate;
import com.fleetpilot.telemetry.domain.LiveVehicle.Observation;
import com.fleetpilot.telemetry.domain.LiveVehicle.TripRecord;
import com.fleetpilot.telemetry.domain.LiveVehicle.VehicleInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the raw messages of the boxes into what FleetPilot keeps: the live state of each vehicle, a sampled history,
 * the trips and the daily usage, the trouble codes, and the alerts. Messages arrive in batches from Kafka; a batch is
 * stored in one transaction.
 *
 * <p>The trip in progress and the state of the rules of each vehicle are kept in memory ({@link LiveVehicle}): Kafka
 * delivers the messages of one vehicle to one consumer, in order. After a restart, a trip in progress is simply
 * counted from the next position.
 */
@Service
public class TelemetryIngestion {

    private final Map<UUID, LiveVehicle> live = new ConcurrentHashMap<>();
    private final TelemetryStore store;
    private final AlertService alerts;
    private final FleetReference reference;

    public TelemetryIngestion(TelemetryStore store, AlertService alerts, FleetReference reference) {
        this.store = store;
        this.alerts = alerts;
        this.reference = reference;
    }

    @Transactional
    public void positions(List<PositionReport> batch) {
        Map<UUID, PositionReport> latest = new LinkedHashMap<>();
        List<PositionReport> history = new ArrayList<>();
        List<TripRecord> trips = new ArrayList<>();
        List<PositionReport> ordered = batch.stream().sorted(Comparator.comparing(PositionReport::at)).toList();
        for (PositionReport report : ordered) {
            VehicleInfo info = reference.info(report.vehicleId());
            Observation observation = live.computeIfAbsent(report.vehicleId(), id -> new LiveVehicle())
                    .observe(report, info);
            latest.merge(report.vehicleId(), report, (a, b) -> b.at().isAfter(a.at()) ? b : a);
            if (observation.keepInHistory()) {
                history.add(report);
            }
            if (observation.finishedTrip() != null) {
                trips.add(observation.finishedTrip());
            }
            for (AlertCandidate candidate : observation.alerts()) {
                alerts.raise(candidate, context(report), report.at());
            }
        }
        store.saveStates(latest.values());
        store.savePositions(history);
        store.saveTrips(trips);
    }

    @Transactional
    public void mileages(List<MileageReport> batch) {
        Map<UUID, MileageReport> latest = new LinkedHashMap<>();
        batch.forEach(report -> latest.merge(report.vehicleId(), report,
                (a, b) -> b.odometerKm() >= a.odometerKm() ? b : a));
        store.saveOdometers(latest.values());
        for (MileageReport report : latest.values()) {
            LiveVehicle.serviceDue(report.odometerKm(), reference.info(report.vehicleId())).ifPresent(candidate ->
                    alerts.raise(candidate, new Alert.Context(report.vehicleId(), report.companyId(),
                            report.registration(), null, null), report.at()));
        }
    }

    /** A serious trouble code raises a critical alert, a medium one a warning; a minor one is only recorded. */
    @Transactional
    public void fault(FaultReport fault) {
        store.saveFault(fault);
        if (fault.severity() == Severity.LOW) {
            return;
        }
        boolean serious = fault.severity() == Severity.HIGH;
        alerts.raise(new AlertCandidate(serious ? Alert.Type.ENGINE_FAULT : Alert.Type.ENGINE_WARNING,
                        serious ? Alert.Severity.CRITICAL : Alert.Severity.WARNING,
                        fault.code() + " : " + fault.description(), null),
                new Alert.Context(fault.vehicleId(), fault.companyId(), fault.registration(), null, null), fault.at());
    }

    private static Alert.Context context(PositionReport report) {
        return new Alert.Context(report.vehicleId(), report.companyId(), report.registration(), report.latitude(),
                report.longitude());
    }
}
