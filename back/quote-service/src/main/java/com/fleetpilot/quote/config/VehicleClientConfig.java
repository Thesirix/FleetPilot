package com.fleetpilot.quote.config;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.quote.application.VehicleDirectory;
import com.fleetpilot.quote.infrastructure.vehicle.RestVehicleDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;

/** The fleet module, called with the identity of the quote service ({@code client_credentials}). */
@Configuration
public class VehicleClientConfig {

    @Bean
    @ConditionalOnMissingBean(VehicleDirectory.class)
    public VehicleDirectory vehicleDirectory(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return new RestVehicleDirectory(ServiceRestClients.create(builder, manager, "quote-service", baseUrl));
    }
}
