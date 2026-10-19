package com.fleetpilot.reporting.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.reporting.domain.Report;
import com.fleetpilot.reporting.domain.ReportFormat;
import com.fleetpilot.reporting.domain.ReportType;

/** A report as listed in the portal: its description and progress, never its file. */
public record ReportView(UUID id, ReportType type, ReportFormat format, String title, UUID companyId,
        LocalDate periodFrom, LocalDate periodTo, Report.Status status, Instant requestedAt, Instant completedAt,
        String fileName, Integer sizeBytes, Integer rowCount, String error) {

    public static ReportView of(Report r) {
        return new ReportView(r.getId(), r.getType(), r.getFormat(), r.getTitle(), r.getCompanyId(), r.getPeriodFrom(),
                r.getPeriodTo(), r.getStatus(), r.getRequestedAt(), r.getCompletedAt(), r.getFileName(),
                r.getSizeBytes(), r.getRowCount(), r.getError());
    }
}
