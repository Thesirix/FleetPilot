package com.fleetpilot.vehicle.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.event.EventTypes;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class VehicleServiceConfig {

    /** Business dates (deadlines, contract ends) are French calendar dates. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Paris");

    private static final String SECURITY_SCHEME = "keycloak";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }

    @Bean
    public NewTopic fleetEventsTopic() {
        return TopicBuilder.name(EventTypes.TOPIC_FLEET).partitions(6).replicas(1).build();
    }

    /** Swagger UI logs in with Keycloak (authorization code + PKCE) and sends the access token with every call. */
    @Bean
    public OpenAPI vehicleServiceOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
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
                .title("FleetPilot Fleet API")
                .version("v1")
                .description("""
                        Fleet register of the client companies managed by FleetPilot: vehicles, drivers, assignments, \
                        long-term lease contracts, deadlines (technical inspection, service, tyres...) and claims.

                        Errors follow RFC 9457 (application/problem+json) with a stable `code` and the \
                        `correlationId` of the request.""")
                .license(new License().name("MIT")));
    }
}
