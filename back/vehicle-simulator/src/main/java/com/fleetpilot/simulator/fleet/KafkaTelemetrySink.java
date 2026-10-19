package com.fleetpilot.simulator.fleet;

import java.util.UUID;

import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.simulator.engine.TelemetrySink;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Sends the messages of the simulated boxes to Kafka, keyed by vehicle so that each vehicle's messages stay in order. */
public class KafkaTelemetrySink implements TelemetrySink {

    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public KafkaTelemetrySink(KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @Override
    public void position(PositionReport report) {
        send(TelemetryMessages.TOPIC_POSITION, report.vehicleId(), report);
    }

    @Override
    public void mileage(MileageReport report) {
        send(TelemetryMessages.TOPIC_MILEAGE, report.vehicleId(), report);
    }

    @Override
    public void fault(FaultReport report) {
        send(TelemetryMessages.TOPIC_FAULT, report.vehicleId(), report);
    }

    private void send(String topic, UUID vehicleId, Object message) {
        kafka.send(topic, vehicleId.toString(), json.writeValueAsString(message));
    }
}
