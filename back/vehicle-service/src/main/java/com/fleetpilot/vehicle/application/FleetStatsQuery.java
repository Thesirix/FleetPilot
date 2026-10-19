package com.fleetpilot.vehicle.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Read-side query computing the fleet indicators of the dashboard in a few SQL aggregates. */
public interface FleetStatsQuery {

    /**
     * Indicators of the fleet of one company, or of every company when {@code companyId} is null.
     *
     * @param inFleet               vehicles not disposed
     * @param byStatus              vehicles per status
     * @param byCategory            vehicles in the fleet per category (CAR, VAN...)
     * @param byEnergy              vehicles in the fleet per energy
     * @param byCritAir             vehicles in the fleet per Crit'Air sticker (0 is E)
     * @param averageAgeMonths      average age of the vehicles in the fleet
     * @param averageCo2GramsPerKm  average CO2 emissions of the vehicles in the fleet
     * @param activeDrivers         active drivers
     * @param assignedVehicles      vehicles with a current driver
     * @param overdueDeadlines      open deadlines past their date or mileage
     * @param dueSoonDeadlines      open deadlines within 30 days or 1,500 km
     * @param contractsEndingSoon   active contracts ending within 90 days
     * @param contractsOverrunRisk  active contracts heading for a mileage overrun above 5 %
     * @param monthlyRentTotal      sum of the monthly rents of the active contracts
     * @param openIncidents         incidents not closed nor rejected
     * @param incidentsLast12Months incidents that occurred over the last 12 months
     */
    record FleetSummary(UUID companyId, LocalDate asOf, long inFleet, Map<String, Long> byStatus,
            Map<String, Long> byCategory, Map<String, Long> byEnergy, Map<String, Long> byCritAir,
            double averageAgeMonths, double averageCo2GramsPerKm, long activeDrivers, long assignedVehicles,
            long overdueDeadlines, long dueSoonDeadlines, long contractsEndingSoon, long contractsOverrunRisk,
            BigDecimal monthlyRentTotal, long openIncidents, long incidentsLast12Months) {
    }

    FleetSummary summary(UUID companyId, LocalDate today);
}
