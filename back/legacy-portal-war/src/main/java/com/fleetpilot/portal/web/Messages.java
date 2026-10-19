package com.fleetpilot.portal.web;

import java.util.Map;

/** What the lessor reads when something goes wrong, in French; the codes are those of the fleet API. */
public final class Messages {

    public static final String NOT_FOUND = "Ce contrat n'existe pas ou n'est pas porté par votre société.";
    public static final String SIGN_IN_FAILED = "La connexion n'a pas abouti. Réessayez dans un instant.";
    public static final String NOT_LESSOR = "Ce compte n'est pas un compte loueur : l'extranet est réservé aux sociétés de "
            + "location partenaires de FleetPilot.";
    public static final String EXPIRED_FORM = "Le formulaire a expiré : rechargez la page et recommencez.";
    public static final String INVALID_READING = "Saisissez un kilométrage en chiffres et une date valide.";
    public static final String FUTURE_READING = "Un relevé ne peut pas être daté dans le futur.";
    public static final String UNAVAILABLE = "Le service FleetPilot est momentanément indisponible. Réessayez dans "
            + "quelques minutes.";

    private static final Map<String, String> BY_CODE = Map.of(
            "MILEAGE_DECREASE", "Ce kilométrage est inférieur au dernier relevé du véhicule.",
            "VEHICLE_NOT_FOUND", NOT_FOUND,
            "CONTRACT_NOT_FOUND", NOT_FOUND,
            "RESOURCE_NOT_FOUND", NOT_FOUND,
            "VALIDATION_FAILED", INVALID_READING,
            "ACCESS_DENIED", NOT_LESSOR,
            "SERVICE_UNAVAILABLE", UNAVAILABLE);

    private static final Map<String, String> SOURCES = Map.of(
            "DRIVER", "Conducteur",
            "TELEMETRY", "Boîtier connecté",
            "GARAGE", "Garage",
            "MANAGER", "Gestionnaire",
            "IMPORT", "Import",
            "LESSOR", "Loueur");

    private Messages() {
    }

    public static String forCode(String code) {
        return BY_CODE.getOrDefault(code, UNAVAILABLE);
    }

    public static String source(String code) {
        return SOURCES.getOrDefault(code, code);
    }
}
