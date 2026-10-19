package com.fleetpilot.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("French registration numbers (SIV)")
class RegistrationNumbersTest {

    @ParameterizedTest(name = "\"{0}\" is normalized to {1}")
    @CsvSource({
        "AB-123-CD, AB-123-CD",
        "ab-123-cd, AB-123-CD",
        "AB 123 CD, AB-123-CD",
        "AB123CD, AB-123-CD",
        "' fz-001-za ', FZ-001-ZA",
        "WW-999-ZZ, WW-999-ZZ"
    })
    void normalizes_valid_numbers(String raw, String expected) {
        assertThat(RegistrationNumbers.normalize(raw)).contains(expected);
        assertThat(RegistrationNumbers.isValid(raw)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is rejected")
    @ValueSource(strings = {
        "AI-123-CD", "AB-123-OD", "UB-123-CD",
        "AB-000-CD",
        "SS-123-CD", "AB-123-SS",
        "A-123-CD", "AB-12-CD", "AB-1234-CD", "AB-123-C",
        "123-AB-45", "AB_123_CD", "1234 AB 13"
    })
    void rejects_invalid_numbers(String raw) {
        assertThat(RegistrationNumbers.normalize(raw)).isEmpty();
        assertThat(RegistrationNumbers.isValid(raw)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejects_missing_values(String raw) {
        assertThat(RegistrationNumbers.isValid(raw)).isFalse();
    }

    @Test
    void old_fni_format_is_not_accepted() {
        assertThat(RegistrationNumbers.isValid("123 ABC 13")).isFalse();
    }
}
