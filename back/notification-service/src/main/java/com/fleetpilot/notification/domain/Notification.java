package com.fleetpilot.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An e-mail sent, or to send, by FleetPilot. Its rendering is kept so that it can be read again. */
@Entity
@Table(name = "notification")
public class Notification {

    /** After this many failed attempts, the notification stops being retried and waits for a manual retry. */
    public static final int MAX_ATTEMPTS = 5;

    private static final int MAX_ERROR_LENGTH = 500;

    public enum Status {
        QUEUED,
        /** Taken by a consumer, being rendered and sent. */
        SENDING,
        SENT,
        FAILED
    }

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true, updatable = false)
    private String requestId;

    @Column(nullable = false, updatable = false)
    private String template;

    @Column(nullable = false, updatable = false)
    private String recipient;

    @Column(name = "recipient_name")
    private String recipientName;

    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "related_type")
    private String relatedType;

    @Column(name = "related_id")
    private String relatedId;

    private String attachments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String request;

    @Column(name = "body_html")
    private String bodyHtml;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Version
    private Long version;

    protected Notification() {
    }

    /** What the notification is about, for the journal of the sendings. */
    public record Target(String template, String recipient, String recipientName, UUID companyId, String relatedType,
            String relatedId, String attachments) {

        public Target {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(recipient, "recipient");
        }
    }

    public Notification(UUID id, String requestId, Target target, String request, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.requestId = Objects.requireNonNull(requestId);
        this.template = target.template();
        this.recipient = target.recipient();
        this.recipientName = target.recipientName();
        this.companyId = target.companyId();
        this.relatedType = target.relatedType();
        this.relatedId = target.relatedId();
        this.attachments = target.attachments();
        this.request = Objects.requireNonNull(request);
        this.status = Status.QUEUED;
        this.createdAt = now;
    }

    public void sent(String renderedSubject, String html, String newMessageId, Instant at) {
        this.subject = renderedSubject;
        this.bodyHtml = html;
        this.messageId = newMessageId;
        this.status = Status.SENT;
        this.sentAt = at;
        this.attempts++;
        this.lastError = null;
    }

    /**
     * Records a failed attempt.
     *
     * @return true when the notification may be tried again, false when it gave up (status FAILED)
     */
    public boolean failed(String error) {
        this.attempts++;
        this.lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
            return false;
        }
        this.status = Status.QUEUED;
        return true;
    }

    /** A manager asks to send a failed notification again. */
    public void requeue() {
        this.status = Status.QUEUED;
        this.attempts = 0;
        this.lastError = null;
    }

    public boolean isSent() {
        return status == Status.SENT;
    }

    public UUID getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTemplate() {
        return template;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getSubject() {
        return subject;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getRelatedType() {
        return relatedType;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public String getAttachments() {
        return attachments;
    }

    public String getRequest() {
        return request;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Long getVersion() {
        return version;
    }
}
