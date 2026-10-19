package com.fleetpilot.quote.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer;
import com.fleetpilot.quote.domain.analysis.Tariff;
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
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
public class QuoteServiceConfig {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Paris");

    private static final String SECURITY_SCHEME = "keycloak";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }

    @Bean
    public QuoteAnalyzer quoteAnalyzer(Tariff tariff) {
        return new QuoteAnalyzer(tariff);
    }

    @Bean
    public NewTopic quoteEventsTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_QUOTES).partitions(6).replicas(1).build();
    }

    /** Fleet events that could not be handled after the retries, kept for analysis and replay. */
    @Bean
    public NewTopic fleetEventsDeadLetterTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_FLEET + ".DLT").partitions(1).replicas(1).build();
    }

    /** Three retries one second apart, then the message goes to the dead letter topic instead of blocking the others. */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (consumed, error) -> new TopicPartition(consumed.topic() + ".DLT", 0));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }

    @Bean
    public OpenAPI quoteServiceOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
        OAuthFlow authorizationCode = new OAuthFlow()
                .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                .tokenUrl(issuer + "/protocol/openid-connect/token")
                .scopes(new Scopes().addString("openid", "OpenID Connect"));
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("Keycloak, realm fleetpilot. Demo accounts are listed in the README.")
                        .flows(new OAuthFlows().authorizationCode(authorizationCode))))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .info(new Info()
                        .title("FleetPilot Quote API")
                        .version("v1")
                        .description("""
                                Garage quotes compared line by line with the reference tariff (part prices, standard \
                                labour times, negotiated regional rates). Overcharges are flagged, negotiated with the \
                                garage, and the savings are measured.

                                Garages submit and follow their quotes in the garage space; fleet managers review, \
                                negotiate and decide. Errors follow RFC 9457 (application/problem+json).""")
                        .license(new License().name("MIT")));
    }
}
