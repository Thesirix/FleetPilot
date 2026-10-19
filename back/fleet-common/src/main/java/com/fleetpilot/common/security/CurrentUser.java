package com.fleetpilot.common.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The user of the current request, as described by their access token.
 *
 * <p>Outside of an HTTP request (scheduled jobs, event consumers) there is no user: {@link #isAuthenticated()} is
 * false and the code runs with the rights of the system.
 */
public final class CurrentUser {

    /** Claims added to the access token by the FleetPilot Keycloak realm. */
    public static final String COMPANY_CLAIM = "company_id";
    public static final String DRIVER_CLAIM = "driver_id";
    public static final String GARAGE_CLAIM = "garage_id";
    /** Name of the leasing company of a lessor user (extranet of the lessors): "Arval", "Ayvens"... */
    public static final String LESSOR_CLAIM = "lessor";

    private CurrentUser() {
    }

    public static Optional<JwtAuthenticationToken> token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()
                ? Optional.of(jwt) : Optional.empty();
    }

    public static boolean isAuthenticated() {
        return token().isPresent();
    }

    public static boolean hasAnyRole(String... roles) {
        return token().map(token -> token.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority -> Arrays.stream(roles)
                                .anyMatch(role -> authority.equals(KeycloakJwtConverter.ROLE_PREFIX + role))))
                .orElse(false);
    }

    /** The login of the user ({@code preferred_username}), or the token subject when the claim is absent. */
    public static Optional<String> username() {
        return token().map(token -> {
            String preferred = token.getToken().getClaimAsString("preferred_username");
            return preferred != null ? preferred : token.getName();
        });
    }

    /** The OAuth2 client the token was issued to ({@code azp}): the portal, or a service calling as itself. */
    public static Optional<String> clientId() {
        return token().map(token -> token.getToken().getClaimAsString("azp"));
    }

    public static Optional<UUID> companyId() {
        return uuidClaim(COMPANY_CLAIM);
    }

    public static Optional<UUID> driverId() {
        return uuidClaim(DRIVER_CLAIM);
    }

    public static Optional<UUID> garageId() {
        return uuidClaim(GARAGE_CLAIM);
    }

    public static Optional<String> lessor() {
        return token().map(token -> token.getToken().getClaimAsString(LESSOR_CLAIM)).filter(value -> !value.isBlank());
    }

    private static Optional<UUID> uuidClaim(String claim) {
        return token().map(token -> token.getToken().getClaimAsString(claim))
                .filter(value -> !value.isBlank())
                .flatMap(CurrentUser::parse);
    }

    private static Optional<UUID> parse(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
