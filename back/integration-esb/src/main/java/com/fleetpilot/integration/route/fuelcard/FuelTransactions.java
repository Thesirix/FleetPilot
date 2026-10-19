package com.fleetpilot.integration.route.fuelcard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The fuel card transactions received by the bus, in SQL. */
@Component
public class FuelTransactions {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public FuelTransactions(JdbcClient jdbc, JdbcTemplate template) {
        this.jdbc = jdbc;
        this.template = template;
    }

    public record FuelTransaction(UUID id, UUID exchangeId, String provider, String reference, String card,
            Instant occurredAt, String station, String city, String product, BigDecimal litres, BigDecimal unitPrice,
            BigDecimal amount, Integer odometer, String registration, UUID vehicleId, UUID companyId, String energy,
            String anomalies) {

        public List<String> anomalyCodes() {
            return anomalies == null ? List.of() : Arrays.asList(anomalies.split(","));
        }
    }

    /** The last fill of a vehicle before a file, to check the first fills of the file. */
    public record LastFill(Instant at, Integer odometer) {
    }

    /** Spending and anomalies over a period. */
    public record Summary(LocalDate from, LocalDate to, int transactions, BigDecimal amount, BigDecimal litres,
            BigDecimal averagePrice, int withAnomaly, BigDecimal amountWithAnomaly, Map<String, Integer> anomaliesByType,
            List<MonthSpending> months) {
    }

    public record MonthSpending(String month, BigDecimal amount, BigDecimal litres) {
    }

    public void save(List<FuelTransaction> transactions) {
        template.batchUpdate("""
                insert into fuel_transaction (id, exchange_id, provider, transaction_ref, card_number, occurred_at,
                    station, city, product, litres, unit_price, amount, odometer, registration, vehicle_id, company_id,
                    energy, anomalies)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (provider, transaction_ref) do nothing""",
                transactions.stream().map(t -> new Object[] {t.id(), t.exchangeId(), t.provider(), t.reference(),
                    t.card(), Timestamp.from(t.occurredAt()), t.station(), t.city(), t.product(), t.litres(),
                    t.unitPrice(), t.amount(), t.odometer(), t.registration(), t.vehicleId(), t.companyId(), t.energy(),
                    t.anomalies()}).toList());
    }

