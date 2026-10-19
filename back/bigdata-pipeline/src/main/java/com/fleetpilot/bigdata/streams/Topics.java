package com.fleetpilot.bigdata.streams;

import java.util.Map;

/** The topics the pipeline reads and writes, and the Elasticsearch index of each output. */
public final class Topics {

    public static final String VEHICLES = "reference.vehicles";
    public static final String ALERTS = "telemetry.alerts";
    public static final String FUEL = "fuel.transactions";

    public static final String POSITIONS_OUT = "analytics.positions";
    public static final String ACTIVITY_OUT = "analytics.fleet-activity";
    public static final String VEHICLE_DAY_OUT = "analytics.vehicle-day";
    public static final String FAULTS_OUT = "analytics.faults";
    public static final String ALERTS_OUT = "analytics.alerts";
    public static final String FUEL_OUT = "analytics.fuel";

    /** Output topic to Elasticsearch index. */
    public static final Map<String, String> INDICES = Map.of(
            POSITIONS_OUT, "fleet-positions",
            ACTIVITY_OUT, "fleet-activity",
            VEHICLE_DAY_OUT, "fleet-vehicle-day",
            FAULTS_OUT, "fleet-faults",
            ALERTS_OUT, "fleet-alerts",
            FUEL_OUT, "fleet-fuel");

    private Topics() {
    }
}
