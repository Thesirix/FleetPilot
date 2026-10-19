package com.fleetpilot.quote.unit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fleetpilot.quote.domain.analysis.Tariff;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;

/** The real reference tariff, read from the Flyway migration, for tests that need the whole catalogue. */
public final class SqlTariff implements Tariff {

    private final Map<String, List<String>> parts = new HashMap<>();
    private final Map<String, List<String>> operations = new HashMap<>();
    private final Map<String, List<String>> rates = new HashMap<>();

    public SqlTariff() {
        String sql;
        try (InputStream in = SqlTariff.class.getResourceAsStream("/db/migration/V2__reference_tariff.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, List<String>> target = null;
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("insert into part_reference")) {
                target = parts;
            } else if (trimmed.startsWith("insert into labour_operation")) {
                target = operations;
            } else if (trimmed.startsWith("insert into labour_rate")) {
                target = rates;
            } else if (trimmed.startsWith("(") && target != null) {
                for (List<String> row : rows(trimmed)) {
                    String key = target == rates ? row.get(0) + "/" + row.get(1) : row.get(0);
                    target.put(key, row);
                }
            }
        }
    }

    @Override
    public Optional<PartPrice> part(String code, TariffClass tariffClass) {
        return Optional.ofNullable(parts.get(code))
                .map(r -> new PartPrice(code, r.get(1), new BigDecimal(r.get(3 + tariffClass.ordinal()))));
    }

    @Override
    public Optional<StandardTime> operation(String code, TariffClass tariffClass) {
        return Optional.ofNullable(operations.get(code)).map(r -> new StandardTime(code, r.get(1),
                LabourCategory.valueOf(r.get(2)), new BigDecimal(r.get(3 + tariffClass.ordinal()))));
    }

    @Override
    public Optional<BigDecimal> hourlyRate(Region region, LabourCategory category, TariffClass tariffClass) {
        return Optional.ofNullable(rates.get(region + "/" + category))
                .map(r -> new BigDecimal(r.get(tariffClass == TariffClass.TRUCK ? 3 : 2)));
    }

    public int partCount() {
        return parts.size();
    }

    /** Splits "('A', 'B', 1.0), ('C', ...)" into rows of values, honouring quotes and doubled quotes. */
    private static List<List<String>> rows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = null;
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '\'' && i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                    value.append('\'');
                    i++;
                } else if (c == '\'') {
                    quoted = false;
                } else {
                    value.append(c);
                }
            } else if (c == '\'') {
                quoted = true;
            } else if (c == '(') {
                row = new ArrayList<>();
                value.setLength(0);
            } else if ((c == ',' || c == ')') && row != null) {
                row.add(value.toString().trim());
                value.setLength(0);
                if (c == ')') {
                    rows.add(row);
                    row = null;
                }
            } else if (row != null) {
                value.append(c);
            }
        }
        return rows;
    }
}
