package com.fleetpilot.telemetry.application;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import org.springframework.stereotype.Component;

/**
 * Which vehicles the caller may follow: FleetPilot staff every company, a client manager their own. Data of another
 * company is reported as "not found". Without a user (Kafka consumers), the code runs with the rights of the system.
 */
@Component
public class TelemetryScope {

    private static final UUID NO_COMPANY = new UUID(0, 0);

    public Optional<UUID> restrictedCompanyId() {
        if (!CurrentUser.isAuthenticated() || CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER)) {
            return Optional.empty();
        }
        return Optional.of(CurrentUser.companyId().orElse(NO_COMPANY));
    }

    public UUID companyFilter(UUID requested) {
        return restrictedCompanyId().orElse(requested);
    }

    public void checkAccess(UUID companyId, String resource, Object id) {
        restrictedCompanyId().filter(own -> !own.equals(companyId)).ifPresent(own -> {
            throw new ResourceNotFoundException(resource, id);
        });
    }
}
