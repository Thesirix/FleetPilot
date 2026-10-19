package com.fleetpilot.telemetry.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.telemetry.domain.LiveVehicle.TripRecord;

/**
 * Storage of the telemetry, written in batches (thousands of positions a minute): plain JDBC, no entity per row.
 * Every method takes part in the caller's transaction.
 */
public interface TelemetryStore {

    /** Upserts the last known state of each vehicle. */
    void saveStates(Collection<PositionReport> latest);

    /** Keeps these positions in the history (duplicates ignored). */
    void savePositions(List<PositionReport> positions);

    /** Records finished trips and adds them to the daily usage of their vehicles. */
    void saveTrips(List<TripRecord> trips);

    /** Updates the odometer of the vehicles. */
    void saveOdometers(Collection<MileageReport> latest);

    void saveFault(FaultReport fault);

    /** Deletes the positions older than the retention period; returns the number of rows deleted. */
    int purgePositions(Instant before);
}
