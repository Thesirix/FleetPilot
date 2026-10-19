package com.fleetpilot.portal.web;

import java.io.IOException;
import java.util.List;

import com.fleetpilot.portal.api.Contract;
import com.fleetpilot.portal.api.FleetApi;
import com.fleetpilot.portal.auth.PortalUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Home page: the active contracts of the lessor. */
@WebServlet({"", "/contracts"})
public class ContractsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        PortalUser user = (PortalUser) request.getAttribute("user");
        List<Contract> contracts;
        try {
            contracts = Portal.api(request).activeContracts(user.tokens().accessToken());
        } catch (FleetApi.ApiException e) {
            Portal.error(request, response, HttpServletResponse.SC_BAD_GATEWAY, Messages.forCode(e.code()));
            return;
        }
        request.setAttribute("list", ContractList.of(contracts, request.getParameter("filter"),
                request.getParameter("q"), page(request.getParameter("page"))));
        request.setAttribute("flash", Flash.take(request));
        Portal.render(request, response, "contracts");
    }

    static int page(String value) {
        try {
            return value == null ? 1 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
