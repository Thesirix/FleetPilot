package com.fleetpilot.integration.journal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.integration.journal.JournalViews.ExchangeDetail;
import com.fleetpilot.integration.journal.JournalViews.ExchangeSummary;
import com.fleetpilot.integration.journal.JournalViews.FlowActivity;
import com.fleetpilot.integration.journal.JournalViews.IssueView;
import com.fleetpilot.integration.journal.Outcome.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Journal of the exchanges with the partners: every file or message received, its content (to replay it), what the
 * bus made of it and the issues found, record by record. It is what the integration console of the portal shows.
 */
@Component
public class ExchangeJournal {

    /** Issues kept per exchange: enough to fix a file, without storing thousands of lines of the same mistake. */
    static final int MAX_ISSUES = 500;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRANCE)
            .withZone(ZoneId.of("Europe/Paris"));

    private final JdbcClient jdbc;
    private final Clock clock;

    public ExchangeJournal(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Records a file or a message as it arrives, before anything is done with it. */
    @Transactional
    public UUID open(Flow flow, String source, String payload, String checksum, UUID replayOf) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into exchange (id, flow, source, checksum, content_type, payload, received_at, status, replay_of)
                values (:id, :flow, :source, :checksum, :contentType, :payload, :at, 'RUNNING', :replayOf)""")
                .param("id", id).param("flow", flow.name()).param("source", source).param("checksum", checksum)
                .param("contentType", flow.contentType()).param("payload", payload)
                .param("at", Timestamp.from(clock.instant())).param("replayOf", replayOf)
                .update();
        return id;
    }

    @Transactional
    public void complete(UUID id, Outcome outcome) {
        jdbc.sql("""
                update exchange set status = :status, partner = :partner, records = :records, accepted = :accepted,
                    rejected = :rejected, summary = :summary, result_ref = :resultRef, completed_at = :at
                where id = :id""")
                .param("status", outcome.status().name()).param("partner", outcome.partner())
                .param("records", outcome.records()).param("accepted", outcome.accepted())
                .param("rejected", outcome.rejected()).param("summary", truncate(outcome.summary(), 500))
                .param("resultRef", outcome.resultRef()).param("at", Timestamp.from(clock.instant())).param("id", id)
                .update();
        outcome.issues().stream().limit(MAX_ISSUES).forEach(issue -> jdbc.sql("""
                insert into exchange_issue (exchange_id, line, reference, severity, code, message)
                values (:id, :line, :reference, :severity, :code, :message)""")
                .param("id", id).param("line", issue.line()).param("reference", truncate(issue.reference(), 60))
                .param("severity", issue.severity().name()).param("code", issue.code())
                .param("message", truncate(issue.message(), 500))
                .update());
    }

    /** The same content was already handled: the exchange is kept in the journal, pointing to the first one. */
    @Transactional
    public void duplicate(UUID id, UUID original) {
        String first = original == null ? null : jdbc.sql("select received_at from exchange where id = :id")
                .param("id", original).query(Timestamp.class).optional()
                .map(at -> WHEN.format(at.toInstant())).orElse(null);
        complete(id, new Outcome(null, 0, 0, List.of(),
                first == null ? "Fichier identique déjà traité" : "Fichier identique déjà traité le " + first, null));
        jdbc.sql("update exchange set status = 'DUPLICATE', replay_of = :original where id = :id")
                .param("original", original).param("id", id).update();
    }

    /** A technical failure that persisted after the retries: the content is kept, the exchange can be replayed. */
    @Transactional
    public void failed(UUID id, String reason) {
        jdbc.sql("update exchange set status = 'FAILED', summary = :summary, completed_at = :at where id = :id")
                .param("summary", truncate("Échec technique après plusieurs tentatives : " + reason, 500))
                .param("at", Timestamp.from(clock.instant())).param("id", id)
                .update();
    }

    public Page<ExchangeSummary> search(Flow flow, ExchangeStatus status, Pageable pageable) {
        String where = " where (cast(:flow as varchar) is null or flow = :flow)"
                + " and (cast(:status as varchar) is null or status = :status)";
        String flowName = flow == null ? null : flow.name();
        String statusName = status == null ? null : status.name();
        long total = jdbc.sql("select count(*) from exchange" + where)
                .param("flow", flowName).param("status", statusName).query(Long.class).single();
        List<ExchangeSummary> content = jdbc.sql("""
                select id, flow, partner, source, received_at, completed_at, status, records, accepted, rejected,
                       summary, result_ref, replay_of
                from exchange""" + where + " order by received_at desc limit :limit offset :offset")
                .param("flow", flowName).param("status", statusName)
                .param("limit", pageable.getPageSize()).param("offset", pageable.getOffset())
                .query((rs, row) -> JournalViews.summary(rs))
                .list();
        return new PageImpl<>(content, pageable, total);
    }

    public Optional<ExchangeDetail> detail(UUID id) {
        return jdbc.sql("""
                select id, flow, partner, source, received_at, completed_at, status, records, accepted, rejected,
                       summary, result_ref, replay_of, content_type, payload
                from exchange where id = :id""")
                .param("id", id)
                .query((rs, row) -> new ExchangeDetail(JournalViews.summary(rs), rs.getString("content_type"),
                        rs.getString("payload"), issues(id)))
                .optional();
    }

    List<IssueView> issues(UUID id) {
        return jdbc.sql("""
                select line, reference, severity, code, message from exchange_issue where exchange_id = :id
                order by severity, line nulls first, id""")
                .param("id", id)
                .query((rs, row) -> new IssueView((Integer) rs.getObject("line"), rs.getString("reference"),
                        Issue.Severity.valueOf(rs.getString("severity")), rs.getString("code"), rs.getString("message")))
                .list();
    }

    /** Activity of each flow over the last 30 days, by status, and its last exchange. */
    public List<FlowActivity> activity() {
        Instant since = clock.instant().minusSeconds(30L * 24 * 3600);
        return Arrays.stream(Flow.values()).map(flow -> jdbc.sql("""
                select count(*) as total,
                       count(*) filter (where status = 'PROCESSED') as processed,
                       count(*) filter (where status = 'PARTIAL') as partial,
                       count(*) filter (where status = 'REJECTED') as rejected,
                       count(*) filter (where status = 'FAILED') as failed,
                       count(*) filter (where status = 'DUPLICATE') as duplicates,
                       coalesce(sum(records), 0) as records,
                       max(received_at) as last_received
                from exchange where flow = :flow and received_at >= :since""")
                .param("flow", flow.name()).param("since", Timestamp.from(since))
                .query((rs, row) -> new FlowActivity(flow, rs.getInt("total"), rs.getInt("processed"),
                        rs.getInt("partial"), rs.getInt("rejected"), rs.getInt("failed"), rs.getInt("duplicates"),
                        rs.getLong("records"), rs.getTimestamp("last_received") == null ? null
                                : rs.getTimestamp("last_received").toInstant()))
                .single())
                .toList();
    }

    public boolean isEmpty() {
        return jdbc.sql("select not exists (select 1 from exchange)").query(Boolean.class).single();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
