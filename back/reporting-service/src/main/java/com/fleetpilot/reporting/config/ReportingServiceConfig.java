package com.fleetpilot.reporting.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.reporting.application.Sources.FleetSource;
import com.fleetpilot.reporting.application.Sources.QuoteSource;
import com.fleetpilot.reporting.infrastructure.source.RestSources;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;

@Configuration
public class ReportingServiceConfig {

    private static final String SERVICE = "reporting-service";
    private static final String SECURITY_SCHEME = "keycloak";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }

    @Bean
    @ConditionalOnMissingBean
    public FleetSource fleetSource(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return new RestSources.Fleet(ServiceRestClients.create(builder, manager, SERVICE, baseUrl));
    }

    @Bean
    @ConditionalOnMissingBean
    public QuoteSource quoteSource(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.quote-url}") String baseUrl) {
        return new RestSources.Quotes(ServiceRestClients.create(builder, manager, SERVICE, baseUrl));
    }

    @Bean
    public OpenAPI reportingServiceOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
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
                        .title("FleetPilot Reporting API")
                        .version("v1")
                        .description("""
                                Reports of the fleet (inventory, total cost of ownership, garage quotes, deadlines, \
                                savings) in Excel and PDF, built in the background from an ActiveMQ queue, and the \
                                indicators of the fleet over twelve months.""")
                        .license(new License().name("MIT")));
    }
}
