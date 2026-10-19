package com.fleetpilot.portal.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The signed-in user of a leasing company, kept in the HTTP session with their tokens. Only accounts with the
 * {@code api-supplier} role and a {@code lessor} claim reach the extranet.
 */
public record PortalUser(String username, String name, String email, String lessor, OidcClient.Tokens tokens)
        implements Serializable {

    public static final String SESSION_KEY = "portalUser";
    static final String SUPPLIER_ROLE = "api-supplier";

    /** The user described by the claims of the ID token and the roles of the access token, or null if not allowed. */
    static PortalUser from(Map<String, Object> idClaims, Map<String, Object> accessClaims, OidcClient.Tokens tokens) {
        Object lessor = accessClaims.get("lessor");
        if (!roles(accessClaims).contains(SUPPLIER_ROLE) || !(lessor instanceof String name) || name.isBlank()) {
            return null;
        }
        String username = String.valueOf(idClaims.getOrDefault("preferred_username", idClaims.get("sub")));
        Object fullName = idClaims.get("name");
        return new PortalUser(username, fullName == null ? username : fullName.toString(),
                (String) idClaims.get("email"), name, tokens);
    }

    public PortalUser withTokens(OidcClient.Tokens fresh) {
        return new PortalUser(username, name, email, lessor, fresh);
    }

    public String initials() {
        String[] words = name.trim().split("[\\s.@]+");
        StringBuilder initials = new StringBuilder();
        for (int i = 0; i < Math.min(2, words.length); i++) {
            if (!words[i].isEmpty()) {
                initials.append(Character.toUpperCase(words[i].charAt(0)));
            }
        }
        return initials.toString();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> roles(Map<String, Object> claims) {
        if (claims.get("realm_access") instanceof Map<?, ?> access && access.get("roles") instanceof Collection<?> roles) {
            return (Collection<String>) roles;
        }
        return List.of();
    }
}
