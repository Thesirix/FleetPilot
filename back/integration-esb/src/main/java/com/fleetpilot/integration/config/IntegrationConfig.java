package com.fleetpilot.integration.config;

import java.time.Clock;
import java.time.ZoneId;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.common.security.KeycloakJwtConverter;
import com.fleetpilot.common.security.ProblemSecurityHandler;
import com.fleetpilot.integration.fleet.FleetDirectory;
import com.fleetpilot.integration.fleet.RestBusinessServices;
import com.fleetpilot.integration.fleet.RestFleetDirectory;
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
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class IntegrationConfig {

    private static final String SERVICE = "integration-esb";
    private static final String SECURITY_SCHEME = "keycloak";
    private static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs", "/v3/api-docs/**",
        "/swagger-ui.html", "/swagger-ui/**", "/error"
    };

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }

    @Bean
    @ConditionalOnMissingBean(FleetDirectory.class)
    public RestFleetDirectory fleetDirectory(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String vehicleUrl, Clock clock) {
        return new RestFleetDirectory(ServiceRestClients.create(builder, manager, SERVICE, vehicleUrl), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestBusinessServices businessServices(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.quote-url}") String quoteUrl,
            @Value("${fleetpilot.services.vehicle-url}") String vehicleUrl, JsonMapper json) {
        return new RestBusinessServices(ServiceRestClients.create(builder, manager, SERVICE, quoteUrl),
                ServiceRestClients.create(builder, manager, SERVICE, vehicleUrl), json);
    }

    /** The partner APIs played by the bus (the insurer) are protected by their own API key, not by Keycloak. */
    @Bean
    @Order(1)
    public SecurityFilterChain partnerSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/partners/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    /** The API of the bus itself: the same rules as every FleetPilot service (Keycloak tokens, roles). */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, KeycloakJwtConverter converter,
            ProblemSecurityHandler problems) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .build();
    }

    @Bean
    public OpenAPI integrationOpenApi(@Value("${fleetpilot.oidc.public-issuer-uri}") String issuer) {
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
                        .title("FleetPilot Integration API")
                        .version("v1")
                        .description("""
                                Enterprise service bus (Apache Camel): the files and messages exchanged with the \
                                partners (garage quotes in XML, lessor invoices in CSV, insurer claims over REST, fuel \
                                card transactions), what became of each of them, the issues found line by line, and \
                                the replay of a failed exchange. Also the fuel card transactions and their \
                                anomalies.""")
                        .license(new License().name("MIT")));
    }
}
