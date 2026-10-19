package com.fleetpilot.integration.route.lessor;

import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.ProcessedFiles;
import com.fleetpilot.integration.route.FileFlowRoute;
import org.apache.camel.dataformat.csv.CsvDataFormat;
import org.springframework.stereotype.Component;

/**
 * Lessor invoices: a CSV file per monthly invoice (one line per rent), dropped in {@code inbox/lessors}. The lines are
 * read as maps keyed by the header and checked against the lease contracts.
 */
@Component
public class LessorInvoiceRoute extends FileFlowRoute {

    public LessorInvoiceRoute(ProcessedFiles processedFiles) {
        super(processedFiles);
    }

    @Override
    public void configure() {
        CsvDataFormat csv = new CsvDataFormat();
        csv.setDelimiter(';');
        csv.setUseMaps(true);
        csv.setIgnoreEmptyLines(true);
        csv.setTrim(true);

        fileFlow(Flow.LESSOR_INVOICES, IllegalArgumentException.class)
                .unmarshal(csv)
                .bean("lessorInvoiceChecker", "check")
                .bean("intake", "complete");
    }
}
