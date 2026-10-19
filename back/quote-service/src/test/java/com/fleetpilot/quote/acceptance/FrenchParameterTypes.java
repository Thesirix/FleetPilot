package com.fleetpilot.quote.acceptance;

import java.math.BigDecimal;

import io.cucumber.java.ParameterType;

/** Amounts written the French way in the scenarios: 5 700,00 or 94,00. */
public class FrenchParameterTypes {

    @ParameterType("\\d{1,3}(?:[  ]\\d{3})*(?:,\\d{1,2})?")
    public BigDecimal euros(String value) {
        return new BigDecimal(value.replace(" ", "").replace(" ", "").replace(',', '.'));
    }
}
