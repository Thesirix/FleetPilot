package com.fleetpilot.portal.web;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Numbers, amounts and dates the French way, as the lessors read them: 16 277 km, 412,50 €, 15/10/2026. */
public final class Formats {

    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE);

    private Formats() {
    }

    public static String number(double value, int decimals) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        // A plain no-break space: the narrow one of the JDK is missing from some fonts and from Excel
        return format.format(value).replace(' ', ' ');
    }

    public static String km(int value) {
        return number(value, 0) + " km";
    }

    public static String money(double value) {
        return number(value, 2) + " €";
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "" : instant.atZone(PARIS).format(DATE_TIME);
    }
}
