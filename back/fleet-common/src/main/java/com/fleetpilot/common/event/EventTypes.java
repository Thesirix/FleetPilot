package com.fleetpilot.common.event;

/** Catalogue of Kafka topics and event types exchanged between FleetPilot services. */
public final class EventTypes {

    /** Business events of the fleet (vehicle-service). */
    public static final String TOPIC_FLEET = "fleet.events";
    /** Garage quote lifecycle (quote-service). */
    public static final String TOPIC_QUOTES = "quote.events";
    /** Raw positions sent by connected vehicles. */
    public static final String TOPIC_TELEMETRY_POSITION = "telemetry.position";
    /** Odometer readings sent by connected vehicles. */
    public static final String TOPIC_TELEMETRY_MILEAGE = "telemetry.mileage";
    /** Diagnostic trouble codes sent by connected vehicles. */
    public static final String TOPIC_TELEMETRY_FAULT = "telemetry.fault";
    /** Alerts raised by telemetry rules. */
    public static final String TOPIC_ALERTS = "telemetry.alerts";

    public static final String VEHICLE_CREATED = "vehicle.created";
    public static final String VEHICLE_UPDATED = "vehicle.updated";
    public static final String VEHICLE_STATUS_CHANGED = "vehicle.status-changed";
    public static final String DRIVER_ASSIGNED = "driver.assigned";
    public static final String DRIVER_UNASSIGNED = "driver.unassigned";
    public static final String DEADLINE_DUE = "deadline.due";
    public static final String INCIDENT_DECLARED = "incident.declared";
    public static final String CONTRACT_MILEAGE_OVERRUN = "contract.mileage-overrun";

    public static final String QUOTE_RECEIVED = "quote.received";
    public static final String QUOTE_ANALYSED = "quote.analysed";
    public static final String QUOTE_COUNTER_OFFER = "quote.counter-offer";
    public static final String QUOTE_ACCEPTED = "quote.accepted";
    public static final String QUOTE_REJECTED = "quote.rejected";
    public static final String QUOTE_INVOICED = "quote.invoiced";

    public static final String ALERT_RAISED = "alert.raised";

    private EventTypes() {
    }
}
