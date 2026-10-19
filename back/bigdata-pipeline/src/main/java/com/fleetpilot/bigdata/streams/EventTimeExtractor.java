package com.fleetpilot.bigdata.streams;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;

/**
 * The time of a telemetry message is when the box measured it (its {@code at} field), not when Kafka received it:
 * a box that was out of coverage sends its positions late, and they must land in the right window and the right day.
 * The field is found without parsing the whole message; a message without it keeps the time of the record.
 */
public class EventTimeExtractor implements TimestampExtractor {

    private static final String FIELD = "\"at\":\"";

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof String json) {
            int start = json.indexOf(FIELD);
            if (start >= 0) {
                int from = start + FIELD.length();
                int end = json.indexOf('"', from);
                try {
                    return Instant.parse(json.substring(from, end)).toEpochMilli();
                } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
                    return record.timestamp();
                }
            }
        }
        return record.timestamp();
    }
}
