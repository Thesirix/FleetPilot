package com.fleetpilot.portal.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.fleetpilot.portal.api.FleetApi.ApiException;
import com.fleetpilot.portal.auth.PortalUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A reading at the return of the vehicle (or at an inspection by the lessor). Post, then redirect to the contract
 * with a message: refreshing the page never records the reading twice.
 */
@WebServlet("/reading")
public class ReadingServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!Csrf.valid(request)) {
            Portal.error(request, response, HttpServletResponse.SC_FORBIDDEN, Messages.EXPIRED_FORM);
            return;
        }
        UUID contractId = Ids.parse(request.getParameter("contract"));
        UUID vehicleId = Ids.parse(request.getParameter("vehicle"));
        if (contractId == null || vehicleId == null) {
            Portal.error(request, response, HttpServletResponse.SC_NOT_FOUND, Messages.NOT_FOUND);
            return;
        }
        String back = request.getContextPath() + "/contract?id=" + contractId;
        Integer mileage = mileage(request.getParameter("mileage"));
        Instant readAt = readAt(request.getParameter("readAt"));
        if (mileage == null || readAt == null) {
            Flash.error(request, Messages.INVALID_READING);
            response.sendRedirect(back);
            return;
        }
        if (readAt.isAfter(Instant.now().plusSeconds(600))) {
            Flash.error(request, Messages.FUTURE_READING);
            response.sendRedirect(back);
            return;
        }
        PortalUser user = (PortalUser) request.getAttribute("user");
        try {
            Portal.api(request).recordReading(user.tokens().accessToken(), vehicleId, mileage, readAt);
            Flash.success(request, "Relevé de " + Formats.km(mileage) + " enregistré.");
        } catch (ApiException e) {
            Flash.error(request, Messages.forCode(e.code()));
        }
        response.sendRedirect(back);
    }

    /** "45 210", "45210" or "45 210 km" are the same reading. */
    static Integer mileage(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[\\s\\u00a0\\u202f.]|km", "");
        if (!digits.matches("\\d{1,7}")) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    /** The date and time typed in the form (datetime-local, Paris time). */
    static Instant readAt(String value) {
        try {
            return value == null || value.isBlank() ? Instant.now()
                    : LocalDateTime.parse(value).atZone(Formats.PARIS).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