    public Set<String> knownReferences(String provider, Collection<String> references) {
        if (references.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(jdbc.sql("""
                select transaction_ref from fuel_transaction where provider = :provider
                and transaction_ref in (:refs)""")
                .param("provider", provider).param("refs", references).query(String.class).list());
    }

    public Map<String, LastFill> lastFills(Collection<String> registrations, Instant before) {
        Map<String, LastFill> last = new HashMap<>();
        if (registrations.isEmpty()) {
            return last;
        }
        jdbc.sql("""
                select distinct on (registration) registration, occurred_at, odometer from fuel_transaction
                where registration in (:registrations) and occurred_at < :before and product <> 'ADBLUE'
                order by registration, occurred_at desc""")
                .param("registrations", registrations).param("before", Timestamp.from(before))
                .query(rs -> {
                    last.put(rs.getString("registration"), new LastFill(rs.getTimestamp("occurred_at").toInstant(),
                            (Integer) rs.getObject("odometer")));
                });
        return last;
    }

    private static final String FILTER = """
             where (cast(:company as uuid) is null or company_id = :company)
               and (cast(:vehicle as uuid) is null or vehicle_id = :vehicle)
               and (cast(:anomaly as varchar) is null or (',' || anomalies || ',') like '%,' || :anomaly || ',%')
               and (not :onlyAnomalies or anomalies is not null)
               and (cast(:q as varchar) is null or registration ilike '%' || :q || '%' or station ilike '%' || :q || '%')
            """;

    public Page<FuelTransaction> search(UUID companyId, UUID vehicleId, String anomaly, boolean onlyAnomalies, String q,
            Pageable pageable) {
        long total = bind(jdbc.sql("select count(*) from fuel_transaction" + FILTER), companyId, vehicleId, anomaly,
                onlyAnomalies, q).query(Long.class).single();
        List<FuelTransaction> content = bind(jdbc.sql("select * from fuel_transaction" + FILTER
                        + " order by occurred_at desc limit :limit offset :offset"), companyId, vehicleId, anomaly,
                onlyAnomalies, q)
                .param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, row) -> new FuelTransaction(rs.getObject("id", UUID.class),
                        rs.getObject("exchange_id", UUID.class), rs.getString("provider"),
                        rs.getString("transaction_ref"), rs.getString("card_number"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("station"), rs.getString("city"),
                        rs.getString("product"), rs.getBigDecimal("litres"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("amount"), (Integer) rs.getObject("odometer"), rs.getString("registration"),
                        rs.getObject("vehicle_id", UUID.class), rs.getObject("company_id", UUID.class),
                        rs.getString("energy"), rs.getString("anomalies")))
                .list();
        return new PageImpl<>(content, pageable, total);
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec sql, UUID companyId, UUID vehicleId,
            String anomaly, boolean onlyAnomalies, String q) {
        return sql.param("company", companyId).param("vehicle", vehicleId).param("anomaly", anomaly)
                .param("onlyAnomalies", onlyAnomalies).param("q", q == null || q.isBlank() ? null : q.trim());
    }

    public Summary summary(UUID companyId, LocalDate from, LocalDate to) {
        Timestamp start = Timestamp.from(from.atStartOfDay(PARIS).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(PARIS).toInstant());
        String scope = " where occurred_at >= :from and occurred_at < :to"
                + " and (cast(:company as uuid) is null or company_id = :company)";
        Map<String, Object> totals = jdbc.sql("""
                select count(*) as transactions, coalesce(sum(amount), 0) as amount,
                       coalesce(sum(litres) filter (where product <> 'ADBLUE'), 0) as litres,
                       count(*) filter (where anomalies is not null) as with_anomaly,
                       coalesce(sum(amount) filter (where anomalies is not null), 0) as amount_with_anomaly,
                       coalesce(sum(amount) filter (where product <> 'ADBLUE'), 0) as fuel_amount
                from fuel_transaction""" + scope)
                .param("from", start).param("to", end).param("company", companyId)
                .query().singleRow();
        Map<String, Integer> byType = new HashMap<>();
        jdbc.sql("select unnest(string_to_array(anomalies, ',')) as code, count(*) as n from fuel_transaction" + scope
                        + " and anomalies is not null group by 1")
                .param("from", start).param("to", end).param("company", companyId)
                .query(rs -> {
                    byType.put(rs.getString("code"), rs.getInt("n"));
                });
        List<MonthSpending> months = jdbc.sql("""
                select to_char(occurred_at at time zone 'Europe/Paris', 'YYYY-MM') as month, sum(amount) as amount,
                       coalesce(sum(litres) filter (where product <> 'ADBLUE'), 0) as litres
                from fuel_transaction""" + scope + " group by 1 order by 1")
                .param("from", start).param("to", end).param("company", companyId)
                .query((rs, row) -> new MonthSpending(rs.getString("month"), rs.getBigDecimal("amount"),
                        rs.getBigDecimal("litres")))
                .list();
        BigDecimal litres = (BigDecimal) totals.get("litres");
        BigDecimal fuelAmount = (BigDecimal) totals.get("fuel_amount");
        return new Summary(from, to, ((Number) totals.get("transactions")).intValue(), (BigDecimal) totals.get("amount"),
                litres, litres.signum() == 0 ? BigDecimal.ZERO : fuelAmount.divide(litres, 3, RoundingMode.HALF_UP),
                ((Number) totals.get("with_anomaly")).intValue(), (BigDecimal) totals.get("amount_with_anomaly"),
                byType, months);
    }

    public boolean isEmpty() {
        return jdbc.sql("select not exists (select 1 from fuel_transaction)").query(Boolean.class).single();
    }
}
