package com.fleetpilot.portal.auth;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fleetpilot.portal.web.Messages;
import com.fleetpilot.portal.web.Portal;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Keycloak sends the browser back here with a code: exchanged for the tokens, then the user enters the extranet. */
@WebServlet("/callback")
public class CallbackServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(CallbackServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String expectedState = session == null ? null : (String) session.getAttribute(AuthFilter.STATE);
        if (expectedState == null || !expectedState.equals(request.getParameter("state"))
                || request.getParameter("code") == null) {
            // A stale or forged answer: start again from the home page
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }
        String verifier = (String) session.getAttribute(AuthFilter.VERIFIER);
        String nonce = (String) session.getAttribute(AuthFilter.NONCE);
        String target = (String) session.getAttribute(AuthFilter.TARGET);
        OidcClient oidc = Portal.oidc(request);
        OidcClient.Tokens tokens;
        try {
            tokens = oidc.exchange(request.getParameter("code"), Portal.callbackUrl(request), verifier);
        } catch (OidcClient.OidcException e) {
            LOG.log(Level.WARNING, "Sign-in failed", e);
            Portal.error(request, response, HttpServletResponse.SC_BAD_GATEWAY, Messages.SIGN_IN_FAILED);
            return;
        }
        Map<String, Object> idClaims = oidc.claims(tokens.idToken());
        if (!nonce.equals(idClaims.get("nonce"))) {
            Portal.error(request, response, HttpServletResponse.SC_BAD_REQUEST, Messages.SIGN_IN_FAILED);
            return;
        }
        PortalUser user = PortalUser.from(idClaims, oidc.claims(tokens.accessToken()), tokens);
        // A new session id once signed in: a session id planted before the login is worthless
        request.changeSessionId();
        session.removeAttribute(AuthFilter.STATE);
        session.removeAttribute(AuthFilter.NONCE);
        session.removeAttribute(AuthFilter.VERIFIER);
        session.removeAttribute(AuthFilter.TARGET);
        if (user == null) {
            // Signed in, but not as a lessor: the Keycloak session is closed so another account can be used
            request.setAttribute("logoutUrl", oidc.logoutUrl(tokens.idToken(), Portal.signedOutUrl(request)));
            Portal.error(request, response, HttpServletResponse.SC_FORBIDDEN, Messages.NOT_LESSOR);
            return;
        }
        session.setAttribute(PortalUser.SESSION_KEY, user);
        response.sendRedirect(target == null ? request.getContextPath() + "/" : target);
    }
}
