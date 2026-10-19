package com.fleetpilot.quote.domain.analysis;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.quote.domain.model.LineKind;

/**
 * A line as written by the garage.
 *
 * @param kind      part, labour or supplies
 * @param code      code of the part or operation in the reference tariff, null when the garage did not give one
 * @param label     description written by the garage
 * @param quantity  units for a part, hours for labour, 1 for supplies
 * @param unitPrice unit price, or hourly rate for labour, excluding tax
 */
public record QuoteLineInput(LineKind kind, String code, String label, BigDecimal quantity, BigDecimal unitPrice) {

    public QuoteLineInput {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "The quantity of '" + label + "' must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_PRICE", "The price of '" + label + "' cannot be negative");
        }
        code = code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
