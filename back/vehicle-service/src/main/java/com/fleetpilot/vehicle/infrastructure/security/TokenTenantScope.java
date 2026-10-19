package com.fleetpilot.vehicle.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.TenantScope;
import org.springframework.stereotype.Component;

/**
 * Reads the client company of the caller from the access token.
 *
 * <ul>
 *   <li>FleetPilot staff and supplier integrations see every company;</li>
 *   <li>client managers and drivers see their own company only (the {@code company_id} claim);</li>
 *   <li>a client user whose token has no company sees nothing at all;</li>
 *   <li>without a user (scheduled jobs, event consumers), the code runs with the rights of the system. The HTTP
 *       layer guarantees that every API request is authenticated.</li>
 * </ul>
 */
@Component
public class TokenTenantScope implements TenantScope {

    /** Company that matches no data: the safe default for a client user without a company claim. */
    static final UUID NO_COMPANY = new UUID(0, 0);

    @Override
    public Optional<UUID> restrictedCompanyId() {
        if (!CurrentUser.isAuthenticated()
                || CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.API_SUPPLIER)) {
            return Optional.empty();
        }
        return Optional.of(CurrentUser.companyId().orElse(NO_COMPANY));
    }

    @Override
    public Optional<String> restrictedLessor() {
        return CurrentUser.hasAnyRole(Roles.API_SUPPLIER) ? CurrentUser.lessor() : Optional.empty();
    }
}
