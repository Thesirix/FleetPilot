package com.fleetpilot.vehicle.infrastructure.demo;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** Claim types, frequencies, cost ranges and descriptions (in French, as typed by drivers) of the demo. */
final class DemoIncidents {

    static final String[] TYPES = {"COLLISION", "GLASS_BREAKAGE", "MECHANICAL_FAILURE", "VANDALISM", "WEATHER", "THEFT",
        "FIRE"};
    static final int[] TYPE_WEIGHTS = {36, 29, 16, 8, 7, 3, 1};

    /** Minimum and maximum repair cost in euros, per type. */
    private static final Map<String, int[]> COSTS = Map.of(
            "COLLISION", new int[] {450, 6800},
            "GLASS_BREAKAGE", new int[] {180, 1100},
            "MECHANICAL_FAILURE", new int[] {250, 3900},
            "VANDALISM", new int[] {300, 2600},
            "WEATHER", new int[] {400, 4500},
            "THEFT", new int[] {8500, 32000},
            "FIRE", new int[] {4000, 28000});

    private static final Map<String, List<String>> DESCRIPTIONS = Map.of(
            "COLLISION", List.of(
                    "Choc arrière à faible vitesse dans un embouteillage, le véhicule tiers n'a pas freiné à temps.",
                    "Accrochage en marche arrière sur le parking du client, rétroviseur et aile arrière gauche endommagés.",
                    "Refus de priorité d'un tiers à un rond-point, impact sur l'avant droit. Constat amiable rempli.",
                    "Contact avec un poteau lors d'une manoeuvre de livraison, pare-chocs avant fissuré.",
                    "Collision latérale en changeant de file sur voie rapide, portière conducteur enfoncée.",
                    "Véhicule percuté à l'arrêt au feu rouge, hayon déformé et feux arrière cassés."),
            "GLASS_BREAKAGE", List.of(
                    "Impact de gravillon sur le pare-brise sur autoroute, fissure qui progresse.",
                    "Pare-brise fissuré sur toute la largeur après projection d'un caillou par un poids lourd.",
                    "Vitre latérale arrière brisée, aucun objet dérobé.",
                    "Impact sur le pare-brise dans le champ de vision du conducteur, remplacement nécessaire."),
            "MECHANICAL_FAILURE", List.of(
                    "Voyant moteur allumé puis perte de puissance, véhicule remorqué au garage agréé.",
                    "Batterie déchargée à répétition, démarrage impossible ce matin au dépôt.",
                    "Bruit anormal au freinage, plaquettes et disques à contrôler.",
                    "Crevaison avec jante endommagée, roue de secours montée.",
                    "Fuite de liquide de refroidissement constatée sous le véhicule."),
            "VANDALISM", List.of(
                    "Carrosserie rayée sur tout le flanc droit pendant la nuit sur le parking du site.",
                    "Deux pneus crevés volontairement, véhicule immobilisé au dépôt.",
                    "Rétroviseurs arrachés et tags sur la carrosserie, plainte déposée."),
            "WEATHER", List.of(
                    "Grêle pendant l'orage, nombreux impacts sur le toit et le capot.",
                    "Branche tombée sur le véhicule stationné lors d'un épisode de mistral violent.",
                    "Véhicule pris dans une zone inondée lors d'un épisode cévenol, moteur à expertiser."),
            "THEFT", List.of(
                    "Véhicule volé pendant la nuit devant le domicile du conducteur, plainte déposée.",
                    "Vol de l'outillage et du chargement après effraction de la porte latérale.",
                    "Tentative de vol, neiman forcé et vitre conducteur brisée."),
            "FIRE", List.of(
                    "Départ de feu dans le compartiment moteur, éteint par les pompiers. Véhicule hors d'usage.",
                    "Véhicule incendié sur la voie publique pendant la nuit, plainte déposée."));

    private DemoIncidents() {
    }

    static double cost(String type, Random random) {
        int[] range = COSTS.get(type);
        return range[0] + random.nextDouble() * (range[1] - range[0]);
    }

    static String description(String type, Random random) {
        List<String> options = DESCRIPTIONS.get(type);
        return options.get(random.nextInt(options.size()));
    }
}
