package com.fleetpilot.integration.journal;

/** Where an exchange with a partner stands. */
public enum ExchangeStatus {
    /** Received, being handled. */
    RUNNING,
    /** Every record was accepted. */
    PROCESSED,
    /** Some records were accepted, the others are listed as issues. */
    PARTIAL,
    /** Nothing could be accepted: invalid file, unknown vehicle, refused by the business service. */
    REJECTED,
    /** The same file was already handled: ignored. */
    DUPLICATE,
    /** A technical failure persisted after the retries (service down...): the exchange can be replayed. */
    FAILED
}
