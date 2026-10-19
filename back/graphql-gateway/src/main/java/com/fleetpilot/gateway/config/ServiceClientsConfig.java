package com.fleetpilot.gateway.config;

import java.time.Duration;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.web.CorrelationIdFilter;
import com.fleetpilot.gateway.client.ServiceClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * REST clients of the services behind the gateway. Every call carries the user's own access token (token relay) and
 * the correlation id of the GraphQL request, so the services apply their rules and the logs can be followed.
 */
@Configuration
public class ServiceClientsConfig {

    /** Propagates the identity and the correlation id of the incoming request. */
    static ClientHttpRequestInterceptor tokenRelay() {
        return (request, body, execution) -> {
            CurrentUser.token().ifPresent(token -> request.getHeaders().setBearerAuth(token.getToken().getTokenValue()));
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            request.getHeaders().set(HttpHeaders.ACCEPT, "application/json, application/problem+json");
            return execution.execute(request, body);
        };
    }

    @Bean
    public ServiceClient vehicleService(RestClient.Builder builder,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient quoteService(RestClient.Builder builder,
            @Value("${fleetpilot.services.quote-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient notificationService(RestClient.Builder builder,
            @Value("${fleetpilot.services.notification-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient reportingService(RestClient.Builder builder,
            @Value("${fleetpilot.services.reporting-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient telemetryService(RestClient.Builder builder,
            @Value("${fleetpilot.services.telemetry-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient integrationService(RestClient.Builder builder,
            @Value("${fleetpilot.services.integration-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    @Bean
    public ServiceClient energyService(RestClient.Builder builder,
            @Value("${fleetpilot.services.energy-url}") String baseUrl) {
        return client(builder, baseUrl);
    }

    private static ServiceClient client(RestClient.Builder builder, String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        return new ServiceClient(builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .requestInterceptor(tokenRelay())
                .build());
    }
}
