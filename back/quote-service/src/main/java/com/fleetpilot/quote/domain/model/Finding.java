package com.fleetpilot.quote.domain.model;

/** What the analysis found on a quote line. */
public enum Finding {
    /** Part price above the reference price beyond the tolerance. */
    OVERPRICED_PART,
    /** Labour time above the standard time beyond the tolerance. */
    EXCESSIVE_TIME,
    /** Hourly rate above the negotiated regional rate beyond the tolerance. */
    OVERPRICED_RATE,
    /** The same part or operation is billed twice. */
    DUPLICATE,
    /** Consumables above the lump sum allowed for the work. */
    EXCESSIVE_SUPPLIES,
    /** Item absent from the reference tariff: a manager has to check it. */
    UNKNOWN_ITEM;

    /** Findings that justify a price reduction (an unknown item only calls for a check). */
    public boolean isOvercharge() {
        return this != UNKNOWN_ITEM;
    }
}
