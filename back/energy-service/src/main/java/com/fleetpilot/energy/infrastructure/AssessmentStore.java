package com.fleetpilot.energy.infrastructure;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.energy.application.AssessmentRepository;
import com.fleetpilot.energy.application.EnergyViews.AssessmentView;
import com.fleetpilot.energy.application.EnergyViews.EvModelView;
import com.fleetpilot.energy.domain.Transition.Assessment;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.ZfeRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue, the zones and the assessments, in SQL. */
@Component
public class AssessmentStore implements AssessmentRepository {

    private static final String SELECT = """
            select a.*, m.make as m_make, m.model as m_model, m.category as m_category, m.segment as m_segment,
                   m.battery_kwh, m.wltp_range_km, m.consumption_kwh_100km, m.fast_charge_kw, m.monthly_rent
            from assessment a left join ev_model m on m.code = a.ev_model_code
            """;
    private static final String FILTER = """
             where (cast(:company as uuid) is null or a.company_id = :company)
               and (cast(:status as varchar) is null or a.status = :status)
               and (cast(:category as varchar) is null or a.category = :category)
               and (cast(:q as varchar) is null or a.registration ilike '%' || :q || '%'
                    or a.model ilike '%' || :q || '%' or a.site_city ilike '%' || :q || '%')
            """;
    private static final Map<String, String> SORTS = Map.of(
            // the saving only means something for a vehicle that can switch: the others come last
            "annualSaving", "case when a.status in ('ELIGIBLE', 'ELIGIBLE_WITH_CHARGING', 'TO_CONFIRM') "
                    + "then a.annual_saving end desc nulls last", "renewalDate", "a.renewal_date asc nulls last",
            "annualKm", "a.annual_km desc", "registration", "a.registration");

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public AssessmentStore(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    @Override
    public List<EvModel> catalog() {
        return jdbc.sql("select * from ev_model order by category, monthly_rent")
                .query((rs, row) -> new EvModel(rs.getString("code"), rs.getString("make"), rs.getString("model"),
                        rs.getString("category"), rs.getString("segment"), rs.getBigDecimal("battery_kwh"),
                        rs.getInt("wltp_range_km"), rs.getBigDecimal("consumption_kwh_100km"),
                        rs.getInt("fast_charge_kw"), rs.getBigDecimal("monthly_rent")))
                .list();
    }

    @Override
    public List<ZfeRule> zones() {
        return jdbc.sql("select * from zfe_rule order by city")
                .query((rs, row) -> new ZfeRule(rs.getString("city"), rs.getString("zone"), rs.getInt("max_crit_air"),
                        rs.getDate("effective_from").toLocalDate(), rs.getString("status")))
                .list();
    }

    @Override
    @Transactional
    public void saveModel(EvModel m) {
        jdbc.sql("""
                insert into ev_model (code, make, model, category, segment, battery_kwh, wltp_range_km,
                                      consumption_kwh_100km, fast_charge_kw, monthly_rent)
                values (:code, :make, :model, :category, :segment, :battery, :range, :consumption, :charge, :rent)
                on conflict (code) do update set make = excluded.make, model = excluded.model,
                    category = excluded.category, segment = excluded.segment, battery_kwh = excluded.battery_kwh,
                    wltp_range_km = excluded.wltp_range_km, consumption_kwh_100km = excluded.consumption_kwh_100km,
                    fast_charge_kw = excluded.fast_charge_kw, monthly_rent = excluded.monthly_rent""")
                .param("code", m.code()).param("make", m.make()).param("model", m.model())
                .param("category", m.category()).param("segment", m.segment()).param("battery", m.batteryKwh())
                .param("range", m.wltpRangeKm()).param("consumption", m.consumptionKwh100km())
                .param("charge", m.fastChargeKw()).param("rent", m.monthlyRent())
                .update();
    }

    @Override
    @Transactional
    public void deleteModel(String code) {
        jdbc.sql("update assessment set ev_model_code = null where ev_model_code = :code").param("code", code).update();
        jdbc.sql("delete from ev_model where code = :code").param("code", code).update();
    }

    @Override
    @Transactional
    public void saveZone(ZfeRule z) {
        jdbc.sql("""
                insert into zfe_rule (city, zone, max_crit_air, effective_from, status)
                values (:city, :zone, :max, :from, :status)
                on conflict (city) do update set zone = excluded.zone, max_crit_air = excluded.max_crit_air,
                    effective_from = excluded.effective_from, status = excluded.status""")
                .param("city", z.city()).param("zone", z.zone()).param("max", z.maxCritAir())
                .param("from", z.effectiveFrom()).param("status", z.status())
                .update();
    }

    @Override
    @Transactional
    public void deleteZone(String city) {
        jdbc.sql("delete from zfe_rule where lower(city) = lower(:city)").param("city", city).update();
    }

    /** The assessments of the whole fleet are replaced at once: the pages never show half of a computation. */
    @Override
    @Transactional
    public void replaceAll(List<Assessment> assessments, Instant at) {
        jdbc.sql("delete from assessment").update();
        template.batchUpdate("""
                insert into assessment (vehicle_id, company_id, company_name, registration, make, model, category,
                    energy, crit_air, co2_g_km, site_city, status, data_source, reason, annual_km, active_days,
                    p95_daily_km, longest_day_km, days_beyond_range, required_range_km, ev_model_code,
                    realistic_range_km, current_annual_cost, ev_annual_cost, annual_saving, co2_current_kg, co2_ev_kg,
                    renewal_date, zfe_zone, zfe_banned_from, computed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                assessments.stream().map(a -> new Object[] {
                    a.vehicle().id(), a.vehicle().companyId(), a.vehicle().companyName(), a.vehicle().registration(),
                    a.vehicle().make(), a.vehicle().model(), a.vehicle().category(), a.vehicle().energy(),
                    a.vehicle().critAir(), a.vehicle().co2GramsPerKm(), a.vehicle().siteCity(), a.status().name(),
                    a.dataSource(), a.reason(), a.annualKm(), a.activeDays(), a.p95DailyKm(), a.longestDayKm(),
                    a.daysBeyondRange(), a.requiredRangeKm(), a.evModel() == null ? null : a.evModel().code(),
                    a.evModel() == null ? null : a.evModel().realisticRangeKm(), a.currentAnnualCost(),
                    a.evAnnualCost(), a.annualSaving(), a.co2CurrentKg(), a.co2EvKg(), date(a.renewalDate()),
                    a.zfeZone(), date(a.zfeBannedFrom()), Timestamp.from(at)}).toList());
    }

    @Override
    public Page<AssessmentView> search(UUID companyId, Eligibility status, String category, String q, String sort,
            Pageable pageable) {
        String statusName = status == null ? null : status.name();
        String text = q == null || q.isBlank() ? null : q.trim();
        long total = jdbc.sql("select count(*) from assessment a" + FILTER).param("company", companyId)
                .param("status", statusName).param("category", category).param("q", text)
                .query(Long.class).single();
        List<AssessmentView> content = jdbc.sql(SELECT + FILTER + " order by "
                        + SORTS.getOrDefault(sort == null ? "" : sort, SORTS.get("annualSaving"))
                        + ", a.registration limit :limit offset :offset")
                .param("company", companyId).param("status", statusName).param("category", category)
                .param("q", text).param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, row) -> view(rs)).list();
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Optional<AssessmentView> find(UUID vehicleId) {
        return jdbc.sql(SELECT + " where a.vehicle_id = :id").param("id", vehicleId).query((rs, row) -> view(rs))
                .optional();
    }

    /** All the assessments of a company (or of the fleet), for the summary and the plan. */
    @Override
    public List<AssessmentView> all(UUID companyId) {
        return jdbc.sql(SELECT + " where (cast(:company as uuid) is null or a.company_id = :company)")
                .param("company", companyId).query((rs, row) -> view(rs)).list();
    }

    private static AssessmentView view(ResultSet rs) throws SQLException {
        EvModelView model = rs.getString("ev_model_code") == null ? null : new EvModelView(
                rs.getString("ev_model_code"), rs.getString("m_make") + " " + rs.getString("m_model"),
                rs.getString("m_category"), rs.getString("m_segment"), rs.getBigDecimal("battery_kwh"),
                rs.getInt("wltp_range_km"), rs.getInt("realistic_range_km"), rs.getBigDecimal("consumption_kwh_100km"),
                rs.getInt("fast_charge_kw"), rs.getBigDecimal("monthly_rent"), rs.getString("m_make"),
                rs.getString("m_model"));
        return new AssessmentView(rs.getObject("vehicle_id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getString("company_name"), rs.getString("registration"), rs.getString("make"),
                rs.getString("model"), rs.getString("category"), rs.getString("energy"),
                (Integer) rs.getObject("crit_air"), (Integer) rs.getObject("co2_g_km"), rs.getString("site_city"),
                Eligibility.valueOf(rs.getString("status")), rs.getString("data_source"), rs.getString("reason"),
                rs.getInt("annual_km"), (Integer) rs.getObject("active_days"), rs.getBigDecimal("p95_daily_km"),
                rs.getBigDecimal("longest_day_km"), (Integer) rs.getObject("days_beyond_range"),
                (Integer) rs.getObject("required_range_km"), model, rs.getBigDecimal("current_annual_cost"),
                rs.getBigDecimal("ev_annual_cost"), rs.getBigDecimal("annual_saving"),
                (Integer) rs.getObject("co2_current_kg"), (Integer) rs.getObject("co2_ev_kg"),
                localDate(rs.getDate("renewal_date")), rs.getString("zfe_zone"),
                localDate(rs.getDate("zfe_banned_from")), rs.getTimestamp("computed_at").toInstant());
    }

    private static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
