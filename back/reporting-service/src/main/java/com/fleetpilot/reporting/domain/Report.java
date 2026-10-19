package com.fleetpilot.reporting.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A report requested by a user: PENDING, built in the background (RUNNING), then READY to download or FAILED. */
@Entity
@Table(name = "report")
public class Report {

    private static final int MAX_ERROR_LENGTH = 500;

    public enum Status {
        PENDING,
        RUNNING,
        READY,
        FAILED
    }

    /** Who asked for the report: the file is theirs, and they are told by e-mail when it is ready. */
    public record Requester(String username, String email, String name) {
        public Requester {
            Objects.requireNonNull(username, "username");
        }
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReportType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ReportFormat format;

    @Column(nullable = false, updatable = false)
    private String title;

    @Column(name = "company_id", updatable = false)
    private UUID companyId;

    @Column(name = "period_from", updatable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", updatable = false)
    private LocalDate periodTo;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "requester_email", updatable = false)
    private String requesterEmail;

    @Column(name = "requester_name", updatable = false)
    private String requesterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    @Column(name = "row_count")
    private Integer rowCount;

    private String error;

    @Version
    private Long version;

    protected Report() {
    }

    public Report(UUID id, ReportType type, String companyName, UUID companyId, LocalDate from, LocalDate to,
            Requester requester, Instant now) {
        if (type.periodic() && (from == null || to == null)) {
            throw new BusinessRuleException("PERIOD_REQUIRED", "This report covers a period: give its start and end");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("INVALID_PERIOD", "The start of the period must be before its end");
        }
        this.id = Objects.requireNonNull(id);
        this.type = type;
        this.format = type.format();
        this.title = companyName == null ? type.title() : type.title() + " · " + companyName;
        this.companyId = companyId;
        this.periodFrom = type.periodic() ? from : null;
        this.periodTo = type.periodic() ? to : null;
        this.requestedBy = requester.username();
        this.requesterEmail = requester.email();
        this.requesterName = requester.name();
        this.status = Status.PENDING;
        this.requestedAt = now;
    }

    /** The builder takes the report; a report already taken or done cannot be built twice. */
    public void start(Instant now) {
        if (status != Status.PENDING) {
            throw new BusinessRuleException("REPORT_ALREADY_STARTED", "Report " + id + " is " + status);
        }
        this.status = Status.RUNNING;
        this.startedAt = now;
    }

    /** The file is stored apart ({@link ReportFile}); the report keeps its description. */
    public void ready(int size, int rows, Instant now) {
        this.sizeBytes = size;
        this.rowCount = rows;
        this.contentType = format.contentType();
        this.fileName = fileName(now.atZone(ZoneId.of("Europe/Paris")).toLocalDate());
        this.status = Status.READY;
        this.completedAt = now;
    }

    public void failed(String message, Instant now) {
        this.error = message == null ? "unknown" : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        this.status = Status.FAILED;
        this.completedAt = now;
    }

    /** inventaire-du-parc-2026-10-15.xlsx */
    private String fileName(LocalDate day) {
        String slug = Normalizer.normalize(type.title(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug + "-" + day + "." + format.extension();
    }

    public UUID getId() {
        return id;
    }

    public ReportType getType() {
        return type;
    }

    public ReportFormat getFormat() {
        return format;
    }

    public String getTitle() {
        return title;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Integer getSizeBytes() {
        return sizeBytes;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public String getError() {
        return error;
    }

    public Long getVersion() {
        return version;
    }
}
