package com.fleetpilot.gateway.security;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

/** Who is connected: the portal adapts its navigation to the roles and the company of the user. */
@Controller
public class ViewerController {

    /** The FleetPilot roles of the user, without the technical Keycloak ones (offline_access...). */
    private static final List<String> APPLICATION_ROLES = List.of(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER,
            Roles.CLIENT_MANAGER, Roles.DRIVER, Roles.GARAGE, Roles.API_SUPPLIER);

    /** The connected user, as described by their access token. */
    public record Viewer(String username, String name, String email, List<String> roles, boolean staff,
            UUID companyId, UUID driverId) {
    }

    @QueryMapping
    public Viewer viewer() {
        Jwt token = CurrentUser.token().orElseThrow().getToken();
        List<String> roles = CurrentUser.token().orElseThrow().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .filter(APPLICATION_ROLES::contains)
                .sorted(Comparator.comparingInt(APPLICATION_ROLES::indexOf))
                .toList();
        return new Viewer(CurrentUser.username().orElse(token.getSubject()), token.getClaimAsString("name"),
                token.getClaimAsString("email"), roles, CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER),
                CurrentUser.companyId().orElse(null), CurrentUser.driverId().orElse(null));
    }
}
