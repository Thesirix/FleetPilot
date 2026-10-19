package com.fleetpilot.quote.domain.model;

/** Kind of quote line. */
public enum LineKind {
    /** A part: quantity of units at a unit price. */
    PART,
    /** Labour: hours at an hourly rate. */
    LABOUR,
    /** Consumables and small supplies ("ingrédients"), billed as a lump sum. */
    SUPPLIES
}
