package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.domain.model.AuditEntry;
import com.fleetpilot.vehicle.domain.repository.AuditEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the activity journal. Called by the use cases once the action succeeded, inside their transaction: the
 * journal line and the change are saved together or not at all. The author is the user of the access token.
 */
@Component
public class AuditLog {

    private static final String[] ROLES = {Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.CLIENT_MANAGER, Roles.DRIVER,
        Roles.GARAGE, Roles.API_SUPPLIER};

    private final AuditEntryRepository entries;
    private final Clock clock;

    public AuditLog(AuditEntryRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    /**
     * @param action      what was done, a stable code such as {@code VEHICLE_CREATED}
     * @param targetType  the kind of record: VEHICLE, DRIVER, CONTRACT, DEADLINE, INCIDENT, COMPANY, SITE, USER...
     * @param targetLabel what the journal shows: a registration, a name, a reference
     * @param details     a short complement in plain words, or null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, Object targetId, String targetLabel, UUID companyId,
            String details) {
        entries.save(new AuditEntry(UUID.randomUUID(), clock.instant(), actor(), role(), companyId, action, targetType,
                targetId == null ? null : targetId.toString(), truncate(targetLabel, 200), truncate(details, 500)));
    }

    /** Full name of the user when the token carries it, else the login, else "system" for the scheduled jobs. */
    static String actor() {
        return CurrentUser.token()
                .map(token -> {
                    String name = token.getToken().getClaimAsString("name");
                    return name != null && !name.isBlank() ? name : CurrentUser.username().orElse(token.getName());
                })
                .orElse("FleetPilot");
    }

    static String role() {
        for (String role : ROLES) {
            if (CurrentUser.hasAnyRole(role)) {
                return role;
            }
        }
        return CurrentUser.isAuthenticated() ? "service" : "system";
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
