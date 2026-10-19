package com.fleetpilot.quote.domain.model;

/** How the quote reached FleetPilot. */
public enum QuoteChannel {
    /** Typed by the garage in its space of the portal. */
    PORTAL,
    /** XML file sent by the garage's software, through the integration bus. */
    ESB,
    /** REST API of a garage network. */
    API
}
