package com.fleetpilot.notification.application;

import java.util.Map;

import com.fleetpilot.documents.DocumentFormats;

/**
 * Formats of the e-mail templates ({@code ${fmt.money(amount)}}), plus the French labels of the business values
 * carried by the events (deadline types, claim types...).
 */
public final class MailFormats extends DocumentFormats {

    public static final MailFormats INSTANCE = new MailFormats();

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("TECHNICAL_INSPECTION", "contrôle technique"), Map.entry("SERVICE", "révision"),
            Map.entry("TYRES", "remplacement des pneumatiques"), Map.entry("TACHOGRAPH", "contrôle du chronotachygraphe"),
            Map.entry("CONTRACT_END", "fin du contrat de location"),
            Map.entry("PERMANENT", "attribution permanente"), Map.entry("TEMPORARY", "prêt temporaire"),
            Map.entry("COLLISION", "collision"), Map.entry("GLASS_BREAKAGE", "bris de glace"), Map.entry("THEFT", "vol"),
            Map.entry("VANDALISM", "vandalisme"), Map.entry("WEATHER", "événement climatique"), Map.entry("FIRE", "incendie"),
            Map.entry("MECHANICAL_FAILURE", "panne mécanique"),
            Map.entry("ENGINE_FAULT", "défaut grave"), Map.entry("ENGINE_WARNING", "défaut à surveiller"),
            Map.entry("SPEEDING", "excès de vitesse"), Map.entry("LOW_BATTERY", "batterie faible"),
            Map.entry("PART", "Pièce"), Map.entry("LABOUR", "Main-d'œuvre"), Map.entry("SUPPLIES", "Ingrédients"));

    private MailFormats() {
    }

    /** French label of a business value (deadline type, claim type...), or the value itself. */
    public String label(Object value) {
        return value == null ? "-" : LABELS.getOrDefault(value.toString(), value.toString());
    }
}
