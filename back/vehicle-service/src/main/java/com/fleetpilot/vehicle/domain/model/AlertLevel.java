package com.fleetpilot.vehicle.domain.model;

/** Reminders sent for a deadline, from the earliest to the most severe. Each level is sent once. */
public enum AlertLevel {
    /** 30 days (or 1,500 km) before. */
    D30,
    /** 7 days (or 500 km) before. */
    D7,
    /** The deadline is passed. */
    OVERDUE
}
