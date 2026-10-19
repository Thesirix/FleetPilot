package com.fleetpilot.portal.web;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.portal.api.Contract;
import com.fleetpilot.portal.api.FleetApi;
import com.fleetpilot.portal.api.FleetApi.ApiException;
import com.fleetpilot.portal.api.Reading;
import com.fleetpilot.portal.auth.PortalUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** A contract: its terms, the projection of the mileage, the readings of the vehicle and the form of a new reading. */
@WebServlet("/contract")
public class ContractServlet extends HttpServlet {

    /** A reading as shown: date, mileage, who made it. */
    public record ReadingRow(String readAt, String mileage, String source) {
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        UUID id = Ids.parse(request.getParameter("id"));
        if (id == null) {
            Portal.error(request, response, HttpServletResponse.SC_NOT_FOUND, Messages.NOT_FOUND);
            return;
        }
        PortalUser user = (PortalUser) request.getAttribute("user");
        FleetApi api = Portal.api(request);
        Contract contract;
        List<Reading> readings;
        try {
            contract = api.contract(user.tokens().accessToken(), id);
            readings = api.readings(user.tokens().accessToken(), contract.vehicleId());
        } catch (ApiException e) {
            int status = e.status() == 404 ? HttpServletResponse.SC_NOT_FOUND : HttpServletResponse.SC_BAD_GATEWAY;
            Portal.error(request, response, status, Messages.forCode(e.code()));
            return;
        }
        request.setAttribute("contract", ContractRow.of(contract));
        request.setAttribute("readings", readings.stream()
                .map(r -> new ReadingRow(Formats.dateTime(r.readAt()), Formats.km(r.mileage()), Messages.source(r.source())))
                .toList());
        request.setAttribute("lastMileage",
                Formats.km(readings.isEmpty() ? contract.startMileage() : readings.get(0).mileage()));
        request.setAttribute("now", LocalDateTime.now(Formats.PARIS).truncatedTo(ChronoUnit.MINUTES));
        request.setAttribute("flash", Flash.take(request));
        Portal.render(request, response, "contract");
    }
}
