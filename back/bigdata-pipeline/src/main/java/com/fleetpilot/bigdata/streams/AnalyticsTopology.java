package com.fleetpilot.bigdata.streams;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.fleetpilot.bigdata.streams.Model.ActivityDoc;
import com.fleetpilot.bigdata.streams.Model.ActivityWindow;
import com.fleetpilot.bigdata.streams.Model.AlertDoc;
import com.fleetpilot.bigdata.streams.Model.AlertEvent;
import com.fleetpilot.bigdata.streams.Model.EnrichedPosition;
import com.fleetpilot.bigdata.streams.Model.FaultDoc;
import com.fleetpilot.bigdata.streams.Model.FuelDoc;
import com.fleetpilot.bigdata.streams.Model.FuelState;
import com.fleetpilot.bigdata.streams.Model.FuelTransaction;
import com.fleetpilot.bigdata.streams.Model.GeoPoint;
import com.fleetpilot.bigdata.streams.Model.VehicleDay;
import com.fleetpilot.bigdata.streams.Model.VehicleDayDoc;
import com.fleetpilot.bigdata.streams.Model.VehicleRef;
import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

/**
 * The analytics of FleetPilot, as a Kafka Streams topology. Every output is keyed by the id of its Elasticsearch
 * document, so that an update replaces the previous version instead of adding a duplicate.
 *
 * <ol>
 *   <li>positions enriched with the vehicle (global table {@code reference.vehicles}) and kept once a minute per
 *       vehicle, for the maps;</li>
 *   <li>activity of each company's fleet per window of five minutes (vehicles connected, moving, idling, average
 *       speed);</li>
 *   <li>distance, driving time and top speed per vehicle and day (Paris time), from the odometers and positions;</li>
 *   <li>trouble codes and alerts, enriched;</li>
 *   <li>fuel consumption in litres per 100 km, from one fill to the next of the same vehicle.</li>
 * </ol>
 * The aggregates go through the record cache of Kafka Streams, flushed at every commit (five seconds): Elasticsearch
 * receives the current picture of each key, not every intermediate step, and the last state of a day or a window is
 * sent even if no message follows it.
 */
public final class AnalyticsTopology {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    static final Duration SAMPLE = Duration.ofMinutes(1);
    static final Duration ACTIVITY_WINDOW = Duration.ofMinutes(5);
    /**
     * How late a position may arrive and still count in its window: the positions of the six partitions are merged by
     * company, and a partition may lag the others by a few minutes (catching up after a restart).
     */
    static final Duration LATE_TOLERANCE = Duration.ofMinutes(10);
    static final String SAMPLER_STORE = "position-sampler";
    static final EventTimeExtractor EVENT_TIME = new EventTimeExtractor();
    /** A fill and the previous one must be this far apart for the consumption to make sense. */
    static final int MIN_KM = 20;
    static final int MAX_KM = 3_000;

    private AnalyticsTopology() {
    }

    /**
     * @param reportSeconds interval of the positions of a moving vehicle (the tick of the boxes)
     */
    public static void build(StreamsBuilder builder, long reportSeconds) {
        Serde<String> keys = Serdes.String();
        GlobalKTable<String, VehicleRef> vehicles = builder.globalTable(Topics.VEHICLES,
                Consumed.with(keys, JsonSerde.of(VehicleRef.class)), Materialized.as("vehicles"));

        KStream<String, PositionReport> rawPositions = builder.stream(TelemetryMessages.TOPIC_POSITION,
                        Consumed.with(keys, JsonSerde.of(PositionReport.class)).withTimestampExtractor(EVENT_TIME))
                .filter((k, p) -> p != null && p.vehicleId() != null);
        KStream<String, EnrichedPosition> positions = rawPositions
                .leftJoin(vehicles, (k, p) -> p.vehicleId().toString(), AnalyticsTopology::enrich);

        // 1. one position per vehicle and minute, for the maps
        builder.addStateStore(Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(SAMPLER_STORE), keys,
                Serdes.Long()));
        positions.processValues(Sampler::new, SAMPLER_STORE)
                .selectKey((k, p) -> p.vehicleId() + "|" + p.at().toEpochMilli())
                .to(Topics.POSITIONS_OUT, Produced.with(keys, JsonSerde.of(EnrichedPosition.class)));

