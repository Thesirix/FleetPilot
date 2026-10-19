package com.fleetpilot.portal.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import com.fleetpilot.portal.api.Contract;
import com.fleetpilot.portal.api.FleetApi;
import com.fleetpilot.portal.auth.PortalUser;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The contracts of the current filter as a CSV file, the way the lessors load them in their own tools: semicolons,
 * decimal comma, UTF-8 with a byte order mark so that Excel reads the accents. The oldest feature of v1, kept as is.
 */
@WebServlet("/contracts.csv")
public class ExportServlet extends HttpServlet {

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
        List<Contract> lines = ContractList.select(contracts, request.getParameter("filter"), request.getParameter("q"));
        String file = "contrats-" + user.lessor().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                + "-" + LocalDate.now(Formats.PARIS) + ".csv";
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + file + "\"");
        try (PrintWriter out = response.getWriter()) {
            out.write(csv(lines));
        }
    }

    static String csv(List<Contract> contracts) {
        StringBuilder csv = new StringBuilder("﻿");
        csv.append("Contrat;Immatriculation;Véhicule;Début;Fin;Jours restants;Km contractuels;Km départ;"
                + "Km projetés en fin de contrat;Dépassement projeté (km);Loyer mensuel (€);Entretien;Pneus;"
                + "Véhicule de remplacement\r\n");
        for (Contract c : contracts) {
            csv.append(String.join(";",
                    cell(c.contractNumber()),
                    cell(c.registration()),
                    cell(c.make() + " " + c.model()),
                    Formats.date(c.startDate()),
                    Formats.date(c.endDate()),
                    c.daysRemaining() == null ? "" : String.valueOf(c.daysRemaining()),
                    String.valueOf(c.contractualMileage()),
                    String.valueOf(c.startMileage()),
                    c.projectedMileage() == null ? "" : String.valueOf(c.projectedMileage()),
                    c.projectedOverrunKm() == null ? "" : String.valueOf(Math.max(0, c.projectedOverrunKm())),
                    c.monthlyRent() == null ? "" : c.monthlyRent().toPlainString().replace('.', ','),
                    yesNo(c.maintenanceIncluded()),
                    yesNo(c.tyresIncluded()),
                    yesNo(c.replacementVehicle())));
            csv.append("\r\n");
        }
        return csv.toString();
    }

    /** Text cells are quoted when they hold a separator, a quote or a line break; quotes are doubled. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        // A leading = + - @ would be read as a formula by a spreadsheet: neutralized
        String safe = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.matches(".*[;\"\\r\\n].*") ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    private static String yesNo(boolean value) {
        return value ? "oui" : "non";
    }
}
