package com.fleetpilot.integration.route.insurer;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * The insurer flow, in three routes:
 *
 * <ol>
 *   <li>{@code insurer-events}: follows the fleet events on Kafka and keeps the declared claims;</li>
 *   <li>{@code insurer-declare}: declares a claim to the insurer's REST API (retried five times if the insurer is
 *       unreachable);</li>
 *   <li>{@code insurer-follow-up}: every minute, asks the insurer where each open claim stands and reports it in
 *       FleetPilot.</li>
 * </ol>
 */
@Component
public class InsurerRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("kafka:fleet.events?groupId=integration-esb&autoOffsetReset=latest")
                .routeId("insurer-events")
                .routeDescription("Sinistres déclarés dans FleetPilot (Kafka)")
                .autoStartup("{{fleetpilot.integration.insurer.declarations-enabled}}")
                .filter().method("insurerClaims", "isDeclaration")
                .to("direct:declare-claim");

        from("direct:declare-claim")
                .routeId("insurer-declare")
                .routeDescription("Déclaration du sinistre à l'assureur (REST)")
                .onException(Exception.class)
                    .maximumRedeliveries(5).redeliveryDelay("{{fleetpilot.integration.redelivery-delay}}")
                    .backOffMultiplier(2).useExponentialBackOff()
                    .retryAttemptedLogLevel(LoggingLevel.WARN)
                    .handled(true)
                    .bean("insurerClaims", "declarationFailed")
                .end()
                .bean("insurerClaims", "prepareDeclaration")
                .filter(body().isNotNull())
                    .removeHeaders("*")
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                    .setHeader("X-Api-Key", simple("{{fleetpilot.integration.insurer.api-key}}"))
                    .to("{{fleetpilot.integration.insurer.url}}/claims")
                    .convertBodyTo(String.class)
                    .bean("insurerClaims", "declared")
                .end();

        from("timer:insurer-follow-up?delay={{fleetpilot.integration.insurer.follow-up-delay}}"
                        + "&period={{fleetpilot.integration.insurer.follow-up-interval}}")
                .routeId("insurer-follow-up")
                .routeDescription("Suivi des dossiers chez l'assureur, chaque minute")
                .bean("insurerClaims", "openClaims")
                .split(body())
                    .setProperty(InsurerClaims.CLAIM, body())
                    .doTry()
                        .removeHeaders("*")
                        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .setHeader("X-Api-Key", simple("{{fleetpilot.integration.insurer.api-key}}"))
                        .setBody(constant(null))
                        .toD("{{fleetpilot.integration.insurer.url}}/claims/${exchangeProperty."
                                + InsurerClaims.CLAIM + ".claimNumber}")
                        .convertBodyTo(String.class)
                        .bean("insurerClaims", "synchronise")
                    .doCatch(Exception.class)
                        // the insurer or FleetPilot is unreachable, or the claim is unknown: the next poll tries again
                        .log(LoggingLevel.WARN, "Claim ${exchangeProperty." + InsurerClaims.CLAIM
                                + ".claimNumber} not followed up: ${exception.message}")
                    .end()
                .end();
    }
}
