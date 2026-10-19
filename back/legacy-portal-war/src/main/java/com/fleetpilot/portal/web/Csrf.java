package com.fleetpilot.portal.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.fleetpilot.portal.auth.OidcClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Synchronizer token against cross-site request forgery: every form carries the token of the session, and a POST
 * without it is refused. The session cookie is also SameSite=Lax (see context.xml).
 */
public final class Csrf {

    public static final String PARAMETER = "_csrf";
    private static final String SESSION_KEY = "csrf.token";

    private Csrf() {
    }

    /** The token of the session, created on first use; JSP pages read it as ${csrf}. */
    public static String token(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(SESSION_KEY);
        if (token == null) {
            token = OidcClient.randomValue();
            session.setAttribute(SESSION_KEY, token);
        }
        return token;
    }

    public static boolean valid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(SESSION_KEY);
        String sent = request.getParameter(PARAMETER);
        return expected != null && sent != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                sent.getBytes(StandardCharsets.UTF_8));
    }
}
