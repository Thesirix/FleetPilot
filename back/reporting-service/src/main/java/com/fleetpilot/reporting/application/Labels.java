package com.fleetpilot.reporting.application;

import java.util.Map;

/** French labels of the business values written in the reports (the services expose codes). */
final class Labels {

    private static final Map<String, String> VALUES = Map.ofEntries(
            Map.entry("CAR", "VL"), Map.entry("VAN", "VU"), Map.entry("TRUCK", "PL"), Map.entry("MACHINE", "Engin"),
            Map.entry("PETROL", "Essence"), Map.entry("DIESEL", "Diesel"), Map.entry("HYBRID", "Hybride"),
            Map.entry("PLUG_IN_HYBRID", "Hybride rechargeable"), Map.entry("ELECTRIC", "Électrique"),
            Map.entry("LPG", "GPL"), Map.entry("CNG", "GNV"), Map.entry("HYDROGEN", "Hydrogène"),
            Map.entry("AWAITING_DELIVERY", "En commande"), Map.entry("ACTIVE", "En service"),
            Map.entry("IN_REPAIR", "Au garage"), Map.entry("IMMOBILIZED", "Immobilisé"), Map.entry("DISPOSED", "Sorti du parc"),
            Map.entry("TECHNICAL_INSPECTION", "Contrôle technique"), Map.entry("SERVICE", "Révision"),
            Map.entry("TYRES", "Pneumatiques"), Map.entry("TACHOGRAPH", "Chronotachygraphe"),
            Map.entry("CONTRACT_END", "Fin de contrat"), Map.entry("OVERDUE", "En retard"), Map.entry("DUE_SOON", "Bientôt"),
            Map.entry("UPCOMING", "À venir"), Map.entry("RECEIVED", "Reçu"), Map.entry("IN_REVIEW", "En analyse"),
            Map.entry("TO_NEGOTIATE", "À négocier"), Map.entry("ACCEPTED", "Accepté"), Map.entry("REJECTED", "Refusé"),
            Map.entry("COMPLETED", "Réalisé"), Map.entry("INVOICED", "Facturé"));

    private Labels() {
    }

    static String of(String code) {
        return code == null ? null : VALUES.getOrDefault(code, code);
    }
}
