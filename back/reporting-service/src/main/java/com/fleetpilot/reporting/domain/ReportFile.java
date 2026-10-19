package com.fleetpilot.reporting.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The file of a ready report, stored apart from its description so that lists never load it. */
@Entity
@Table(name = "report_file")
public class ReportFile {

    @Id
    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    protected ReportFile() {
    }

    public ReportFile(UUID reportId, byte[] content) {
        this.reportId = reportId;
        this.content = content.clone();
    }

    public UUID getReportId() {
        return reportId;
    }

    public byte[] getContent() {
        return content.clone();
    }
}
