package com.fleetpilot.vehicle.application;

import java.time.Instant;
import java.util.UUID;

import com.fleetpilot.vehicle.application.view.Views.AuditView;
import com.fleetpilot.vehicle.domain.repository.AuditEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the activity journal: FleetPilot staff see every company, a client manager only theirs. */
@Service
@Transactional(readOnly = true)
public class AuditQuery {

    private final AuditEntryRepository entries;
    private final TenantScope tenant;

    public AuditQuery(AuditEntryRepository entries, TenantScope tenant) {
        this.entries = entries;
        this.tenant = tenant;
    }

    public Page<AuditView> search(UUID companyId, String targetType, String actor, Instant since, Pageable pageable) {
        return entries.search(tenant.companyFilter(companyId), blank(targetType), blank(actor),
                since == null ? Instant.EPOCH : since, pageable)
                .map(AuditView::of);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
