package com.fleetpilot.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security shared by every FleetPilot API, active as soon as a service depends on the OAuth2 resource server starter:
 *
 * <ul>
 *   <li>stateless API protected by Keycloak access tokens (signature, issuer and audience checked by Spring Boot);</li>
 *   <li>Keycloak realm roles mapped to Spring roles, rules expressed with {@code @PreAuthorize} on controllers;</li>
 *   <li>health probes, OpenAPI and Swagger UI left public, everything else authenticated;</li>
 *   <li>401 and 403 answered as problem details.</li>
 * </ul>
 *
 * <p>Ordered before Spring Boot's own security auto-configurations, which create a default chain when none exists.
 */
@AutoConfiguration(beforeName = {
    "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
    "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
    "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({JwtAuthenticationToken.class, HttpSecurity.class})
@EnableMethodSecurity
public class FleetSecurityAutoConfiguration {

    static final String[] PUBLIC_PATHS = {
        "/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs", "/v3/api-docs/**",
        "/swagger-ui.html", "/swagger-ui/**", "/graphiql", "/graphiql/**", "/graphql/schema", "/error"
    };

    @Bean
    @ConditionalOnMissingBean
    public KeycloakJwtConverter keycloakJwtConverter() {
        return new KeycloakJwtConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemSecurityHandler problemSecurityHandler() {
        return new ProblemSecurityHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain fleetSecurityFilterChain(HttpSecurity http, KeycloakJwtConverter converter,
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
}
