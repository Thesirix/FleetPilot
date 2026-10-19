package com.fleetpilot.portal.web;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import com.fleetpilot.portal.api.FleetApi;
import com.fleetpilot.portal.auth.OidcClient;
import com.fleetpilot.portal.config.PortalConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the shared objects of the extranet when Tomcat starts the application, and gives them to the servlets:
 * the configuration, the OpenID Connect client and the client of the fleet API.
 */
@WebListener
public class Portal implements ServletContextListener {

    private static final String CONFIG = Portal.class.getName() + ".config";
    private static final String OIDC = Portal.class.getName() + ".oidc";
    private static final String API = Portal.class.getName() + ".api";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        PortalConfig config = PortalConfig.fromEnvironment();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JsonMapper json = JsonMapper.builder().build();
        context.setAttribute(CONFIG, config);
        context.setAttribute(OIDC, new OidcClient(config, http, json));
        context.setAttribute(API, new FleetApi(config.apiUrl(), http, json));
        context.log("Lessor extranet started: issuer " + config.issuer() + ", API " + config.apiUrl());
    }

    public static PortalConfig config(HttpServletRequest request) {
        return (PortalConfig) request.getServletContext().getAttribute(CONFIG);
    }

    public static OidcClient oidc(HttpServletRequest request) {
        return (OidcClient) request.getServletContext().getAttribute(OIDC);
    }

    public static FleetApi api(HttpServletRequest request) {
        return (FleetApi) request.getServletContext().getAttribute(API);
    }

    /** Public address of the callback: Tomcat sits behind Apache, so it is built from the configured public URL. */
    public static String callbackUrl(HttpServletRequest request) {
        return config(request).publicUrl() + request.getContextPath() + "/callback";
    }

    public static String signedOutUrl(HttpServletRequest request) {
        return config(request).publicUrl() + request.getContextPath() + "/signed-out";
    }

    /** Renders a page of the WEB-INF folder (never reachable directly). */
    public static void render(HttpServletRequest request, HttpServletResponse response, String page)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/jsp/" + page + ".jsp").forward(request, response);
    }

    /** The error page with the message for the lessor and the HTTP status. */
    public static void error(HttpServletRequest request, HttpServletResponse response, int status, String message)
            throws ServletException, IOException {
        response.setStatus(status);
        request.setAttribute("message", message);
        render(request, response, "error");
    }
}
