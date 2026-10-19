package com.fleetpilot.integration.journal;

import java.util.List;

/**
 * What a flow made of a file or a message: the partner, how many records it held, how many were accepted as they
 * are, the issues found, and a one-line summary for the journal.
 *
 * @param resultRef what the exchange produced in FleetPilot (a quote reference, a claim number...), if anything
 */
public record Outcome(String partner, int records, int accepted, List<Issue> issues, String summary, String resultRef) {

    /** A problem found in a record: an error rejects it, a warning lets it through but must be looked at. */
    public record Issue(Integer line, String reference, Severity severity, String code, String message) {

        public enum Severity {
            ERROR,
            WARNING
        }

        public static Issue error(Integer line, String reference, String code, String message) {
            return new Issue(line, reference, Severity.ERROR, code, message);
        }

        public static Issue warning(Integer line, String reference, String code, String message) {
            return new Issue(line, reference, Severity.WARNING, code, message);
        }
    }

    public Outcome {
        issues = List.copyOf(issues);
    }

    /** A file refused as a whole (unreadable, invalid against its schema, refused by the business service). */
    public static Outcome rejected(String partner, Issue issue, String summary) {
        return new Outcome(partner, 1, 0, List.of(issue), summary, null);
    }

    public int rejected() {
        return records - accepted;
    }

    public ExchangeStatus status() {
        if (records > 0 && accepted == 0) {
            return ExchangeStatus.REJECTED;
        }
        return accepted < records ? ExchangeStatus.PARTIAL : ExchangeStatus.PROCESSED;
    }
}
