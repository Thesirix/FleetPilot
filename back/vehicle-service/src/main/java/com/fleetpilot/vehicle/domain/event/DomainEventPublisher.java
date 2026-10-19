package com.fleetpilot.vehicle.domain.event;

import java.util.UUID;

/**
 * Port used by the application layer to publish business events. The implementation must take part in the current
 * transaction: an event is published if, and only if, the business change is committed.
 */
public interface DomainEventPublisher {

    /**
     * @param type          event type, one of {@link com.fleetpilot.common.event.EventTypes}
     * @param companyId     client company the event belongs to
     * @param aggregateType kind of the changed aggregate ("vehicle", "driver"...)
     * @param aggregateId   id of the changed aggregate, also used as the Kafka key to keep per-aggregate order
     * @param payload       one of the {@link FleetEvents} records
     */
    void publish(String type, UUID companyId, String aggregateType, UUID aggregateId, Object payload);
}
