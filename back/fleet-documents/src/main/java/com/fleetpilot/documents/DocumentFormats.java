package com.fleetpilot.documents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * French formats for the templates of e-mails and documents ({@code ${fmt.money(amount)}}): amounts, numbers, dates.
 * Values may come from JSON (numbers, ISO strings) or from Java (BigDecimal, LocalDate, Instant).
 */
public class DocumentFormats {

    public static final DocumentFormats INSTANCE = new DocumentFormats();

    private static final char NARROW_NO_BREAK_SPACE = (char) 0x202F;
    private static final char NO_BREAK_SPACE = (char) 0x00A0;

    private static final Locale FRENCH = Locale.FRANCE;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", FRENCH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", FRENCH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", FRENCH);

    protected DocumentFormats() {
    }

    /** 1 250,00 € */
    public String money(Object value) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return "-";
        }
        return spaces(NumberFormat.getCurrencyInstance(FRENCH).format(amount));
    }

    /** 1 250 € */
    public String roundMoney(Object value) {
        BigDecimal amount = decimal(value);
        if (amount == null) {
            return "-";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(FRENCH);
        format.setMaximumFractionDigits(0);
        return spaces(format.format(amount.setScale(0, RoundingMode.HALF_UP)));
    }

    /** 12 500 */
    public String number(Object value) {
        BigDecimal number = decimal(value);
        return number == null ? "-" : spaces(NumberFormat.getIntegerInstance(FRENCH).format(number));
    }

    /** A quantity as written on a quote: 28, 4,1 or 0,25 (no useless decimals). */
    public String quantity(Object value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return "-";
        }
        NumberFormat format = NumberFormat.getNumberInstance(FRENCH);
        format.setMaximumFractionDigits(2);
        return spaces(format.format(number.stripTrailingZeros()));
    }

    /** 8,6 % */
    public String percent(Object value) {
        BigDecimal number = decimal(value);
        if (number == null) {
            return "-";
        }
        NumberFormat format = NumberFormat.getNumberInstance(FRENCH);
        format.setMaximumFractionDigits(1);
        format.setMinimumFractionDigits(1);
        return spaces(format.format(number)) + " %";
    }

    /** "2026-11-02", a date or an instant, as "2 novembre 2026". */
    public String date(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof LocalDate day) {
            return day.format(DATE);
        }
        String text = value.toString();
        return text.length() == 10 ? LocalDate.parse(text).format(DATE)
                : Instant.parse(text).atZone(PARIS).format(DATE);
    }

    /** "2026-09" as "septembre 2026". */
    public String month(Object value) {
        return value == null ? "-" : YearMonth.parse(value.toString()).format(MONTH);
    }

    /** An instant as "14 octobre 2026 à 10h30". */
    public String dateTime(Object value) {
        return value == null ? "-" : Instant.parse(value.toString()).atZone(PARIS).format(DATE_TIME);
    }

    /** Value-added tax (20 %) of an amount excluding tax. */
    public String vat(Object value) {
        BigDecimal amount = decimal(value);
        return amount == null ? "-" : money(amount.multiply(new BigDecimal("0.20")));
    }

    /** Amount including the 20 % tax. */
    public String withVat(Object value) {
        BigDecimal amount = decimal(value);
        return amount == null ? "-" : money(amount.multiply(new BigDecimal("1.20")));
    }

    protected static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(value.toString());
    }

    /** Java writes narrow no-break spaces in French numbers; plain spaces render everywhere. */
    private static String spaces(String text) {
        return text.replace(NARROW_NO_BREAK_SPACE, ' ').replace(NO_BREAK_SPACE, ' ');
    }
}
