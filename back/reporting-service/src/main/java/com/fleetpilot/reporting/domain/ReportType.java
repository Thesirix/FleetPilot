package com.fleetpilot.reporting.domain;

/** The reports FleetPilot can build, each with its file format and whether it covers a period. */
public enum ReportType {

    /** Every vehicle with its driver, site, mileage and status. */
    FLEET_INVENTORY("Inventaire du parc", ReportFormat.XLSX, false),
    /** Total cost of ownership over twelve months: rent, maintenance, claims, cost per kilometre. */
    TCO("Coût total de détention (TCO)", ReportFormat.XLSX, false),
    /** Garage quotes of the period, with their analysis and the savings. */
    QUOTES("Devis garages", ReportFormat.XLSX, true),
    /** Deadlines overdue or due within 30 days. */
    DEADLINES("Échéances à traiter", ReportFormat.XLSX, false),
    /** Savings report for the client, in PDF. */
    SAVINGS("Rapport d'économies", ReportFormat.PDF, false);

    private final String title;
    private final ReportFormat format;
    private final boolean periodic;

    ReportType(String title, ReportFormat format, boolean periodic) {
        this.title = title;
        this.format = format;
        this.periodic = periodic;
    }

    public String title() {
        return title;
    }

    public ReportFormat format() {
        return format;
    }

    public boolean periodic() {
        return periodic;
    }
}
