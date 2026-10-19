package com.fleetpilot.vehicle.infrastructure.messaging;

import java.util.UUID;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.messaging.OutboxPublisher;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes the fleet events to the outbox table, in the caller's transaction; the shared relay sends them to Kafka. */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxPublisher outbox;

    public OutboxDomainEventPublisher(OutboxPublisher outbox) {
        this.outbox = outbox;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, UUID companyId, String aggregateType, UUID aggregateId, Object payload) {
        outbox.publish(EventTypes.TOPIC_FLEET, type, companyId, aggregateType, aggregateId, payload);
    }
}
