package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One business action in the platform, kept for the activity journal: who did what, on which record, when. Written in
 * the same transaction as the action, so the journal never shows something that was rolled back.
 */
@Entity
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_label")
    private String targetLabel;

    private String details;

    protected AuditEntry() {
    }

    public AuditEntry(UUID id, Instant occurredAt, String actor, String actorRole, UUID companyId, String action,
            String targetType, String targetId, String targetLabel, String details) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.actorRole = actorRole;
        this.companyId = companyId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public String getDetails() {
        return details;
    }
}
