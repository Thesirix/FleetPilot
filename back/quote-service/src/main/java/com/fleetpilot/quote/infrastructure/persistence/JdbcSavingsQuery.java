package com.fleetpilot.quote.infrastructure.persistence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.quote.application.SavingsQuery;
import com.fleetpilot.quote.application.view.QuoteViews.FindingCount;
import com.fleetpilot.quote.application.view.QuoteViews.GarageRanking;
import com.fleetpilot.quote.application.view.QuoteViews.GarageStats;
import com.fleetpilot.quote.application.view.QuoteViews.MonthlySavings;
import com.fleetpilot.quote.application.view.QuoteViews.SavingsSummary;
import com.fleetpilot.quote.domain.model.Finding;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Savings indicators in a handful of SQL aggregates. "Decided" quotes are the accepted ones and those that went on to
 * completion and invoicing; the saving of a quote is its initial amount minus the accepted amount.
 */
@Component
@Transactional(readOnly = true)
public class JdbcSavingsQuery implements SavingsQuery {

    private static final String DECIDED = "('ACCEPTED', 'COMPLETED', 'INVOICED')";
    private static final int MONTHS = 12;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private record Totals(long quotes, long open, long toNegotiate, long toReview, long received, BigDecimal quoted,
            BigDecimal accepted, BigDecimal pending, long decided, long auto, long disputed) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    public JdbcSavingsQuery(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public SavingsSummary summary(UUID companyId) {
        String company = companyId == null ? "" : " and q.company_id = :company";
        Totals t = jdbc.sql("""
                select count(*) as quotes,
                       count(*) filter (where status in ('RECEIVED', 'IN_REVIEW', 'TO_NEGOTIATE')) as open,
                       count(*) filter (where status = 'TO_NEGOTIATE') as to_negotiate,
                       count(*) filter (where status = 'IN_REVIEW') as to_review,
                       count(*) filter (where status = 'RECEIVED') as received,
                       coalesce(sum(initial_amount) filter (where status in %1$s), 0) as quoted,
                       coalesce(sum(accepted_amount) filter (where status in %1$s), 0) as accepted,
                       coalesce(sum(overcharge) filter (where status in ('IN_REVIEW', 'TO_NEGOTIATE')), 0) as pending,
                       count(*) filter (where status in %1$s) as decided,
                       count(*) filter (where status in %1$s and auto_approved) as auto,
                       count(*) filter (where invoice_disputed) as disputed
                from quote q where true%2$s""".formatted(DECIDED, company))
                .params(params(companyId))
                .query((rs, row) -> new Totals(rs.getLong("quotes"), rs.getLong("open"), rs.getLong("to_negotiate"),
                        rs.getLong("to_review"), rs.getLong("received"), rs.getBigDecimal("quoted"),
                        rs.getBigDecimal("accepted"), rs.getBigDecimal("pending"), rs.getLong("decided"),
                        rs.getLong("auto"), rs.getLong("disputed")))
                .single();
        BigDecimal savings = t.quoted().subtract(t.accepted());
        return new SavingsSummary(t.quotes(), t.open(), t.toNegotiate(), t.toReview(), t.received(), t.quoted(),
                t.accepted(), savings, percent(savings, t.quoted()), t.pending(),
                percent(BigDecimal.valueOf(t.auto()), BigDecimal.valueOf(t.decided())), t.disputed(),
                months(companyId, company), findings(companyId, company), garagesToWatch(companyId, company));
    }

    @Override
    public GarageStats garageStats(UUID garageId, UUID companyId) {
        String company = companyId == null ? "" : " and company_id = :company";
        return jdbc.sql("""
                select count(*) as quotes,
                       count(*) filter (where status in ('RECEIVED', 'IN_REVIEW', 'TO_NEGOTIATE')) as open,
                       count(*) filter (where status in %1$s) as accepted,
                       count(*) filter (where status = 'REJECTED') as rejected,
                       coalesce(sum(initial_amount), 0) as quoted,
                       coalesce(sum(overcharge), 0) as overcharge,
                       coalesce(sum(initial_amount - accepted_amount) filter (where status in %1$s), 0) as savings,
                       avg(extract(epoch from decided_at - submitted_at) / 3600) filter (where decided_at is not null
                           and not auto_approved) as decision_hours,
                       count(*) filter (where invoice_disputed) as disputed,
                       max(submitted_at) as last_quote
                from quote where garage_id = :garage%2$s""".formatted(DECIDED, company))
                .params(params(companyId))
                .param("garage", garageId)
                .query((rs, row) -> {
                    BigDecimal quoted = rs.getBigDecimal("quoted");
                    BigDecimal overcharge = rs.getBigDecimal("overcharge");
                    BigDecimal hours = rs.getBigDecimal("decision_hours");
                    Timestamp last = rs.getTimestamp("last_quote");
                    return new GarageStats(rs.getLong("quotes"), rs.getLong("open"), rs.getLong("accepted"),
                            rs.getLong("rejected"), quoted, overcharge, percent(overcharge, quoted),
                            rs.getBigDecimal("savings"), hours == null ? null : hours.setScale(1, RoundingMode.HALF_UP),
                            rs.getLong("disputed"), last == null ? null : last.toInstant());
                })
                .single();
    }

    /** Last twelve months, empty months included, oldest first. */
    private List<MonthlySavings> months(UUID companyId, String company) {
        ZoneId zone = clock.getZone();
        YearMonth current = YearMonth.now(clock);
        LocalDate from = current.minusMonths(MONTHS - 1L).atDay(1);
        var rows = jdbc.sql("""
                select to_char(date_trunc('month', submitted_at at time zone :zone), 'YYYY-MM') as month,
                       count(*) as quotes,
                       coalesce(sum(initial_amount) filter (where status in %1$s), 0) as quoted,
                       coalesce(sum(accepted_amount) filter (where status in %1$s), 0) as accepted
                from quote q where submitted_at >= :from%2$s
                group by 1""".formatted(DECIDED, company))
                .params(params(companyId))
                .param("zone", zone.getId())
                .param("from", Timestamp.from(from.atStartOfDay(zone).toInstant()))
                .query((rs, row) -> new MonthlySavings(rs.getString("month"), rs.getLong("quotes"),
                        rs.getBigDecimal("quoted"), rs.getBigDecimal("accepted"),
                        rs.getBigDecimal("quoted").subtract(rs.getBigDecimal("accepted"))))
                .list();
        List<MonthlySavings> result = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            String month = current.minusMonths(i).toString();
            result.add(rows.stream().filter(r -> r.month().equals(month)).findFirst()
                    .orElse(new MonthlySavings(month, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return result;
    }

    private List<FindingCount> findings(UUID companyId, String company) {
        return jdbc.sql("""
                select l.finding, count(*) as lines,
                       coalesce(sum(greatest(l.amount - l.reference_amount, 0))
                           filter (where l.finding <> 'UNKNOWN_ITEM'), 0) as overcharge
                from quote_line l join quote q on q.id = l.quote_id
                where l.finding is not null%s
                group by l.finding order by overcharge desc""".formatted(company))
                .params(params(companyId))
                .query((rs, row) -> new FindingCount(Finding.valueOf(rs.getString("finding")), rs.getLong("lines"),
                        rs.getBigDecimal("overcharge")))
                .list();
    }

    /** Garages with at least five quotes, highest overcharge rate first. */
    private List<GarageRanking> garagesToWatch(UUID companyId, String company) {
        return jdbc.sql("""
                select g.id, g.name, g.city, count(*) as quotes,
                       sum(q.overcharge) * 100 / nullif(sum(q.initial_amount), 0) as rate,
                       coalesce(sum(q.initial_amount - q.accepted_amount) filter (where q.status in %s), 0) as savings
                from quote q join garage g on g.id = q.garage_id
                where true%s
                group by g.id, g.name, g.city
                having count(*) >= 5
                order by rate desc nulls last
                limit 8""".formatted(DECIDED, company))
                .params(params(companyId))
                .query((rs, row) -> new GarageRanking(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("city"), rs.getLong("quotes"), scale(rs, "rate"), rs.getBigDecimal("savings")))
                .list();
    }

    private static Map<String, Object> params(UUID companyId) {
        return companyId == null ? Map.of() : Map.of("company", companyId);
    }

    private static BigDecimal scale(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value.setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO : part.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP);
    }
}
