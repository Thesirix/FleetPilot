package com.fleetpilot.quote.application;

import java.util.UUID;

import com.fleetpilot.quote.application.view.QuoteViews.GarageStats;
import com.fleetpilot.quote.application.view.QuoteViews.SavingsSummary;

/** Aggregates of the savings module, computed in the database. */
public interface SavingsQuery {

    /** Indicators of one client company, or of every company when {@code companyId} is null. */
    SavingsSummary summary(UUID companyId);

    /** Track record of a garage, optionally restricted to the quotes of one client company. */
    GarageStats garageStats(UUID garageId, UUID companyId);
}
