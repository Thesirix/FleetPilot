package com.fleetpilot.reporting.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A report as tables, independent of the file format: the use cases fill them, the Excel writer lays them out.
 */
public final class Tables {

    private Tables() {
    }

    /** How a column is formatted. */
    public enum Kind {
        TEXT,
        INTEGER,
        DECIMAL,
        MONEY,
        PERCENT,
        DATE
    }

    public record Column(String label, Kind kind, int width) {
    }

    /**
     * One sheet: a title and subtitle above a header row, the rows, and an optional totals row.
     *
     * @param totals values of the totals row (null cells stay empty), or an empty list for none
     */
    public record Sheet(String name, String title, String subtitle, List<Column> columns, List<List<Object>> rows,
            List<Object> totals) {

        public Sheet {
            Objects.requireNonNull(name, "name");
            columns = List.copyOf(columns);
            rows = rows.stream().map(row -> Collections.unmodifiableList(new ArrayList<>(row))).toList();
            totals = totals == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(totals));
            for (List<Object> row : rows) {
                if (row.size() != columns.size()) {
                    throw new IllegalArgumentException("Every row of " + name + " needs " + columns.size() + " cells");
                }
            }
        }
    }

    /** Lays the sheets out in a file. */
    public interface WorkbookWriter {
        byte[] write(List<Sheet> sheets);
    }
}
