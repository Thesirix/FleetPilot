package com.fleetpilot.portal.config;

import java.net.URI;
import java.util.function.UnaryOperator;

/**
 * Settings of the extranet, read from the environment of the Tomcat container (a system property of the same name
 * wins, for the tests). The browser and Tomcat reach Keycloak at different addresses in Docker: the public issuer is
 * the one written in the tokens, the internal one is used for the calls made by the server.
 */
public record PortalConfig(URI issuer, URI internalIssuer, String clientId, String clientSecret, URI apiUrl,
        URI publicUrl) {

    public static PortalConfig fromEnvironment() {
        return from(name -> {
            String property = System.getProperty(name);
            return property != null ? property : System.getenv(name);
        });
    }

    /** The settings read from a source of variables (the environment, or a map in the tests). */
    public static PortalConfig from(UnaryOperator<String> source) {
        String issuer = value(source, "FLEETPILOT_ISSUER", "http://localhost:8180/realms/fleetpilot");
        return new PortalConfig(
                URI.create(trim(issuer)),
                URI.create(trim(value(source, "FLEETPILOT_ISSUER_INTERNAL", issuer))),
                value(source, "FLEETPILOT_CLIENT_ID", "fleetpilot-legacy-portal"),
                value(source, "FLEETPILOT_CLIENT_SECRET", "dev-legacy-portal-secret"),
                URI.create(trim(value(source, "FLEETPILOT_API_URL", "http://localhost:8081")) + "/"),
                URI.create(trim(value(source, "FLEETPILOT_PUBLIC_URL", "http://localhost:8095"))));
    }

    private static String value(UnaryOperator<String> source, String name, String fallback) {
        String value = source.apply(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
