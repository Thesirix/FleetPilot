package com.fleetpilot.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");
    private static final UUID COMPANY = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void factory_creates_a_version_1_event_with_a_unique_id() {
        EventEnvelope<Map<String, String>> first = EventEnvelope.of(EventTypes.VEHICLE_CREATED, NOW, "vehicle-service",
                COMPANY, "corr-12345678", Map.of("registration", "AB-123-CD"));
        EventEnvelope<Map<String, String>> second = EventEnvelope.of(EventTypes.VEHICLE_CREATED, NOW, "vehicle-service",
                COMPANY, "corr-12345678", Map.of("registration", "AB-123-CD"));

        assertThat(first.version()).isEqualTo(1);
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.payload()).containsEntry("registration", "AB-123-CD");
    }

    @Test
    void mandatory_metadata_is_enforced() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), null, 1, NOW, "svc", COMPANY, null, "x"))
                .withMessage("type");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventEnvelope<>(UUID.randomUUID(), "t", 0, NOW, "svc", COMPANY, null, "x"));
    }
}
