package com.fleetpilot.portal.auth;

import java.io.IOException;

import com.fleetpilot.portal.web.Csrf;
import com.fleetpilot.portal.web.Portal;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Signs out of the extranet and of Keycloak, then shows the signed-out page. */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!Csrf.valid(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        HttpSession session = request.getSession(false);
        PortalUser user = session == null ? null : (PortalUser) session.getAttribute(PortalUser.SESSION_KEY);
        if (session != null) {
            session.invalidate();
        }
        String idToken = user == null ? null : user.tokens().idToken();
        response.sendRedirect(Portal.oidc(request).logoutUrl(idToken, Portal.signedOutUrl(request)).toString());
    }
}
