package com.fleetpilot.bigdata.backfill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.fleetpilot.bigdata.reference.FleetReference;
import com.fleetpilot.bigdata.sink.ElasticsearchSink;
import com.fleetpilot.bigdata.streams.Model.VehicleDayDoc;
import com.fleetpilot.bigdata.streams.Model.VehicleRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Loads the past days into Elasticsearch: the stream only sees what happens from its start, the telemetry service
 * keeps 90 days of daily usage. Done once, when the index has no history yet; the days already covered by the stream
 * are left as the stream computed them.
 */
public class UsageHistoryBackfill {

    static final int DAYS = 90;
    private static final Logger LOG = LoggerFactory.getLogger(UsageHistoryBackfill.class);
    private static final int PAGE_SIZE = 5_000;

    private final RestClient telemetry;
    private final FleetReference fleet;
    private final ElasticsearchClient elasticsearch;
    private final ElasticsearchSink sink;
    private final Clock clock;

    public UsageHistoryBackfill(RestClient telemetry, FleetReference fleet, ElasticsearchClient elasticsearch,
            ElasticsearchSink sink, Clock clock) {
        this.telemetry = telemetry;
        this.fleet = fleet;
        this.elasticsearch = elasticsearch;
        this.sink = sink;
        this.clock = clock;
    }

    record UsageDay(UUID vehicleId, UUID companyId, LocalDate day, BigDecimal distanceKm, int trips,
            BigDecimal longestTripKm, int drivingMinutes, int maxSpeed) {
    }

    record UsagePage(List<UsageDay> content, int totalPages, long totalElements) {
    }

    public boolean needed() {
        try {
            boolean exists = elasticsearch.indices().exists(b -> b.index("fleet-vehicle-day")).value();
            return !exists || elasticsearch.count(b -> b.index("fleet-vehicle-day")
                    .query(q -> q.term(t -> t.field("source").value("history")))).count() == 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return the number of days loaded */
    public long load() {
        LocalDate from = LocalDate.now(clock).minusDays(DAYS);
        long loaded = 0;
        int page = 0;
        UsagePage current;
        do {
            int index = page++;
            current = telemetry.get().uri(b -> b.path("/api/v1/telemetry/usage-days").queryParam("from", from)
                    .queryParam("page", index).queryParam("size", PAGE_SIZE).build())
                    .retrieve().body(UsagePage.class);
            if (current == null) {
                break;
            }
            List<BulkOperation> operations = new ArrayList<>();
            for (UsageDay d : current.content()) {
                VehicleRef ref = fleet.vehicle(d.vehicleId());
                VehicleDayDoc doc = new VehicleDayDoc(d.vehicleId(), d.companyId(),
                        ref == null ? null : ref.companyName(), ref == null ? null : ref.registration(),
                        ref == null ? null : ref.category(), ref == null ? null : ref.energy(),
                        ref == null ? null : (ref.make() + " " + ref.model()).trim(), d.day(),
                        d.distanceKm().intValue(), d.drivingMinutes(), d.maxSpeed(), "history");
                // the "create" operation leaves alone a day the stream already indexed
                operations.add(BulkOperation.of(b -> b.create(c -> c.index("fleet-vehicle-day")
                        .id(d.vehicleId() + "|" + d.day()).document(doc))));
            }
            sink.bulk(operations);
            loaded += operations.size();
        } while (page < current.totalPages());
        LOG.info("Usage history loaded into Elasticsearch: {} vehicle days since {}", loaded, from);
        return loaded;
    }
}
