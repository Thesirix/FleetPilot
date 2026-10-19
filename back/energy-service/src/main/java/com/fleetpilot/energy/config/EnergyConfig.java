package com.fleetpilot.energy.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.energy.domain.Transition.Prices;
import com.fleetpilot.energy.infrastructure.RestSources;
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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class EnergyConfig {

    private static final String SERVICE = "energy-service";
    private static final String SECURITY_SCHEME = "keycloak";

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }

    /** Prices and emission factors come from the configuration: they change, the rules do not. */
    @Bean
    public Prices prices(@Value("${fleetpilot.energy.prices.diesel}") BigDecimal diesel,
            @Value("${fleetpilot.energy.prices.petrol}") BigDecimal petrol,
            @Value("${fleetpilot.energy.prices.lpg}") BigDecimal lpg,
            @Value("${fleetpilot.energy.prices.cng}") BigDecimal cng,
            @Value("${fleetpilot.energy.prices.depot-kwh}") BigDecimal depotKwh,
            @Value("${fleetpilot.energy.prices.public-kwh}") BigDecimal publicKwh,
            @Value("${fleetpilot.energy.grid-kg-co2-per-kwh}") BigDecimal grid) {
        return new Prices(diesel, petrol, lpg, cng, depotKwh, publicKwh, grid);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestSources restSources(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String vehicleUrl,
            @Value("${fleetpilot.services.telemetry-url}") String telemetryUrl, Clock clock) {
        return new RestSources(ServiceRestClients.create(builder, manager, SERVICE, vehicleUrl),
                ServiceRestClients.create(builder, manager, SERVICE, telemetryUrl), clock);
    }

    @Bean
    public OpenAPI energyOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
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
                        .title("FleetPilot Energy Transition API")
                        .version("v1")
                        .description("""
                                Energy transition of the fleet: for each vehicle, from its real usage measured by the \
                                connected box over 90 days, the electric model that covers it, the annual cost today \
                                and in electric, the CO2 avoided, the low emission zone of its site and its renewal \
                                date; the greening plan quarter by quarter and the quotas of the loi LOM.""")
                        .license(new License().name("MIT")));
    }
}
