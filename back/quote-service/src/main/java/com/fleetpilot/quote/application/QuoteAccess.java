package com.fleetpilot.quote.application;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.quote.domain.model.Quote;

/**
 * Tells which quotes the caller may see. FleetPilot staff see every quote; a client manager the quotes of their own
 * company; a garage its own quotes only. A quote out of reach is reported as "not found".
 */
public interface QuoteAccess {

    /** The only client company the caller may see, empty when not restricted by company. */
    Optional<UUID> restrictedCompanyId();

    /** The only garage the caller may see, empty when not restricted by garage. */
    Optional<UUID> restrictedGarageId();

    /** Name of the caller, written in the timeline of the quote. */
    String author();

    /** True when the caller may approve quotes above the approval limit of fleet managers. */
    boolean canApproveAnyAmount();

    default void checkAccess(Quote quote) {
        restrictedGarageId().ifPresent(garage -> {
            if (!garage.equals(quote.getGarage().getId())) {
                throw new ResourceNotFoundException("Quote", quote.getId());
            }
        });
        restrictedCompanyId().ifPresent(company -> {
            if (!company.equals(quote.getCompanyId())) {
                throw new ResourceNotFoundException("Quote", quote.getId());
            }
        });
    }
}
