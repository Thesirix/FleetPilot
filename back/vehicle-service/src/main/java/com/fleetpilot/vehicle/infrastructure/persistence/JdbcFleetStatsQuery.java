package com.fleetpilot.vehicle.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.vehicle.application.FleetStatsQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard indicators computed with plain SQL aggregates: one round trip per indicator instead of loading
 * thousands of entities. The urgency and overrun rules mirror the ones of the domain model.
 *
 * <p>Every query is a compile-time constant: only values travel as bind parameters, never as SQL text.
 */
@Component
@Transactional(readOnly = true)
public class JdbcFleetStatsQuery implements FleetStatsQuery {

    /** Restricts a query to one company when the :company parameter is not null. */
    private static final String COMPANY = " (cast(:company as uuid) is null or v.company_id = cast(:company as uuid)) ";
    private static final String TODAY = "cast(:today as date)";
    private static final String IN_FLEET = " v.status <> 'DISPOSED' ";
    private static final String GROUP = " group by k order by n desc, k";

    private static final String BY_STATUS = "select v.status as k, count(*) as n from vehicle v where" + COMPANY + GROUP;
    private static final String BY_CATEGORY = "select v.category as k, count(*) as n from vehicle v where" + COMPANY
            + "and" + IN_FLEET + GROUP;
    private static final String BY_ENERGY = "select v.energy as k, count(*) as n from vehicle v where" + COMPANY
            + "and" + IN_FLEET + GROUP;
    private static final String BY_CRIT_AIR = "select cast(v.crit_air as varchar) as k, count(*) as n from vehicle v where"
            + COMPANY + "and" + IN_FLEET + GROUP;

    private static final String AVERAGES = "select coalesce(avg(extract(year from age(" + TODAY + ", v.first_registration_date)) * 12"
            + " + extract(month from age(" + TODAY + ", v.first_registration_date))), 0) as age,"
            + " coalesce(avg(v.co2_g_km), 0) as co2 from vehicle v where" + COMPANY + "and" + IN_FLEET;

    private static final String ACTIVE_DRIVERS = "select count(*) from driver v where v.active and" + COMPANY;
    private static final String ASSIGNED = "select count(*) from assignment a join vehicle v on v.id = a.vehicle_id"
            + " where a.end_date is null and" + COMPANY;

    // coalesce: a null due date or mileage must count as "not reached", never as unknown (SQL three-valued logic)
    private static final String OVERDUE = " (coalesce(d.due_date < " + TODAY + ", false)"
            + " or coalesce(v.mileage >= d.due_mileage, false)) ";
    private static final String DUE_SOON = " (coalesce(d.due_date <= " + TODAY + " + 30, false)"
            + " or coalesce(d.due_mileage - v.mileage <= 1500, false)) ";
    private static final String OPEN_DEADLINES = "select count(*) from deadline d join vehicle v on v.id = d.vehicle_id"
            + " where d.status = 'OPEN' and" + IN_FLEET + "and" + COMPANY;
    private static final String OVERDUE_DEADLINES = OPEN_DEADLINES + "and" + OVERDUE;
    private static final String DUE_SOON_DEADLINES = OPEN_DEADLINES + "and not" + OVERDUE + "and" + DUE_SOON;

    private static final String ACTIVE_CONTRACTS = " from lease_contract c join vehicle v on v.id = c.vehicle_id"
            + " where c.status = 'ACTIVE' and" + COMPANY;
    private static final String CONTRACTS_ENDING = "select count(*)" + ACTIVE_CONTRACTS + "and c.end_date <= " + TODAY + " + 90";
    private static final String CONTRACTS_OVERRUN = "select count(*)" + ACTIVE_CONTRACTS
            + "and (v.mileage - c.start_mileage)::numeric"
            + " / greatest(1, least(" + TODAY + " - c.start_date, c.end_date - c.start_date))"
            + " * (c.end_date - c.start_date) > c.contractual_mileage * 1.05";
    private static final String MONTHLY_RENT = "select coalesce(sum(c.monthly_rent), 0)" + ACTIVE_CONTRACTS;

    private static final String INCIDENTS = "select count(*) from incident i join vehicle v on v.id = i.vehicle_id where"
            + COMPANY;
    private static final String OPEN_INCIDENTS = INCIDENTS + "and i.status not in ('CLOSED', 'REJECTED')";
    private static final String RECENT_INCIDENTS = INCIDENTS + "and i.occurred_at >= " + TODAY + " - interval '12 months'";

    private final JdbcClient jdbc;

    public JdbcFleetStatsQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FleetSummary summary(UUID companyId, LocalDate today) {
        Map<String, Long> byStatus = countBy(BY_STATUS, companyId);
        long inFleet = byStatus.entrySet().stream().filter(e -> !"DISPOSED".equals(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        Map<String, Object> averages = jdbc.sql(AVERAGES).param("company", companyId).param("today", today)
                .query().singleRow();
        BigDecimal rentSum = jdbc.sql(MONTHLY_RENT).param("company", companyId).query(BigDecimal.class).single();
        BigDecimal rent = rentSum == null ? BigDecimal.ZERO : rentSum.setScale(2, RoundingMode.HALF_UP);

        return new FleetSummary(companyId, today, inFleet, byStatus,
                countBy(BY_CATEGORY, companyId), countBy(BY_ENERGY, companyId), countBy(BY_CRIT_AIR, companyId),
                round(averages.get("age")), round(averages.get("co2")),
                count(ACTIVE_DRIVERS, companyId, today), count(ASSIGNED, companyId, today),
                count(OVERDUE_DEADLINES, companyId, today), count(DUE_SOON_DEADLINES, companyId, today),
                count(CONTRACTS_ENDING, companyId, today), count(CONTRACTS_OVERRUN, companyId, today),
                rent,
                count(OPEN_INCIDENTS, companyId, today), count(RECENT_INCIDENTS, companyId, today));
    }

    private Map<String, Long> countBy(String sql, UUID companyId) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.sql(sql).param("company", companyId)
                .query((rs, row) -> result.put(rs.getString("k"), rs.getLong("n")))
                .list();
        return result;
    }

    private long count(String sql, UUID companyId, LocalDate today) {
        JdbcClient.StatementSpec statement = jdbc.sql(sql).param("company", companyId);
        if (sql.contains(":today")) {
            statement = statement.param("today", today);
        }
        Long value = statement.query(Long.class).single();
        return value == null ? 0 : value;
    }

    private static double round(Object value) {
        return value == null ? 0 : new BigDecimal(value.toString()).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
