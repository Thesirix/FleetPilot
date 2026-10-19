package com.fleetpilot.portal.auth;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import com.fleetpilot.portal.web.Csrf;
import com.fleetpilot.portal.web.Portal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Every page needs a signed-in lessor. Without a session the browser is sent to Keycloak; a session whose access
 * token is about to expire is refreshed first. This filter replaced the login form and the password table of v1.
 */
@WebFilter(urlPatterns = "/*")
public class AuthFilter extends HttpFilter {

    static final String STATE = "oidc.state";
    static final String NONCE = "oidc.nonce";
    static final String VERIFIER = "oidc.verifier";
    static final String TARGET = "oidc.target";

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(30);

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }
        HttpSession session = request.getSession(false);
        PortalUser user = session == null ? null : (PortalUser) session.getAttribute(PortalUser.SESSION_KEY);
        if (user != null && user.tokens().expiresWithin(REFRESH_MARGIN, Instant.now())) {
            try {
                user = user.withTokens(Portal.oidc(request).refresh(user.tokens()));
                session.setAttribute(PortalUser.SESSION_KEY, user);
            } catch (OidcClient.OidcException expired) {
                session.invalidate();
                user = null;
            }
        }
        if (user == null) {
            startLogin(request, response);
            return;
        }
        request.setAttribute("user", user);
        // Every page has the sign-out form, error pages included
        request.setAttribute("csrf", Csrf.token(request));
        response.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }

    static boolean isPublic(String path) {
        return path.startsWith("/static/") || path.equals("/callback") || path.equals("/signed-out")
                || path.equals("/favicon.ico");
    }

    /** Remembers where the user wanted to go, then sends the browser to Keycloak. */
    private static void startLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(true);
        String state = OidcClient.randomValue();
        String nonce = OidcClient.randomValue();
        String verifier = OidcClient.randomValue();
        session.setAttribute(STATE, state);
        session.setAttribute(NONCE, nonce);
        session.setAttribute(VERIFIER, verifier);
        boolean page = "GET".equals(request.getMethod());
        String query = request.getQueryString();
        session.setAttribute(TARGET, page ? request.getRequestURI() + (query == null ? "" : "?" + query)
                : request.getContextPath() + "/");
        response.sendRedirect(Portal.oidc(request)
                .authorizationUrl(Portal.callbackUrl(request), state, nonce, verifier).toString());
    }
}
