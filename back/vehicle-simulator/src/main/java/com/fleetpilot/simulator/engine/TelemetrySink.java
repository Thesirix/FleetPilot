package com.fleetpilot.simulator.engine;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;

/** Where the simulated boxes send their messages (Kafka in the application, a list in the tests). */
public interface TelemetrySink {

    void position(PositionReport report);

    void mileage(MileageReport report);

    void fault(FaultReport report);
}
