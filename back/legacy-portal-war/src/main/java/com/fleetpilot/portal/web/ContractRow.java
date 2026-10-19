package com.fleetpilot.portal.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.portal.api.Contract;

/** A contract ready to be shown: every value already written the French way, so the pages only lay it out. */
public record ContractRow(UUID id, String contractNumber, UUID vehicleId, String registration, String vehicle,
        String startDate, String endDate, String remaining, boolean returnSoon, String contractualMileage,
        String projectedMileage, String projectedOverrun, boolean overrunRisk, String monthlyRent, String services,
        String duration) {

    static ContractRow of(Contract c) {
        List<String> services = new ArrayList<>();
        if (c.maintenanceIncluded()) {
            services.add("Entretien");
        }
        if (c.tyresIncluded()) {
            services.add("Pneus");
        }
        if (c.replacementVehicle()) {
            services.add("Véhicule de remplacement");
        }
        String remaining;
        if (c.daysRemaining() == null) {
            remaining = "";
        } else if (c.daysRemaining() < 0) {
            remaining = "Échu";
        } else if (c.daysRemaining() == 0) {
            remaining = "Aujourd'hui";
        } else {
            remaining = c.daysRemaining() + (c.daysRemaining() == 1 ? " jour" : " jours");
        }
        return new ContractRow(c.id(), c.contractNumber(), c.vehicleId(), c.registration(), c.make() + " " + c.model(),
                Formats.date(c.startDate()), Formats.date(c.endDate()), remaining,
                c.endsWithin(ContractList.RETURN_HORIZON_DAYS), Formats.km(c.contractualMileage()),
                c.projectedMileage() == null ? "" : Formats.km(c.projectedMileage()),
                c.projectedOverrunKm() == null || c.projectedOverrunKm() <= 0 ? "" : "+" + Formats.km(c.projectedOverrunKm()),
                c.overrunRisk(), c.monthlyRent() == null ? "" : Formats.money(c.monthlyRent().doubleValue()),
                services.isEmpty() ? "Aucune" : String.join(", ", services), c.durationMonths() + " mois");
    }
}
