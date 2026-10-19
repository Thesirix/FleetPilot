package com.fleetpilot.bigdata;

import java.time.Clock;
import java.time.Duration;

import com.fleetpilot.bigdata.backfill.UsageHistoryBackfill;
import com.fleetpilot.bigdata.reference.FleetReference;
import com.fleetpilot.bigdata.sink.IndexTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The jobs around the stream: loading the history once, and purging the old positions every night. */
@Component
@ConditionalOnProperty(name = "fleetpilot.analytics.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class PipelineJobs {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineJobs.class);
    private static final Duration POSITION_RETENTION = Duration.ofDays(7);

    private final FleetReference fleet;
    private final UsageHistoryBackfill backfill;
    private final IndexTemplates indices;
    private final Clock clock;

    public PipelineJobs(FleetReference fleet, UsageHistoryBackfill backfill, IndexTemplates indices, Clock clock) {
        this.fleet = fleet;
        this.backfill = backfill;
        this.indices = indices;
        this.clock = clock;
    }

    /** Once the vehicles are known (to enrich the past days), the history is loaded if the index has none. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadHistory() {
        Thread.ofVirtual().name("usage-history").start(() -> {
            for (int attempt = 1; attempt <= 60; attempt++) {
                try {
                    if (fleet.isLoaded()) {
                        if (backfill.needed()) {
                            backfill.load();
                        }
                        return;
                    }
                } catch (RuntimeException e) {
                    LOG.info("Usage history not loaded yet (attempt {}): {}", attempt, e.getMessage());
                }
                pause();
            }
        });
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Paris")
    public void purgePositions() {
        LOG.info("Positions purged from Elasticsearch: {}",
                indices.purgePositions(clock.instant().minus(POSITION_RETENTION)));
    }

    private static void pause() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
