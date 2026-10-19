package com.fleetpilot.reporting.infrastructure.jms;

import java.util.UUID;

import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.common.messaging.Queues;
import com.fleetpilot.reporting.application.ReportService;
import com.fleetpilot.reporting.application.ReportService.Notifier;
import com.fleetpilot.reporting.application.ReportService.ReportQueue;
import org.springframework.context.annotation.Lazy;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two ActiveMQ queues of the reporting service: the reports to build (produced and consumed here, so that the
 * HTTP request returns at once and the heavy work runs in the background), and the e-mails for the notification
 * service.
 */
@Component
public class JmsReports implements ReportQueue, Notifier {

    private final JmsTemplate jms;
    private final JsonMapper jsonMapper;
    private final ReportService reports;

    public JmsReports(JmsTemplate jms, JsonMapper jsonMapper, @Lazy ReportService reports) {
        this.jms = jms;
        this.jsonMapper = jsonMapper;
        this.reports = reports;
    }

    @Override
    public void enqueue(UUID reportId) {
        jms.send(Queues.REPORTS, session -> session.createTextMessage(reportId.toString()));
    }

    @Override
    public void notify(EmailRequest request) {
        String json = jsonMapper.writeValueAsString(request);
        jms.send(Queues.NOTIFICATIONS, session -> session.createTextMessage(json));
    }

    /** One report at a time per consumer, two consumers: big reports never starve the small ones for long. */
    @JmsListener(destination = Queues.REPORTS, concurrency = "1-2")
    public void onReport(String reportId) {
        reports.generate(UUID.fromString(reportId.trim()));
    }
}
