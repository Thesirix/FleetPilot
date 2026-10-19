package com.fleetpilot.notification.infrastructure.jms;

import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.common.messaging.Queues;
import com.fleetpilot.notification.application.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes the e-mail requests of every service. The JMS session is transacted: when sending fails, the exception
 * rolls the message back and ActiveMQ delivers it again after the redelivery delay.
 */
@Component
public class NotificationRequestListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationRequestListener.class);

    private final NotificationService notifications;
    private final JsonMapper jsonMapper;

    public NotificationRequestListener(NotificationService notifications, JsonMapper jsonMapper) {
        this.notifications = notifications;
        this.jsonMapper = jsonMapper;
    }

    @JmsListener(destination = Queues.NOTIFICATIONS, concurrency = "2-4")
    public void onRequest(String json) {
        EmailRequest request;
        try {
            request = jsonMapper.readValue(json, EmailRequest.class);
        } catch (JacksonException e) {
            // A malformed message will never succeed: it is logged and dropped instead of being redelivered forever
            LOG.error("Unreadable e-mail request dropped: {}", e.getOriginalMessage());
            return;
        }
        notifications.handle(request);
    }
}
