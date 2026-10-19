package com.fleetpilot.quote.infrastructure.messaging;

import java.time.Clock;
import java.util.Set;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.messaging.IncomingEvent;
import com.fleetpilot.messaging.ProcessedEvents;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.domain.repository.VehicleRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps the local copy of the vehicles up to date from the events of the fleet module. Each event is handled once,
 * in the same transaction as the change it causes.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.fleet-events.enabled", havingValue = "true", matchIfMissing = true)
public class FleetEventsListener {

    static final String CONSUMER = "quote-service.vehicles";

    private static final Logger LOG = LoggerFactory.getLogger(FleetEventsListener.class);
    private static final Set<String> HANDLED = Set.of(EventTypes.VEHICLE_CREATED, EventTypes.VEHICLE_UPDATED);

    private final VehicleRefRepository vehicles;
    private final ProcessedEvents processed;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public FleetEventsListener(VehicleRefRepository vehicles, ProcessedEvents processed, JsonMapper jsonMapper,
            Clock clock) {
        this.vehicles = vehicles;
        this.processed = processed;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @KafkaListener(topics = EventTypes.TOPIC_FLEET, groupId = "quote-service")
    @Transactional
    public void onFleetEvent(String message) {
        IncomingEvent event = IncomingEvent.parse(jsonMapper, message);
        if (!HANDLED.contains(event.type()) || !processed.firstDelivery(event.eventId(), CONSUMER)) {
            return;
        }
        event.uuid("vehicleId").ifPresent(vehicleId -> {
            String registration = event.text("registration");
            vehicles.findById(vehicleId).ifPresentOrElse(
                    existing -> {
                        if (registration != null && !registration.equals(existing.getRegistration())) {
                            existing.renamed(registration, clock.instant());
                        }
                    },
                    () -> {
                        if (EventTypes.VEHICLE_CREATED.equals(event.type()) && event.companyId() != null) {
                            vehicles.save(new VehicleRef(vehicleId, event.companyId(), registration, event.text("make"),
                                    event.text("model"), event.text("category"), event.text("energy"), null,
                                    clock.instant()));
                            LOG.info("Vehicle {} added to the quote reference", registration);
                        }
                    });
        });
    }
}
