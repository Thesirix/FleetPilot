package com.fleetpilot.driver.util;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Numbers, amounts and dates the French way: 16 277 km, 72,50 €, 15 oct. 2026. */
public final class Formats {

    private static final Locale FR = Locale.FRANCE;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", FR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", FR);

    private Formats() {
    }

    public static String number(double value, int decimals) {
        NumberFormat format = NumberFormat.getNumberInstance(FR);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        // French grouping uses a narrow no-break space; a plain no-break space renders everywhere
        return format.format(value).replace(' ', ' ');
    }

    public static String km(int value) {
        return number(value, 0) + " km";
    }

    public static String money(double value) {
        return number(value, 2) + " €";
    }

    /** A calendar date (2026-10-15) or an instant (2026-10-15T08:00:00Z), shown as a date in Paris. */
    public static String date(String iso) {
        if (iso == null) {
            return "-";
        }
        try {
            return iso.length() <= 10 ? LocalDate.parse(iso).format(DATE) : Instant.parse(iso).atZone(PARIS).format(DATE);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    public static String dateTime(String iso) {
        if (iso == null) {
            return "-";
        }
        try {
            return Instant.parse(iso).atZone(PARIS).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    public static String now() {
        return ZonedDateTime.now(PARIS).toInstant().toString();
    }
}
