package com.fleetpilot.notification.infrastructure.kafka;

import java.util.List;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.messaging.IncomingEvent;
import com.fleetpilot.messaging.ProcessedEvents;
import com.fleetpilot.notification.application.EventRouter;
import com.fleetpilot.notification.application.Ports.NotificationQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the business events of the fleet and of the quotes into e-mail requests on the JMS queue. Kafka says what
 * happened; the JMS queue holds the work to do, with its own retries.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.business-events.enabled", havingValue = "true", matchIfMissing = true)
public class BusinessEventListener {

    static final String CONSUMER = "notification-service.emails";

    private static final Logger LOG = LoggerFactory.getLogger(BusinessEventListener.class);

    private final EventRouter router;
    private final NotificationQueue queue;
    private final ProcessedEvents processed;
    private final JsonMapper jsonMapper;

    public BusinessEventListener(EventRouter router, NotificationQueue queue, ProcessedEvents processed,
            JsonMapper jsonMapper) {
        this.router = router;
        this.queue = queue;
        this.processed = processed;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = {EventTypes.TOPIC_FLEET, EventTypes.TOPIC_QUOTES, EventTypes.TOPIC_ALERTS}, groupId = "notification-service")
    @Transactional
    public void onEvent(String message) {
        IncomingEvent event = IncomingEvent.parse(jsonMapper, message);
        if (!processed.firstDelivery(event.eventId(), CONSUMER)) {
            return;
        }
        List<EmailRequest> requests = router.route(event);
        // If enqueuing fails, the transaction rolls back and Kafka delivers the event again; the request ids make
        // the e-mails already queued harmless.
        requests.forEach(queue::enqueue);
        if (!requests.isEmpty()) {
            LOG.debug("{} e-mail(s) queued for {}", requests.size(), event.type());
        }
    }
}
