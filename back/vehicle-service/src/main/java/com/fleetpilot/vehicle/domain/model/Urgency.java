package com.fleetpilot.vehicle.domain.model;

/** How soon an open deadline must be handled, computed from today's date and the vehicle's mileage. */
public enum Urgency {
    /** More than 30 days or 1,500 km away. */
    UPCOMING,
    /** Within 30 days or 1,500 km. */
    DUE_SOON,
    /** Date passed or mileage reached. */
    OVERDUE
}
