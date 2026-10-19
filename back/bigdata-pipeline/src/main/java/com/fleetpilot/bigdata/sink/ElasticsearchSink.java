package com.fleetpilot.bigdata.sink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.JsonData;
import com.fleetpilot.bigdata.streams.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Indexes the outputs of the topology into Elasticsearch, a batch of Kafka records per bulk request. The key of a
 * record is the id of its document: an aggregate updated ten times stays one document. If Elasticsearch is down, the
 * batch fails, is retried by the listener container and the offsets do not move: nothing is lost.
 */
public class ElasticsearchSink {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchSink.class);

    private final ElasticsearchClient elasticsearch;

    public ElasticsearchSink(ElasticsearchClient elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    @KafkaListener(topics = {Topics.POSITIONS_OUT, Topics.ACTIVITY_OUT, Topics.VEHICLE_DAY_OUT, Topics.FAULTS_OUT,
        Topics.ALERTS_OUT, Topics.FUEL_OUT}, groupId = "analytics-elasticsearch-sink", batch = "true")
    public void index(List<ConsumerRecord<String, String>> records) {
        List<BulkOperation> operations = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() == null || record.key() == null) {
                continue;
            }
            String index = Topics.INDICES.get(record.topic());
            operations.add(BulkOperation.of(b -> b.index(i -> i.index(index).id(record.key())
                    .document(JsonData.fromJson(record.value())))));
        }
        bulk(operations);
    }

    /** Indexes documents, failing if Elasticsearch refuses some of them (a mapping error, for example). */
    public void bulk(List<BulkOperation> operations) {
        if (operations.isEmpty()) {
            return;
        }
        try {
            BulkResponse response = elasticsearch.bulk(b -> b.operations(operations));
            if (response.errors()) {
                // a "create" of a document that exists is expected (history loaded over days the stream indexed)
                List<BulkResponseItem> refused = response.items().stream().filter(item -> item.error() != null)
                        .filter(item -> !"version_conflict_engine_exception".equals(item.error().type())).toList();
                if (!refused.isEmpty()) {
                    LOG.error("{} documents refused by Elasticsearch: {}", refused.size(), refused.stream()
                            .map(item -> item.error().type() + ": " + item.error().reason()).distinct().limit(3).toList());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Elasticsearch unreachable", e);
        }
    }
}