        // 2. activity of each company's fleet, windows of five minutes
        positions.filter((k, p) -> p.companyId() != null)
                .groupBy((k, p) -> p.companyId().toString(), Grouped.with(keys, JsonSerde.of(EnrichedPosition.class)))
                .windowedBy(TimeWindows.ofSizeAndGrace(ACTIVITY_WINDOW, LATE_TOLERANCE))
                .aggregate(ActivityWindow::new, (company, p, window) -> window.add(p),
                        Materialized.with(keys, JsonSerde.of(ActivityWindow.class)))
                .toStream()
                .map((window, a) -> KeyValue.pair(window.key() + "|" + window.window().start(),
                        new ActivityDoc(a.companyId, a.companyName, window.window().startTime(),
                                window.window().endTime(), a.connected.size(), a.moving.size(), a.idling.size(),
                                a.averageSpeed())))
                .to(Topics.ACTIVITY_OUT, Produced.with(keys, JsonSerde.of(ActivityDoc.class)));

        // 3. distance, driving time and top speed per vehicle and day
        Serde<VehicleDay> daySerde = JsonSerde.of(VehicleDay.class);
        KStream<String, VehicleDay> fromOdometers = builder.stream(TelemetryMessages.TOPIC_MILEAGE,
                        Consumed.with(keys, JsonSerde.of(MileageReport.class)).withTimestampExtractor(EVENT_TIME))
                .filter((k, m) -> m != null && m.vehicleId() != null)
                .map((k, m) -> KeyValue.pair(dayKey(m.vehicleId(), m.at()),
                        new VehicleDay().odometer(m.vehicleId(), m.companyId(), day(m.at()), m.odometerKm(), m.at())));
        KStream<String, VehicleDay> fromPositions = rawPositions
                .map((k, p) -> KeyValue.pair(dayKey(p.vehicleId(), p.at()), new VehicleDay().position(p.vehicleId(),
                        p.companyId(), day(p.at()), p.speedKmh(), p.ignition(), p.at(), reportSeconds)));
        fromOdometers.merge(fromPositions)
                .groupByKey(Grouped.with(keys, daySerde))
                .reduce(VehicleDay::merge, Materialized.with(keys, daySerde))
                .toStream()
                .leftJoin(vehicles, (k, d) -> d.vehicleId.toString(), AnalyticsTopology::dayDoc)
                .to(Topics.VEHICLE_DAY_OUT, Produced.with(keys, JsonSerde.of(VehicleDayDoc.class)));

        // 4. trouble codes and alerts
        builder.stream(TelemetryMessages.TOPIC_FAULT, Consumed.with(keys, JsonSerde.of(FaultReport.class))
                        .withTimestampExtractor(EVENT_TIME))
                .filter((k, f) -> f != null && f.vehicleId() != null)
                .leftJoin(vehicles, (k, f) -> f.vehicleId().toString(), AnalyticsTopology::faultDoc)
                .selectKey((k, d) -> d.vehicleId() + "|" + d.code() + "|" + d.at().toEpochMilli())
                .to(Topics.FAULTS_OUT, Produced.with(keys, JsonSerde.of(FaultDoc.class)));
        builder.stream(Topics.ALERTS, Consumed.with(keys, JsonSerde.of(AlertEvent.class)))
                .filter((k, e) -> e != null && e.payload() != null && e.payload().vehicleId() != null)
                .leftJoin(vehicles, (k, e) -> e.payload().vehicleId().toString(), AnalyticsTopology::alertDoc)
                .selectKey((k, d) -> d.alertId().toString())
                .to(Topics.ALERTS_OUT, Produced.with(keys, JsonSerde.of(AlertDoc.class)));

