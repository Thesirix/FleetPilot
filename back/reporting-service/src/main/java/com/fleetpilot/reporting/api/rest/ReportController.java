package com.fleetpilot.reporting.api.rest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.reporting.application.ReportService;
import com.fleetpilot.reporting.application.ReportService.ReportDownload;
import com.fleetpilot.reporting.application.ReportService.ReportTypeView;
import com.fleetpilot.reporting.application.ReportView;
import com.fleetpilot.reporting.application.TcoCalculator.Indicators;
import com.fleetpilot.reporting.domain.ReportType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Reports", description = "Excel and PDF reports built in the background, fleet indicators")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** What a user asks for; the period is required for the reports that cover one. */
    public record RequestReport(@NotNull ReportType type, UUID companyId, LocalDate from, LocalDate to) {
    }

    @GetMapping("/reports/types")
    @Operation(summary = "The reports that can be requested")
    public List<ReportTypeView> catalog() {
        return reports.catalog();
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Request a report", description = "Answers at once (202) with the PENDING report; the file is "
            + "built in the background and the requester gets an e-mail when it is ready. A client manager always gets "
            + "the data of their own company")
    public ReportView request(@Valid @RequestBody RequestReport command) {
        return reports.request(command.type(), command.companyId(), command.from(), command.to());
    }

    @GetMapping("/reports")
    @Operation(summary = "My reports, most recent first")
    public PageResponse<ReportView> mine(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(reports.mine(pageable));
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "One report and its progress")
    public ReportView get(@PathVariable UUID id) {
        return reports.get(id);
    }

    @GetMapping("/reports/{id}/file")
    @Operation(summary = "Download the file of a ready report")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        ReportDownload file = reports.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .body(file.content());
    }

    @GetMapping("/indicators")
    @Operation(summary = "Fleet indicators over twelve months", description = "Total cost of ownership, cost per "
            + "kilometre, claims for 100 vehicles, immobilisation rate. Refreshed every ten minutes")
    public Indicators indicators(@RequestParam(required = false) UUID companyId) {
        return reports.indicators(companyId);
    }
}
