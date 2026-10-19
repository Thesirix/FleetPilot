package com.fleetpilot.telemetry.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.telemetry.application.FleetReference;
import com.fleetpilot.telemetry.application.TelemetryStore;
import com.fleetpilot.telemetry.infrastructure.reference.CachedFleetReference;
import com.fleetpilot.telemetry.infrastructure.reference.FleetModule;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableAsync
public class TelemetryServiceConfig {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryServiceConfig.class);
    private static final String SECURITY_SCHEME = "keycloak";
    /** Positions are kept a week: enough to replay a track, the trips and daily usage stay. */
    private static final Duration POSITION_RETENTION = Duration.ofDays(7);

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }

    @Bean
    @ConditionalOnMissingBean
    public FleetModule fleetModule(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return new FleetModule(ServiceRestClients.create(builder, manager, "telemetry-service", baseUrl));
    }

    @Bean
    @ConditionalOnMissingBean(FleetReference.class)
    public CachedFleetReference fleetReference(FleetModule fleet, JdbcTemplate jdbc, TransactionTemplate transactions,
            Clock clock) {
        return new CachedFleetReference(fleet, jdbc, transactions, clock);
    }

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_ALERTS).partitions(3).replicas(1).build();
    }

    /**
     * A batch that cannot be stored (database down) is retried five times, two seconds apart, then dropped and logged:
     * telemetry is a flow, the next positions replace the lost ones, and a dead letter topic would only fill up.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, 5));
    }

    /** Purges the positions older than a week, every night. */
    @Bean
    public PositionPurge positionPurge(TelemetryStore store, TransactionTemplate transactions, Clock clock) {
        return new PositionPurge(store, transactions, clock);
    }

    /** Nightly purge of the position history. */
    public static class PositionPurge {

        private final TelemetryStore store;
        private final TransactionTemplate transactions;
        private final Clock clock;

        PositionPurge(TelemetryStore store, TransactionTemplate transactions, Clock clock) {
            this.store = store;
            this.transactions = transactions;
            this.clock = clock;
        }

        @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Paris")
        public void purge() {
            Integer deleted = transactions.execute(status -> store.purgePositions(clock.instant().minus(POSITION_RETENTION)));
            LOG.info("Position history purged: {} positions older than {} days", deleted, POSITION_RETENTION.toDays());
        }
    }

    @Bean
    public OpenAPI telemetryServiceOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes().addString("openid", "OpenID Connect"));
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .info(new Info()
                        .title("FleetPilot Telemetry API")
                        .version("v1")
                        .description("""
                                Connected vehicles: positions, odometer and trouble codes received from Kafka, live \
                                state of each vehicle (also pushed as Server-Sent Events), trips, daily usage over 90 \
                                days, and the alert centre (speeding, long idle, low battery, engine faults, services \
                                due).""")
                        .license(new License().name("MIT")));
    }
}
