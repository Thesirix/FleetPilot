package com.fleetpilot.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Vehicle Identification Numbers")
class VinsTest {

    @Test
    void normalizes_case_and_spaces() {
        assertThat(Vins.normalize(" vf1rfb00x6 5123456 ")).contains("VF1RFB00X65123456");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VF1RFB00X65123456", "WVWZZZ1KZAW000001", "VF3MRHNSMKS000017", "YV1ZWA8U6P1000001"})
    void accepts_real_world_formats(String vin) {
        assertThat(Vins.isValid(vin)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VF1RFB00X6512345", "VF1RFB00X651234567", "VF1RFB00I65123456", "VF1RFB00O65123456",
        "VF1RFB00Q65123456", "VF1-FB00X65123456"})
    void rejects_wrong_length_or_forbidden_letters(String vin) {
        assertThat(Vins.isValid(vin)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejects_missing_values(String vin) {
        assertThat(Vins.isValid(vin)).isFalse();
    }

    @Test
    void extracts_the_world_manufacturer_identifier() {
        assertThat(Vins.manufacturerCode("VF1RFB00X65123456")).isEqualTo("VF1");
        assertThatIllegalArgumentException().isThrownBy(() -> Vins.manufacturerCode("nope"));
    }
}
