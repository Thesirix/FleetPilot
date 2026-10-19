package com.fleetpilot.common.client;

import java.net.http.HttpClient;
import java.time.Duration;

import com.fleetpilot.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * REST clients for calls from one FleetPilot service to another, made with the identity of the calling service: an
 * OAuth2 {@code client_credentials} token of the {@value #REGISTRATION} Keycloak client, obtained and cached by Spring
 * Security, plus the correlation id of the current request so the logs can be followed across services.
 */
public final class ServiceRestClients {

    /** Client registration of the services in {@code spring.security.oauth2.client.registration}. */
    public static final String REGISTRATION = "fleetpilot-services";

    private ServiceRestClients() {
    }

    /**
     * @param service name of the calling service, used as the principal of the cached token
     * @param baseUrl root URL of the called service
     */
    public static RestClient create(RestClient.Builder builder, OAuth2AuthorizedClientManager manager, String service,
            String baseUrl) {
        OAuth2ClientHttpRequestInterceptor oauth = new OAuth2ClientHttpRequestInterceptor(manager);
        oauth.setClientRegistrationIdResolver(request -> REGISTRATION);
        // The service acts as itself, whoever triggered the call
        oauth.setPrincipalResolver(request -> new AnonymousAuthenticationToken(service, service,
                AuthorityUtils.createAuthorityList("ROLE_SERVICE")));
        // The HTTP client of the JDK: unlike HttpURLConnection, it sends PATCH (status changes of a claim, of a quote)
        JdkClientHttpRequestFactory http = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        http.setReadTimeout(Duration.ofSeconds(30));
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(http)
                .requestInterceptor(oauth)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
