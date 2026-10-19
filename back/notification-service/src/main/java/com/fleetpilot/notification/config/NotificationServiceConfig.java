package com.fleetpilot.notification.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.notification.application.Ports.FleetDirectory;
import com.fleetpilot.notification.application.Ports.QuoteDirectory;
import com.fleetpilot.notification.infrastructure.fleet.RestDirectories;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionFactoryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.RestClient;

@Configuration
public class NotificationServiceConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Paris");

    private static final String SECURITY_SCHEME = "keycloak";
    private static final String SERVICE = "notification-service";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }

    /**
     * Redelivery of the failed e-mails by the broker: 10 s, then 30 s, 90 s... The notification itself gives up after
     * {@link com.fleetpilot.notification.domain.Notification#MAX_ATTEMPTS} attempts.
     */
    @Bean
    public ActiveMQConnectionFactoryCustomizer redeliveryPolicy(
            @Value("${fleetpilot.notifications.redelivery-delay:10000}") long delay) {
        return factory -> {
            RedeliveryPolicy policy = new RedeliveryPolicy();
            policy.setInitialRedeliveryDelay(delay);
            policy.setRedeliveryDelay(delay);
            policy.setUseExponentialBackOff(true);
            policy.setBackOffMultiplier(3);
            policy.setMaximumRedeliveries(10);
            factory.setRedeliveryPolicy(policy);
            factory.setTrustAllPackages(false);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public FleetDirectory fleetDirectory(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return new RestDirectories.Fleet(ServiceRestClients.create(builder, manager, SERVICE, baseUrl));
    }

    @Bean
    @ConditionalOnMissingBean
    public QuoteDirectory quoteDirectory(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.quote-url}") String baseUrl) {
        return new RestDirectories.Quotes(ServiceRestClients.create(builder, manager, SERVICE, baseUrl));
    }

    @Bean
    public NewTopic quoteEventsDeadLetterTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_QUOTES + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic fleetEventsDeadLetterTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_FLEET + ".DLT").partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic alertEventsDeadLetterTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_ALERTS + ".DLT").partitions(1).replicas(1).build();
    }

    /** Three retries one second apart, then the event goes to the dead letter topic instead of blocking the others. */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumed, error) -> new TopicPartition(consumed.topic() + ".DLT", 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    public OpenAPI notificationServiceOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
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
                        .title("FleetPilot Notification API")
                        .version("v1")
                        .description("""
                                Journal of the e-mails sent by FleetPilot. Business events (Kafka) become e-mail \
                                requests on an ActiveMQ queue; any service can also put a request on the queue \
                                `fleetpilot.notifications`. Each e-mail is sent once, retried on failure, and kept \
                                with its rendering.""")
                        .license(new License().name("MIT")));
    }
}
