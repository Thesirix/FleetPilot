package com.fleetpilot.common.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * French vehicle registration numbers (SIV system, in use since 2009): {@code AB-123-CD}.
 *
 * <p>Rules applied: two letters, three digits, two letters; the letters I, O and U are never used (they look like
 * 1, 0 and V); the number block cannot be {@code 000}; the pair {@code SS} is not issued in either letter block.
 * Input is tolerant (lower case, spaces or no separator) and is normalized to the canonical form.
 */
public final class RegistrationNumbers {

    private static final String LETTER = "[A-HJ-NP-TV-Z]";
    private static final Pattern SIV = Pattern.compile("^(" + LETTER + "{2})(\\d{3})(" + LETTER + "{2})$");

    private RegistrationNumbers() {
    }

    /**
     * Returns the canonical form ({@code AB-123-CD}) of a registration number, or empty when it is not valid.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        Matcher matcher = SIV.matcher(compact);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String left = matcher.group(1);
        String number = matcher.group(2);
        String right = matcher.group(3);
        if ("000".equals(number) || "SS".equals(left) || "SS".equals(right)) {
            return Optional.empty();
        }
        return Optional.of(left + "-" + number + "-" + right);
    }

    public static boolean isValid(String raw) {
        return normalize(raw).isPresent();
    }
}
