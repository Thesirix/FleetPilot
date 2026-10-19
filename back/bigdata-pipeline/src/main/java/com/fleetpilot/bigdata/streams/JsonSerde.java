package com.fleetpilot.bigdata.streams;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON serde of the topology: the input messages, the aggregates kept in the state stores and the documents sent to
 * Elasticsearch. A message that cannot be read becomes null and is dropped by the topology: one box sending garbage
 * must not stop the stream.
 */
public final class JsonSerde<T> implements Serde<T> {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final Class<T> type;

    private JsonSerde(Class<T> type) {
        this.type = type;
    }

    public static <T> JsonSerde<T> of(Class<T> type) {
        return new JsonSerde<>(type);
    }

    @Override
    public Serializer<T> serializer() {
        return (topic, value) -> value == null ? null : JSON.writeValueAsBytes(value);
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, bytes) -> {
            if (bytes == null) {
                return null;
            }
            try {
                return JSON.readValue(bytes, type);
            } catch (JacksonException e) {
                return null;
            }
        };
    }

    public static String write(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return JSON.readValue(json.getBytes(StandardCharsets.UTF_8), type);
    }
}
