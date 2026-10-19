package com.fleetpilot.energy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The vocabulary of the energy transition: inputs of the analysis and what it concludes for one vehicle. */
public final class Transition {

    private Transition() {
    }

    /** Where a vehicle stands on the way to zero emission. */
    public enum Eligibility {
        /** Already electric or hydrogen. */
        ZERO_EMISSION,
        /** An electric model covers every day driven over the last 90 days, charged at the depot. */
        ELIGIBLE,
        /** Covered on most days; on a few long days a fast charge on the way is needed. */
        ELIGIBLE_WITH_CHARGING,
        /** The days beyond the range are too frequent today. */
        NOT_YET,
        /** No connected box: estimated from the odometer, to be confirmed with real usage. */
        TO_CONFIRM,
        /** No electric equivalent in the catalogue (site machinery). */
        NOT_APPLICABLE
    }

    /** An electric model of the catalogue. */
    public record EvModel(String code, String make, String model, String category, String segment,
            BigDecimal batteryKwh, int wltpRangeKm, BigDecimal consumptionKwh100km, int fastChargeKw,
            BigDecimal monthlyRent) {

        /**
         * The range to count on all year: WLTP is measured at 23 degrees on a mixed cycle; in winter, on the
         * motorway, with a load, a fleet keeps 70 % of it.
         */
        public int realisticRangeKm() {
            return (int) Math.round(wltpRangeKm * TransitionAnalysis.REALISTIC_SHARE);
        }

        public String name() {
            return make + " " + model;
        }
    }

    /** A low emission zone: the oldest Crit'Air sticker still allowed in a city, from a date. */
    public record ZfeRule(String city, String zone, int maxCritAir, LocalDate effectiveFrom, String status) {
    }

    /** What the fleet module knows of a vehicle. */
    public record VehicleFacts(UUID id, UUID companyId, String companyName, String registration, String make,
            String model, String category, String energy, int critAir, int co2GramsPerKm, String siteCity,
            int mileage, LocalDate firstRegistration, String acquisition, LocalDate contractEnd,
            BigDecimal monthlyRent) {
    }

    /**
     * What the connected box recorded over the period observed.
     *
     * @param dailyKm the distance of each day driven
     */
    public record Usage(int observedDays, List<BigDecimal> dailyKm) {
    }

    /** Fuel and energy prices, emission factors. */
    public record Prices(BigDecimal diesel, BigDecimal petrol, BigDecimal lpg, BigDecimal cng,
            BigDecimal depotKwh, BigDecimal publicKwh, BigDecimal gridKgCo2PerKwh) {
    }

    /** The conclusion for one vehicle. */
    public record Assessment(VehicleFacts vehicle, Eligibility status, String dataSource, String reason,
            int annualKm, Integer activeDays, BigDecimal p95DailyKm, BigDecimal longestDayKm, Integer daysBeyondRange,
            Integer requiredRangeKm, EvModel evModel, BigDecimal currentAnnualCost, BigDecimal evAnnualCost,
            BigDecimal annualSaving, Integer co2CurrentKg, Integer co2EvKg, LocalDate renewalDate, String zfeZone,
            LocalDate zfeBannedFrom) {
    }
}
