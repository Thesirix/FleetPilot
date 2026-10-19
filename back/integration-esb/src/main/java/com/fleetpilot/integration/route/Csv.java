package com.fleetpilot.integration.route;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Reading the CSV files of French partners: semicolon separated, decimal comma, sometimes a space as thousands
 * separator. A value that cannot be read raises an {@link IllegalArgumentException} naming the column.
 */
public final class Csv {

    private Csv() {
    }

    public static String text(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("colonne « " + column + " » vide");
        }
        return value.trim();
    }

    public static String optional(Map<String, String> row, String column) {
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static BigDecimal decimal(Map<String, String> row, String column) {
        String value = text(row, column).replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("colonne « " + column + " » : « " + row.get(column) + " » n'est pas un nombre");
        }
    }

    public static Integer integer(Map<String, String> row, String column) {
        String value = optional(row, column);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.replace(" ", "").replace(" ", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("colonne « " + column + " » : « " + value + " » n'est pas un entier");
        }
    }
}
