package com.fleetpilot.integration.journal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.integration.journal.Outcome.Issue;

/** Read models of the journal. */
public final class JournalViews {

    private JournalViews() {
    }

    public record ExchangeSummary(UUID id, Flow flow, String partner, String source, Instant receivedAt,
            Instant completedAt, ExchangeStatus status, int records, int accepted, int rejected, String summary,
            String resultRef, UUID replayOf) {
    }

    public record IssueView(Integer line, String reference, Issue.Severity severity, String code, String message) {
    }

    /** An exchange with its content as received and the issues found. */
    public record ExchangeDetail(ExchangeSummary summary, String contentType, String payload, List<IssueView> issues) {
    }

    /** Activity of a flow over the last 30 days; the route status comes from Camel. */
    public record FlowActivity(Flow flow, int exchanges, int processed, int partial, int rejected, int failed,
            int duplicates, long records, Instant lastReceivedAt) {
    }

    static ExchangeSummary summary(ResultSet rs) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new ExchangeSummary(rs.getObject("id", UUID.class), Flow.valueOf(rs.getString("flow")),
                rs.getString("partner"), rs.getString("source"), rs.getTimestamp("received_at").toInstant(),
                completed == null ? null : completed.toInstant(), ExchangeStatus.valueOf(rs.getString("status")),
                rs.getInt("records"), rs.getInt("accepted"), rs.getInt("rejected"), rs.getString("summary"),
                rs.getString("result_ref"), rs.getObject("replay_of", UUID.class));
    }
}
