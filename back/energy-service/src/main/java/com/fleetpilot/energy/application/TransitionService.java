package com.fleetpilot.energy.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.energy.application.EnergyViews.AssessmentView;
import com.fleetpilot.energy.application.EnergyViews.EvModelView;
import com.fleetpilot.energy.application.EnergyViews.Plan;
import com.fleetpilot.energy.application.EnergyViews.PlanQuarter;
import com.fleetpilot.energy.application.EnergyViews.QuotaYear;
import com.fleetpilot.energy.application.EnergyViews.Summary;
import com.fleetpilot.energy.application.Sources.FleetData;
import com.fleetpilot.energy.application.Sources.UsageData;
import com.fleetpilot.energy.domain.Transition.Assessment;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.Prices;
import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;
import com.fleetpilot.energy.domain.Transition.ZfeRule;
import com.fleetpilot.energy.domain.TransitionAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The energy transition of the fleet: every vehicle assessed from its real usage (recomputed every night), the fleet
 * at a glance, and the greening plan, quarter by quarter, with the quotas of the law.
 */
@Service
public class TransitionService {

    /** Days of usage the assessment is based on. */
    public static final int USAGE_DAYS = 90;
    /** Horizon of the plan. */
    static final int PLAN_LAST_YEAR = 2028;
    private static final Logger LOG = LoggerFactory.getLogger(TransitionService.class);
    private static final UUID NO_COMPANY = new UUID(0, 0);
    private static final Set<Eligibility> SWITCHABLE = Set.of(Eligibility.ELIGIBLE, Eligibility.ELIGIBLE_WITH_CHARGING);

    private final FleetData fleet;
    private final UsageData usage;
    private final AssessmentRepository assessments;
    private final Prices prices;
    private final Clock clock;

    public TransitionService(FleetData fleet, UsageData usage, AssessmentRepository assessments, Prices prices,
            Clock clock) {
        this.fleet = fleet;
        this.usage = usage;
        this.assessments = assessments;
        this.prices = prices;
        this.clock = clock;
    }

    /** Assesses every vehicle again from the fleet and the usage of the last 90 days. */
    public int refresh() {
        long start = System.nanoTime();
        List<VehicleFacts> vehicles = fleet.vehicles();
        Map<UUID, Usage> usages = usage.usage(USAGE_DAYS);
        TransitionAnalysis analysis = new TransitionAnalysis(assessments.catalog(), assessments.zones(), prices,
                LocalDate.now(clock));
        List<Assessment> results = vehicles.stream().map(v -> analysis.assess(v, usages.get(v.id()))).toList();
        assessments.replaceAll(results, clock.instant());
        LOG.info("Energy transition assessed: {} vehicles ({} with measured usage) in {} ms", results.size(),
                results.stream().filter(a -> "TELEMETRY".equals(a.dataSource())).count(),
                (System.nanoTime() - start) / 1_000_000);
        return results.size();
    }

    public Page<AssessmentView> search(UUID companyId, Eligibility status, String category, String q, String sort,
            Pageable pageable) {
        return assessments.search(scope(companyId), status, category, q, sort, pageable);
    }

