package com.fleetpilot.bigdata.sink;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Index templates of the analytics indices (mappings: dates, keywords, geo points), installed at startup from
 * {@code elasticsearch/templates/*.json}, before any document is indexed. Installing them again is harmless.
 */
public class IndexTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(IndexTemplates.class);

    private final ElasticsearchClient elasticsearch;

    public IndexTemplates(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    /** Waits for Elasticsearch (it may still be starting), then installs the templates. */
    public void install() {
        for (int attempt = 1; ; attempt++) {
            try {
                elasticsearch.info();
                break;
            } catch (IOException | RuntimeException e) {
                if (attempt == 40) {
                    throw new IllegalStateException("Elasticsearch unreachable", e);
                }
                LOG.info("Elasticsearch not reachable yet (attempt {}): {}", attempt, e.getMessage());
                sleep();
            }
        }
        try {
            for (Resource template : new PathMatchingResourcePatternResolver()
                    .getResources("classpath:elasticsearch/templates/*.json")) {
                String name = template.getFilename().replace(".json", "");
                try (InputStream json = template.getInputStream()) {
                    elasticsearch.indices().putIndexTemplate(b -> b.name(name).withJson(json));
                }
                LOG.info("Index template installed: {}", name);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Positions are kept a week: the maps show the last days, the daily aggregates keep the history. */
    public long purgePositions(Instant before) {
        try {
            return elasticsearch.deleteByQuery(b -> b.index("fleet-positions").query(q -> q.range(r -> r
                    .date(d -> d.field("at").lt(before.toString()))))).deleted();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
