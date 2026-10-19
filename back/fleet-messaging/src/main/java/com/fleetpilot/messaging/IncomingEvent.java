package com.fleetpilot.messaging;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An event read from Kafka: the envelope written by {@link OutboxPublisher}, with a payload left as a JSON tree.
 * Consumers only read the fields they need, so a producer can add fields without breaking them.
 */
public record IncomingEvent(UUID eventId, String type, Instant occurredAt, String source, UUID companyId,
        String correlationId, JsonNode payload) {

    /** Parses the JSON envelope of a Kafka record. */
    public static IncomingEvent parse(JsonMapper mapper, String json) {
        JsonNode root = mapper.readTree(json);
        return new IncomingEvent(
                UUID.fromString(root.required("eventId").asString()),
                root.required("type").asString(),
                Instant.parse(root.required("occurredAt").asString()),
                root.path("source").asString(null),
                uuid(root.path("companyId")).orElse(null),
                root.path("correlationId").asString(null),
                root.required("payload"));
    }

    public String text(String field) {
        JsonNode value = payload.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    public Optional<UUID> uuid(String field) {
        return uuid(payload.path(field));
    }

    public Integer integer(String field) {
        JsonNode value = payload.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static Optional<UUID> uuid(JsonNode node) {
        if (node == null || !node.isString() || node.asString().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(node.asString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
