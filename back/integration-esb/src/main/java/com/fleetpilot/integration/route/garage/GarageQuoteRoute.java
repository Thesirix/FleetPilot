package com.fleetpilot.integration.route.garage;

import com.fleetpilot.integration.journal.Flow;
import com.fleetpilot.integration.journal.ProcessedFiles;
import com.fleetpilot.integration.route.FileFlowRoute;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import org.apache.camel.ValidationException;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.springframework.stereotype.Component;

/**
 * Garage quotes: an XML file per quote, dropped by the garage's software in {@code inbox/garages}. The file is
 * validated against its XSD, read with JAXB, checked and sent to the quote module, which analyses it at once against
 * the reference tariff.
 */
@Component
public class GarageQuoteRoute extends FileFlowRoute {

    public GarageQuoteRoute(ProcessedFiles processedFiles) {
        super(processedFiles);
    }

    @Override
    public void configure() throws JAXBException {
        JaxbDataFormat jaxb = new JaxbDataFormat(JAXBContext.newInstance(GarageQuoteFile.class));

        fileFlow(Flow.GARAGE_QUOTES, ValidationException.class, UnmarshalException.class)
                .to("validator:schemas/garage-quote.xsd")
                .unmarshal(jaxb)
                .bean("garageQuoteHandler", "handle")
                .bean("intake", "complete");
    }
}
