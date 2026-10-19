package com.fleetpilot.integration.route.fuelcard;

import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.ProcessedFiles;
import com.fleetpilot.integration.route.FileFlowRoute;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.dataformat.csv.CsvDataFormat;
import org.springframework.stereotype.Component;

/**
 * Fuel cards: the providers send their transactions as CSV files (one line per fill), dropped in
 * {@code inbox/fuelcards}. Each transaction is matched with its vehicle, checked, stored, then published on Kafka
 * ({@code fuel.transactions}) for the analytics pipeline.
 */
@Component
public class FuelCardRoute extends FileFlowRoute {

    public FuelCardRoute(ProcessedFiles processedFiles) {
        super(processedFiles);
    }

    @Override
    public void configure() {
        CsvDataFormat csv = new CsvDataFormat();
        csv.setDelimiter(';');
        csv.setUseMaps(true);
        csv.setIgnoreEmptyLines(true);
        csv.setTrim(true);

        fileFlow(Flow.FUEL_CARDS, IllegalArgumentException.class)
                .unmarshal(csv)
                .bean("fuelCardChecker", "check")
                .bean("intake", "complete")
                // each new transaction is published for the analytics pipeline (consumption per vehicle)
                .filter(simple("{{fleetpilot.integration.fuel-events-enabled}}"))
                    .setBody(exchangeProperty(FuelCardChecker.STORED))
                    .split(body())
                        .setHeader(KafkaConstants.KEY, simple("${body.registration}"))
                        .bean("fuelEvents", "json")
                        .to("kafka:fuel.transactions")
                    .end()
                .end();
    }
}
