package com.fleetpilot.integration.journal;

/** The partner flows handled by the bus, with the folder of the inbox where their files are dropped. */
public enum Flow {

    /** Quotes in XML, sent by the garages' management software. */
    GARAGE_QUOTES("garages", ".xml", "application/xml"),
    /** Monthly rent invoices in CSV, sent by the lessors. */
    LESSOR_INVOICES("lessors", ".csv", "text/csv"),
    /** Fuel card transactions in CSV, sent by the card providers. */
    FUEL_CARDS("fuelcards", ".csv", "text/csv"),
    /** Claims declared to the insurer and followed through its REST API (no file). */
    INSURER_CLAIMS(null, null, "application/json");

    private final String folder;
    private final String extension;
    private final String contentType;

    Flow(String folder, String extension, String contentType) {
        this.folder = folder;
        this.extension = extension;
        this.contentType = contentType;
    }

    /** Sub-folder of the inbox, or null for a flow that receives no file. */
    public String folder() {
        return folder;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public boolean receivesFiles() {
        return folder != null;
    }
}