    public AssessmentView vehicle(UUID vehicleId) {
        AssessmentView view = assessments.find(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle assessment", vehicleId));
        UUID restricted = restrictedCompany();
        if (restricted != null && !restricted.equals(view.companyId())) {
            throw new ResourceNotFoundException("Vehicle assessment", vehicleId);
        }
        return view;
    }

    public List<EvModelView> catalog() {
        return assessments.catalog().stream().map(m -> new EvModelView(m.code(), m.name(), m.category(), m.segment(),
                m.batteryKwh(), m.wltpRangeKm(), m.realisticRangeKm(), m.consumptionKwh100km(), m.fastChargeKw(),
                m.monthlyRent(), m.make(), m.model())).toList();
    }

    public List<EnergyViews.ZoneView> zones() {
        return assessments.zones().stream()
                .map(z -> new EnergyViews.ZoneView(z.city(), z.zone(), z.maxCritAir(), z.effectiveFrom(), z.status()))
                .toList();
    }

    /** Adds or replaces an electric model, then assesses the fleet again with the new catalogue. */
    public int saveModel(EvModel model) {
        assessments.saveModel(model);
        return refresh();
    }

    /** A model proposed to vehicles is removed with them assessed again first, so none points to it any more. */
    public int deleteModel(String code) {
        if (assessments.catalog().stream().noneMatch(m -> m.code().equals(code))) {
            throw new ResourceNotFoundException("Electric model", code);
        }
        if (assessments.catalog().size() <= 1) {
            throw new BusinessRuleException("CATALOG_EMPTY", "The catalogue needs at least one electric model");
        }
        assessments.deleteModel(code);
        return refresh();
    }

    public int saveZone(ZfeRule zone) {
        assessments.saveZone(zone);
        return refresh();
    }

    public int deleteZone(String city) {
        if (assessments.zones().stream().noneMatch(z -> z.city().equalsIgnoreCase(city))) {
            throw new ResourceNotFoundException("Low emission zone", city);
        }
        assessments.deleteZone(city);
        return refresh();
    }

    public Summary summary(UUID companyId) {
        List<AssessmentView> all = assessments.all(scope(companyId));
        Map<Eligibility, Long> counts = new EnumMap<>(Eligibility.class);
        all.forEach(a -> counts.merge(a.status(), 1L, Long::sum));
        int zero = count(counts, Eligibility.ZERO_EMISSION);
        int switchable = count(counts, Eligibility.ELIGIBLE) + count(counts, Eligibility.ELIGIBLE_WITH_CHARGING);
        BigDecimal saving = all.stream().filter(a -> SWITCHABLE.contains(a.status()) && a.annualSaving() != null)
                .map(AssessmentView::annualSaving).filter(s -> s.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        long co2Current = all.stream().filter(a -> a.co2CurrentKg() != null).mapToLong(AssessmentView::co2CurrentKg).sum();
        long co2Avoidable = all.stream().filter(a -> SWITCHABLE.contains(a.status()))
                .mapToLong(a -> a.co2CurrentKg() - (a.co2EvKg() == null ? 0 : a.co2EvKg())).sum();
        LocalDate today = LocalDate.now(clock);
        Instant computedAt = all.stream().map(AssessmentView::computedAt).max(Instant::compareTo).orElse(null);
        return new Summary(all.size(), zero, count(counts, Eligibility.ELIGIBLE),
                count(counts, Eligibility.ELIGIBLE_WITH_CHARGING), count(counts, Eligibility.NOT_YET),
                count(counts, Eligibility.TO_CONFIRM), count(counts, Eligibility.NOT_APPLICABLE),
                percent(zero, all.size()), percent(zero + switchable, all.size()), saving.setScale(0, RoundingMode.HALF_UP),
                tonnes(co2Current), tonnes(co2Avoidable),
                (int) all.stream().filter(a -> a.zfeZone() != null).count(),
                (int) all.stream().filter(a -> a.zfeBannedFrom() != null && !a.zfeBannedFrom().isAfter(today)).count(),
                computedAt);
    }

    /**
     * The greening plan: each eligible vehicle goes electric when its lease ends (or when an owned vehicle reaches
     * its renewal age), quarter by quarter until the end of 2028; and, year by year, the share of low emission
     * vehicles among the renewals against the quota of the loi LOM (20 % until 2026, 40 % from 2027, 70 % from 2030).
     */
    public Plan plan(UUID companyId) {
        List<AssessmentView> all = assessments.all(scope(companyId));
        LocalDate today = LocalDate.now(clock);
        LocalDate quarter = today.withDayOfMonth(1).withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1);
        int zero = (int) all.stream().filter(a -> a.status() == Eligibility.ZERO_EMISSION).count();
        List<PlanQuarter> quarters = new ArrayList<>();
        while (quarter.getYear() <= PLAN_LAST_YEAR) {
            LocalDate start = quarter;
            LocalDate end = quarter.plusMonths(3);
            List<AssessmentView> switched = all.stream().filter(a -> SWITCHABLE.contains(a.status()))
                    .filter(a -> a.renewalDate() != null && !a.renewalDate().isBefore(start)
                            && a.renewalDate().isBefore(end))
                    .toList();
            zero += switched.size();
            quarters.add(new PlanQuarter("T%d %d".formatted(start.get(IsoFields.QUARTER_OF_YEAR), start.getYear()),
                    start, switched.size(),
                    switched.stream().map(a -> a.annualSaving() == null ? BigDecimal.ZERO : a.annualSaving())
                            .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(0, RoundingMode.HALF_UP),
                    tonnes(switched.stream().mapToLong(a -> a.co2CurrentKg() - (a.co2EvKg() == null ? 0 : a.co2EvKg()))
                            .sum()),
                    percent(zero, all.size())));
            quarter = end;
        }
        Map<Integer, List<AssessmentView>> byYear = all.stream()
                .filter(a -> a.status() != Eligibility.NOT_APPLICABLE && a.renewalDate() != null)
                .collect(Collectors.groupingBy(a -> a.renewalDate().getYear()));
        List<QuotaYear> quotas = new ArrayList<>();
        for (int year = today.getYear(); year <= PLAN_LAST_YEAR; year++) {
            List<AssessmentView> renewals = byYear.getOrDefault(year, List.of());
            int low = (int) renewals.stream().filter(a -> a.status() == Eligibility.ZERO_EMISSION
                    || SWITCHABLE.contains(a.status())).count();
            int objective = year <= 2026 ? 20 : year <= 2029 ? 40 : 70;
            BigDecimal share = percent(low, renewals.size());
            quotas.add(new QuotaYear(year, renewals.size(), low, share, objective,
                    share.compareTo(BigDecimal.valueOf(objective)) >= 0));
        }
        return new Plan(quarters, quotas);
    }

    /** FleetPilot staff see every company; a client manager only theirs, whatever they ask. */
    private UUID scope(UUID requested) {
        UUID restricted = restrictedCompany();
        return restricted != null ? restricted : requested;
    }

    private static UUID restrictedCompany() {
        if (!CurrentUser.isAuthenticated() || CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER)) {
            return null;
        }
        return CurrentUser.companyId().orElse(NO_COMPANY);
    }

    private static int count(Map<Eligibility, Long> counts, Eligibility status) {
        return counts.getOrDefault(status, 0L).intValue();
    }

    static BigDecimal percent(long part, long total) {
        return total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal tonnes(long kg) {
        return BigDecimal.valueOf(kg / 1000.0).setScale(1, RoundingMode.HALF_UP);
    }
}
