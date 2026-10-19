package com.fleetpilot.integration.route;

import com.fleetpilot.integration.journal.FileIntake;
import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.ProcessedFiles;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The skeleton shared by the routes that receive files from the partners, as a JBoss Fuse developer would write it:
 *
 * <ol>
 *   <li>the file consumer polls the folder of the flow; a handled file is moved to {@code .done/<day>/}, a file that
 *       failed to {@code .error/};</li>
 *   <li>the file is journaled, then goes through an idempotent consumer keyed by its content;</li>
 *   <li>a service of FleetPilot unreachable or in error is retried three times with a growing delay (2, 4, 8 s), then
 *       the exchange is marked failed and can be replayed;</li>
 *   <li>a file that cannot be read is refused at once, with the reason, without retry.</li>
 * </ol>
 */
public abstract class FileFlowRoute extends RouteBuilder {

    private final ProcessedFiles processedFiles;

    protected FileFlowRoute(ProcessedFiles processedFiles) {
        this.processedFiles = processedFiles;
    }

    /** Starts the route of a flow, up to the point where its handler takes over the text of the file. */
    @SafeVarargs
    protected final RouteDefinition fileFlow(Flow flow, Class<? extends Exception>... unreadable) {
        RouteDefinition route = from("file:{{fleetpilot.integration.inbox}}/" + flow.folder()
                + "?include=.*\\" + flow.extension()
                + "&charset=UTF-8&readLock=changed&readLockMinAge=1000&sortBy=file:name"
                + "&move=.done/${date:now:yyyyMMdd}/${file:name}&moveFailed=.error/${file:name}")
                .routeId(flow.folder())
                .routeDescription(description(flow));

        route.onException(ResourceAccessException.class, HttpServerErrorException.class)
                .maximumRedeliveries(3).redeliveryDelay("{{fleetpilot.integration.redelivery-delay}}")
                .backOffMultiplier(2).useExponentialBackOff()
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .bean("intake", "fail");
        if (unreadable.length > 0) {
            route.onException(unreadable).handled(true).bean("intake", "rejectUnreadable");
        }

        route.setProperty(FileIntake.FLOW, constant(flow))
                .convertBodyTo(String.class, "UTF-8")
                .bean("intake", "open")
                .idempotentConsumer(exchangeProperty(FileIntake.MESSAGE_KEY)).idempotentRepository(processedFiles)
                    .skipDuplicate(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                    .bean("intake", "duplicate")
                    .stop()
                .end();
        return route;
    }

    private static String description(Flow flow) {
        return switch (flow) {
            case GARAGE_QUOTES -> "Devis des garages (XML) vers le module devis";
            case LESSOR_INVOICES -> "Factures des loueurs (CSV) rapprochées des contrats";
            case FUEL_CARDS -> "Transactions des cartes carburant (CSV) contrôlées";
            case INSURER_CLAIMS -> "Déclaration et suivi des sinistres chez l'assureur";
        };
    }
}
