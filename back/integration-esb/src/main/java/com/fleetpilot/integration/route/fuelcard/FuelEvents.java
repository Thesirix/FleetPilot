package com.fleetpilot.integration.route.fuelcard;

import com.fleetpilot.integration.route.fuelcard.FuelTransactions.FuelTransaction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The fuel transactions as JSON messages, as the analytics pipeline reads them. */
@Component("fuelEvents")
public class FuelEvents {

    private final JsonMapper json;

    public FuelEvents(JsonMapper json) {
        this.json = json;
    }

    public String json(FuelTransaction transaction) {
        return json.writeValueAsString(transaction);
    }
}
