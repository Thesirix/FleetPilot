package com.fleetpilot.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.notification.domain.Notification;

/** Read models of the journal of the sendings. */
public final class NotificationViews {

    private NotificationViews() {
    }

    public record NotificationSummary(UUID id, String template, String recipient, String recipientName, String subject,
            Notification.Status status, int attempts, String lastError, String relatedType, String relatedId,
            String attachments, Instant createdAt, Instant sentAt) {

        public static NotificationSummary of(Notification n) {
            return new NotificationSummary(n.getId(), n.getTemplate(), n.getRecipient(), n.getRecipientName(),
                    n.getSubject(), n.getStatus(), n.getAttempts(), n.getLastError(), n.getRelatedType(),
                    n.getRelatedId(), n.getAttachments(), n.getCreatedAt(), n.getSentAt());
        }
    }

    /** With the rendered e-mail, shown as it was received. */
    public record NotificationDetail(NotificationSummary summary, String bodyHtml, String messageId) {

        public static NotificationDetail of(Notification n) {
            return new NotificationDetail(NotificationSummary.of(n), n.getBodyHtml(), n.getMessageId());
        }
    }

    public record TemplateCount(String template, long count) {
    }

    public record NotificationStats(long sent, long queued, long failed, List<TemplateCount> byTemplate) {
    }
}
