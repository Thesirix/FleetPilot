package com.fleetpilot.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.event.EventEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Incoming event envelope")
class IncomingEventTest {

    private final JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void reads_the_envelope_written_by_the_outbox() {
        UUID company = UUID.randomUUID();
        UUID vehicle = UUID.randomUUID();
        EventEnvelope<Object> envelope = EventEnvelope.of("vehicle.created", Instant.parse("2026-10-14T08:00:00Z"),
                "vehicle-service", company, "corr-1", Map.of("vehicleId", vehicle.toString(), "registration",
                        "GH-482-KM", "mileage", 12_500));

        IncomingEvent event = IncomingEvent.parse(mapper, mapper.writeValueAsString(envelope));

        assertThat(event.eventId()).isEqualTo(envelope.eventId());
        assertThat(event.type()).isEqualTo("vehicle.created");
        assertThat(event.companyId()).isEqualTo(company);
        assertThat(event.correlationId()).isEqualTo("corr-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-14T08:00:00Z"));
        assertThat(event.uuid("vehicleId")).contains(vehicle);
        assertThat(event.text("registration")).isEqualTo("GH-482-KM");
        assertThat(event.integer("mileage")).isEqualTo(12_500);
    }

    @Test
    void missing_or_invalid_optional_fields_are_empty() {
        String json = """
                {"eventId":"%s","type":"x","occurredAt":"2026-10-14T08:00:00Z","companyId":null,
                 "payload":{"vehicleId":"not-a-uuid","label":null}}""".formatted(UUID.randomUUID());

        IncomingEvent event = IncomingEvent.parse(mapper, json);

        assertThat(event.companyId()).isNull();
        assertThat(event.source()).isNull();
        assertThat(event.uuid("vehicleId")).isEmpty();
        assertThat(event.text("label")).isNull();
        assertThat(event.text("unknown")).isNull();
        assertThat(event.integer("label")).isNull();
    }

    @Test
    void an_envelope_without_id_is_rejected() {
        assertThatThrownBy(() -> IncomingEvent.parse(mapper, "{\"type\":\"x\",\"payload\":{}}"))
                .isInstanceOf(RuntimeException.class);
    }
}
