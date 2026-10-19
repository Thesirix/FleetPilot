package com.fleetpilot.common.messaging;

/**
 * JMS queues of ActiveMQ. Kafka carries the business facts ("a quote was accepted"); the JMS queues carry the work to
 * do ("send this e-mail", "build this report"), each message handled once by one consumer, with redelivery on failure.
 */
public final class Queues {

    /** {@link EmailRequest} messages, consumed by the notification service. */
    public static final String NOTIFICATIONS = "fleetpilot.notifications";
    /** Report ids to build, consumed by the reporting service. */
    public static final String REPORTS = "fleetpilot.reports";

    private Queues() {
    }
}
