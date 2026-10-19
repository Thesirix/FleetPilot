package com.fleetpilot.bigdata.config;

import java.time.Clock;
import java.time.ZoneId;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fleetpilot.bigdata.backfill.UsageHistoryBackfill;
import com.fleetpilot.bigdata.reference.FleetReference;
import com.fleetpilot.bigdata.sink.ElasticsearchSink;
import com.fleetpilot.bigdata.sink.IndexTemplates;
import com.fleetpilot.common.client.ServiceRestClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;

@Configuration
@EnableKafkaStreams
@EnableScheduling
public class PipelineConfig {

    private static final String SERVICE = "bigdata-pipeline";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }

    @Bean
    @ConditionalOnMissingBean
    public FleetReference fleetReference(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String vehicleUrl, KafkaTemplate<String, String> kafka) {
        return new FleetReference(ServiceRestClients.create(builder, manager, SERVICE, vehicleUrl), kafka);
    }

    @Bean(initMethod = "install")
    public IndexTemplates indexTemplates(ElasticsearchClient elasticsearch) {
        return new IndexTemplates(elasticsearch);
    }

    @Bean
    public ElasticsearchSink elasticsearchSink(ElasticsearchClient elasticsearch, IndexTemplates templates) {
        return new ElasticsearchSink(elasticsearch);
    }

    @Bean
    public UsageHistoryBackfill usageHistoryBackfill(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.telemetry-url}") String telemetryUrl, FleetReference fleet,
            ElasticsearchClient elasticsearch, ElasticsearchSink sink, Clock clock) {
        return new UsageHistoryBackfill(ServiceRestClients.create(builder, manager, SERVICE, telemetryUrl), fleet,
                elasticsearch, sink, clock);
    }
}
