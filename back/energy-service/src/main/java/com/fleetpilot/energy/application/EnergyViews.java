package com.fleetpilot.energy.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.energy.domain.Transition.Eligibility;

/** Read models of the energy transition API. */
public final class EnergyViews {

    private EnergyViews() {
    }

    public record EvModelView(String code, String name, String category, String segment, BigDecimal batteryKwh,
            int wltpRangeKm, int realisticRangeKm, BigDecimal consumptionKwh100km, int fastChargeKw,
            BigDecimal monthlyRent, String make, String model) {
    }

    /** A low emission zone of a city: the highest Crit'Air sticker allowed, from a date. */
    public record ZoneView(String city, String zone, int maxCritAir, java.time.LocalDate effectiveFrom, String status) {
    }

    /** The assessment of one vehicle. */
    public record AssessmentView(UUID vehicleId, UUID companyId, String companyName, String registration, String make,
            String model, String category, String energy, Integer critAir, Integer co2GramsPerKm, String siteCity,
            Eligibility status, String dataSource, String reason, int annualKm, Integer activeDays,
            BigDecimal p95DailyKm, BigDecimal longestDayKm, Integer daysBeyondRange, Integer requiredRangeKm,
            EvModelView evModel, BigDecimal currentAnnualCost, BigDecimal evAnnualCost, BigDecimal annualSaving,
            Integer co2CurrentKg, Integer co2EvKg, LocalDate renewalDate, String zfeZone, LocalDate zfeBannedFrom,
            Instant computedAt) {
    }

    /** The fleet (or one company) at a glance. */
    public record Summary(int vehicles, int zeroEmission, int eligible, int eligibleWithCharging, int notYet,
            int toConfirm, int notApplicable, BigDecimal zeroEmissionShare, BigDecimal potentialShare,
            BigDecimal annualSavingPotential, BigDecimal co2CurrentTonnes, BigDecimal co2AvoidableTonnes,
            int zfeExposed, int zfeBannedNow, Instant computedAt) {
    }

    /** What the plan changes in one quarter: the eligible vehicles whose lease ends then go electric. */
    public record PlanQuarter(String quarter, LocalDate start, int vehicles, BigDecimal annualSaving,
            BigDecimal co2AvoidedTonnes, BigDecimal zeroEmissionShareAfter) {
    }

    /**
     * Share of low emission vehicles among the renewals of a year, against the quota of the law (loi LOM, fleets of
     * more than 100 vehicles).
     */
    public record QuotaYear(int year, int renewals, int lowEmissionRenewals, BigDecimal share, int objective,
            boolean met) {
    }

    public record Plan(List<PlanQuarter> quarters, List<QuotaYear> quotas) {
    }
}
