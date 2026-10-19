package com.fleetpilot.notification.infrastructure.jms;

import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.common.messaging.Queues;
import com.fleetpilot.notification.application.Ports.NotificationQueue;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Puts e-mail requests on the ActiveMQ queue, as JSON text messages readable by any client. */
@Component
public class JmsNotificationQueue implements NotificationQueue {

    private final JmsTemplate jms;
    private final JsonMapper jsonMapper;

    public JmsNotificationQueue(JmsTemplate jms, JsonMapper jsonMapper) {
        this.jms = jms;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void enqueue(EmailRequest request) {
        String json = jsonMapper.writeValueAsString(request);
        jms.send(Queues.NOTIFICATIONS, session -> session.createTextMessage(json));
    }
}
