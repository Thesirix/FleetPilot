package com.fleetpilot.quote.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a garage quote.
 *
 * <pre>
 * RECEIVED ──(vehicle identified, analysis)──► IN_REVIEW ──► ACCEPTED ──► COMPLETED ──► INVOICED
 *                                          └─► TO_NEGOTIATE ─┘   (or REJECTED from any open status)
 * </pre>
 */
public enum QuoteStatus {
    /** Received, but the vehicle could not be identified yet: no analysis. */
    RECEIVED,
    /** Analysed, compliant or with items to check: waiting for the manager's decision. */
    IN_REVIEW,
    /** Analysed with overcharges: the manager negotiates with the garage. */
    TO_NEGOTIATE,
    ACCEPTED,
    REJECTED,
    /** Work done by the garage. */
    COMPLETED,
    /** Invoice received and reconciled with the accepted amount. */
    INVOICED;

    public boolean isOpen() {
        return this == RECEIVED || this == IN_REVIEW || this == TO_NEGOTIATE;
    }

    Set<QuoteStatus> allowedTransitions() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(IN_REVIEW, TO_NEGOTIATE, ACCEPTED, REJECTED);
            case IN_REVIEW, TO_NEGOTIATE -> EnumSet.of(IN_REVIEW, TO_NEGOTIATE, ACCEPTED, REJECTED);
            case ACCEPTED -> EnumSet.of(COMPLETED);
            case COMPLETED -> EnumSet.of(INVOICED);
            case REJECTED, INVOICED -> EnumSet.noneOf(QuoteStatus.class);
        };
    }

    public boolean canMoveTo(QuoteStatus target) {
        return allowedTransitions().contains(target);
    }
}
