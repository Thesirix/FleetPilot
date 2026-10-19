package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One step of the timeline of a quote: who did what, when, for which amount. */
@Entity
@Table(name = "quote_history")
public class QuoteHistoryEntry {

    /** Who acted. */
    public enum Party {
        GARAGE,
        MANAGER,
        SYSTEM
    }

    /** What happened. */
    public enum Action {
        SUBMITTED,
        VEHICLE_IDENTIFIED,
        ANALYSED,
        AUTO_APPROVED,
        COUNTER_OFFER,
        COUNTER_ACCEPTED,
        COUNTER_REFUSED,
        REVISED,
        ACCEPTED,
        REJECTED,
        COMPLETED,
        INVOICED
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private Quote quote;

    @Column(nullable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Party party;

    @Column(nullable = false)
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    private String message;

    protected QuoteHistoryEntry() {
    }

    QuoteHistoryEntry(Quote quote, Instant at, Party party, String author, Action action, BigDecimal amount,
            String message) {
        this.id = UUID.randomUUID();
        this.quote = quote;
        this.at = at;
        this.party = party;
        this.author = author;
        this.action = action;
        this.amount = amount;
        this.message = message;
    }

    public UUID getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public Party getParty() {
        return party;
    }

    public String getAuthor() {
        return author;
    }

    public Action getAction() {
        return action;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }
}
