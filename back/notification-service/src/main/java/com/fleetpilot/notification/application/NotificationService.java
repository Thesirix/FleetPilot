package com.fleetpilot.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.messaging.EmailRequest;
import com.fleetpilot.notification.application.NotificationViews.NotificationDetail;
import com.fleetpilot.notification.application.NotificationViews.NotificationStats;
import com.fleetpilot.notification.application.NotificationViews.NotificationSummary;
import com.fleetpilot.notification.application.NotificationViews.TemplateCount;
import com.fleetpilot.notification.application.Ports.Mailer;
import com.fleetpilot.notification.application.Ports.NotificationQueue;
import com.fleetpilot.notification.application.Ports.OutgoingMail;
import com.fleetpilot.notification.domain.Notification;
import com.fleetpilot.notification.domain.NotificationRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the e-mails taken from the JMS queue, exactly once per request.
 *
 * <p>The request is recorded first (QUEUED), then rendered and sent outside of any database transaction (rendering
 * may call other services, the SMTP server may be slow), then marked SENT. A failure is recorded and rethrown, so that
 * ActiveMQ delivers the message again later; after {@link Notification#MAX_ATTEMPTS} attempts the notification is
 * FAILED and waits for a manual retry from the journal.
 */
@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    /** Thrown to make the JMS broker redeliver the message. */
    public static final class RetryLaterException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RetryLaterException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** A notification taken by a consumer that stopped is taken again after this delay. */
    static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);

    private final NotificationRepository notifications;
    private final MailComposer composer;
    private final Mailer mailer;
    private final NotificationQueue queue;
    private final TransactionTemplate transactions;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, MailComposer composer, Mailer mailer,
            NotificationQueue queue, TransactionTemplate transactions, JsonMapper jsonMapper, Clock clock) {
        this.notifications = notifications;
        this.composer = composer;
        this.mailer = mailer;
        this.queue = queue;
        this.transactions = transactions;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /** Handles one request taken from the queue. */
    public void handle(EmailRequest request) {
        Notification notification = transactions.execute(status -> notifications.findByRequestId(request.requestId())
                .orElseGet(() -> notifications.save(new Notification(UUID.randomUUID(), request.requestId(),
                        new Notification.Target(request.template(), request.to(), request.toName(), request.companyId(),
                                request.relatedType(), request.relatedId(),
                                request.attachments().isEmpty() ? null : String.join(",", request.attachments())),
                        jsonMapper.writeValueAsString(request), clock.instant()))));
        UUID id = notification.getId();
        Instant now = clock.instant();
        Integer claimed = transactions.execute(status -> notifications.claim(id, now, now.minus(CLAIM_TIMEOUT)));
        if (claimed == null || claimed == 0) {
            LOG.debug("Notification {} already {}, request ignored", request.requestId(), notification.getStatus());
            return;
        }
        try {
            OutgoingMail mail = composer.compose(request);
            String messageId = mailer.send(mail);
            transactions.executeWithoutResult(status -> load(id).sent(mail.subject(), mail.html(), messageId,
                    clock.instant()));
            LOG.info("E-mail {} sent to {}", request.template(), request.to());
        } catch (RuntimeException e) {
            Boolean retry = transactions.execute(status -> load(id).failed(e.getMessage()));
            LOG.warn("E-mail {} to {} failed ({}): {}", request.template(), request.to(),
                    Boolean.TRUE.equals(retry) ? "will be retried" : "gave up", e.getMessage());
            if (Boolean.TRUE.equals(retry)) {
                throw new RetryLaterException("E-mail " + request.requestId() + " will be retried", e);
            }
        }
    }

    // ---------------------------------------------------------------- journal

    @Transactional(readOnly = true)
    public Page<NotificationSummary> search(Notification.Status status, String template, String text, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (template != null && !template.isBlank()) {
                predicates.add(cb.equal(root.get("template"), template));
            }
            if (text != null && !text.isBlank()) {
                String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("recipient")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("subject"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("relatedId"), "")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return notifications.findAll(spec, pageable).map(NotificationSummary::of);
    }

    @Transactional(readOnly = true)
    public NotificationDetail get(UUID id) {
        return NotificationDetail.of(load(id));
    }

    /** Sends a failed notification again, with the same request. */
    public NotificationDetail retry(UUID id) {
        Notification notification = transactions.execute(status -> {
            Notification n = load(id);
            if (n.isSent()) {
                throw new BusinessRuleException("ALREADY_SENT", "This e-mail was already sent");
            }
            n.requeue();
            return n;
        });
        queue.enqueue(jsonMapper.readValue(notification.getRequest(), EmailRequest.class));
        return NotificationDetail.of(notification);
    }

    @Transactional(readOnly = true)
    public NotificationStats stats(Duration period) {
        List<Object[]> rows = notifications.countSince(clock.instant().minus(period));
        long sent = 0;
        long queued = 0;
        long failed = 0;
        Map<String, Long> byTemplate = new TreeMap<>();
        for (Object[] row : rows) {
            long count = (Long) row[2];
            switch ((Notification.Status) row[0]) {
                case SENT -> sent += count;
                case QUEUED, SENDING -> queued += count;
                case FAILED -> failed += count;
                default -> throw new IllegalStateException();
            }
            byTemplate.merge((String) row[1], count, Long::sum);
        }
        return new NotificationStats(sent, queued, failed, byTemplate.entrySet().stream()
                .map(e -> new TemplateCount(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.count(), a.count())).toList());
    }

    private Notification load(UUID id) {
        return notifications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }
}
