package com.fleetpilot.quote.infrastructure.security;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.quote.application.QuoteAccess;
import org.springframework.stereotype.Component;

/**
 * Reads the scope of the caller from the access token.
 *
 * <ul>
 *   <li>staff and supplier integrations (the integration bus) see every quote;</li>
 *   <li>a client manager sees the quotes of their company ({@code company_id} claim);</li>
 *   <li>a garage sees its own quotes ({@code garage_id} claim);</li>
 *   <li>any other user, or a client user without the expected claim, sees nothing;</li>
 *   <li>without a user (event consumers, demo data), the code runs with the rights of the system.</li>
 * </ul>
 */
@Component
public class TokenQuoteAccess implements QuoteAccess {

    /** Matches no data: the safe default for a user without the claim of their role. */
    static final UUID NOBODY = new UUID(0, 0);

    @Override
    public Optional<UUID> restrictedCompanyId() {
        if (!CurrentUser.isAuthenticated() || unrestricted() || CurrentUser.hasAnyRole(Roles.GARAGE)) {
            return Optional.empty();
        }
        return Optional.of(CurrentUser.companyId().orElse(NOBODY));
    }

    @Override
    public Optional<UUID> restrictedGarageId() {
        if (!CurrentUser.isAuthenticated() || unrestricted() || !CurrentUser.hasAnyRole(Roles.GARAGE)) {
            return Optional.empty();
        }
        return Optional.of(CurrentUser.garageId().orElse(NOBODY));
    }

    @Override
    public String author() {
        return CurrentUser.username().orElse("FleetPilot");
    }

    @Override
    public boolean canApproveAnyAmount() {
        return !CurrentUser.isAuthenticated() || CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN);
    }

    private static boolean unrestricted() {
        return CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.API_SUPPLIER);
    }
}