        // 5. fuel consumption from one fill to the next, per vehicle (the transactions are keyed by registration)
        builder.stream(Topics.FUEL, Consumed.with(keys, JsonSerde.of(FuelTransaction.class)))
                .filter((k, t) -> t != null && t.registration() != null)
                .leftJoin(vehicles, (k, t) -> t.vehicleId() == null ? null : t.vehicleId().toString(),
                        AnalyticsTopology::fuelDoc)
                .groupByKey(Grouped.with(keys, JsonSerde.of(FuelDoc.class)))
                // no cache: every transaction must reach Elasticsearch, not only the last one of a commit
                .aggregate(FuelState::new, (registration, doc, state) -> consumption(doc, state),
                        Materialized.<String, FuelState, KeyValueStore<Bytes, byte[]>>with(keys,
                                JsonSerde.of(FuelState.class)).withCachingDisabled())
                .toStream()
                .mapValues(state -> state.current)
                .selectKey((k, d) -> d.transactionId().toString())
                .to(Topics.FUEL_OUT, Produced.with(keys, JsonSerde.of(FuelDoc.class)));
    }

    /** Keeps a position when the previous one kept for the vehicle is a minute old. */
    static final class Sampler implements FixedKeyProcessor<String, EnrichedPosition, EnrichedPosition> {

        private FixedKeyProcessorContext<String, EnrichedPosition> context;
        private KeyValueStore<String, Long> last;

        @Override
        public void init(FixedKeyProcessorContext<String, EnrichedPosition> processorContext) {
            this.context = processorContext;
            this.last = processorContext.getStateStore(SAMPLER_STORE);
        }

        @Override
        public void process(FixedKeyRecord<String, EnrichedPosition> record) {
            String vehicle = record.value().vehicleId().toString();
            long at = record.value().at().toEpochMilli();
            Long previous = last.get(vehicle);
            if (previous == null || at - previous >= SAMPLE.toMillis()) {
                last.put(vehicle, at);
                context.forward(record);
            }
        }
    }

    static FuelState consumption(FuelDoc doc, FuelState state) {
        FuelDoc result = doc;
        boolean fuel = !"ADBLUE".equals(doc.product());
        if (fuel && doc.odometer() != null && state.lastOdometer != null) {
            int km = doc.odometer() - state.lastOdometer;
            if (km >= MIN_KM && km <= MAX_KM) {
                result = new FuelDoc(doc.transactionId(), doc.provider(), doc.vehicleId(), doc.companyId(),
                        doc.companyName(), doc.registration(), doc.category(), doc.energy(), doc.at(), doc.station(),
                        doc.city(), doc.product(), doc.litres(), doc.amount(), doc.unitPrice(), doc.odometer(), km,
                        Model.consumption(doc.litres(), km), doc.anomalies());
            }
        }
        if (fuel && doc.odometer() != null && (state.lastOdometer == null || doc.odometer() > state.lastOdometer)) {
            state.lastOdometer = doc.odometer();
            state.lastAt = doc.at();
        }
        state.current = result;
        return state;
    }

    static EnrichedPosition enrich(PositionReport p, VehicleRef ref) {
        return new EnrichedPosition(p.vehicleId(), p.companyId(), ref == null ? null : ref.companyName(),
                p.registration(), ref == null ? null : ref.category(), ref == null ? null : ref.energy(), p.at(),
                new GeoPoint(p.latitude(), p.longitude()), p.speedKmh(), p.ignition(), p.batteryPercent());
    }

    static VehicleDayDoc dayDoc(VehicleDay d, VehicleRef ref) {
        return new VehicleDayDoc(d.vehicleId, d.companyId, ref == null ? null : ref.companyName(),
                ref == null ? null : ref.registration(), ref == null ? null : ref.category(),
                ref == null ? null : ref.energy(), Model.makeModel(ref), d.day, d.distanceKm(),
                (int) (d.movingSeconds / 60), d.maxSpeed, "live");
    }

    static FaultDoc faultDoc(FaultReport f, VehicleRef ref) {
        return new FaultDoc(f.vehicleId(), f.companyId(), ref == null ? null : ref.companyName(), f.registration(),
                ref == null ? null : ref.category(), ref == null ? null : ref.energy(), Model.makeModel(ref), f.at(),
                f.code(), f.description(), f.severity() == null ? null : f.severity().name());
    }

    static AlertDoc alertDoc(AlertEvent e, VehicleRef ref) {
        Model.AlertPayload a = e.payload();
        return new AlertDoc(a.alertId(), a.vehicleId(), e.companyId(), ref == null ? null : ref.companyName(),
                a.registration(), ref == null ? null : ref.category(), a.type(), a.severity(), a.message(),
                a.raisedAt() == null ? e.occurredAt() : a.raisedAt(),
                a.latitude() == null ? null : new GeoPoint(a.latitude(), a.longitude()));
    }

    static FuelDoc fuelDoc(FuelTransaction t, VehicleRef ref) {
        return new FuelDoc(t.id(), t.provider(), t.vehicleId(), t.companyId(), ref == null ? null : ref.companyName(),
                t.registration(), ref == null ? null : ref.category(), t.energy(), t.occurredAt(), t.station(),
                t.city(), t.product(), t.litres(), t.amount(), t.unitPrice(), t.odometer(), null, null,
                t.anomalies() == null || t.anomalies().isBlank() ? List.of() : List.of(t.anomalies().split(",")));
    }

    static String dayKey(Object vehicleId, Instant at) {
        return vehicleId + "|" + day(at);
    }

    static LocalDate day(Instant at) {
        return LocalDate.ofInstant(at, PARIS);
    }
}
