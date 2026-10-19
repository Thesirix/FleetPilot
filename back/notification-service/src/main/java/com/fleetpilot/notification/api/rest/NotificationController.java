package com.fleetpilot.notification.api.rest;

import java.time.Duration;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.notification.application.NotificationService;
import com.fleetpilot.notification.application.NotificationViews.NotificationDetail;
import com.fleetpilot.notification.application.NotificationViews.NotificationStats;
import com.fleetpilot.notification.application.NotificationViews.NotificationSummary;
import com.fleetpilot.notification.domain.Notification;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Journal of the sendings: FleetPilot staff check what was sent, read it, and send a failed e-mail again. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize(Roles.ANY_STAFF)
@Tag(name = "Notifications", description = "E-mails sent by FleetPilot")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "Search the sent e-mails", description = "Most recent first")
    public PageResponse<NotificationSummary> search(
            @RequestParam(required = false) Notification.Status status,
            @RequestParam(required = false) String template,
            @Parameter(description = "Recipient, subject or related record") @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(notifications.search(status, template, q, pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "E-mails of the last 30 days, by status and template")
    public NotificationStats stats() {
        return notifications.stats(Duration.ofDays(30));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One e-mail, with its rendering")
    public NotificationDetail get(@PathVariable UUID id) {
        return notifications.get(id);
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Send a failed e-mail again")
    public NotificationDetail retry(@PathVariable UUID id) {
        return notifications.retry(id);
    }
}
