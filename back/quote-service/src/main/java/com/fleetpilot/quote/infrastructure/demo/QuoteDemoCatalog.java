package com.fleetpilot.quote.infrastructure.demo;

import java.util.List;
import java.util.Map;

import com.fleetpilot.quote.domain.model.LineKind;
import com.fleetpilot.quote.domain.model.Region;

/** Fictitious but realistic material of the demo: towns of the garage network, names, typical repairs. */
final class QuoteDemoCatalog {

    private QuoteDemoCatalog() {
    }

    /** A town of the network, with the streets used to name and place its garages. */
    record Town(String code, String city, String postalCode, double latitude, double longitude, int garages, List<String> places) {
    }

    /** Towns near the sites of the client companies: most vehicles are serviced close to their site. */
    static final List<Town> TOWNS = List.of(
            new Town("MRS", "Marseille", "13010", 43.2760, 5.4030, 30, List.of("Prado", "Capelette", "Saint-Loup",
                    "La Valentine", "Vieux-Port", "Arenc", "Castellane", "Saint-Just", "Les Olives", "Bonneveine")),
            new Town("AIX", "Aix-en-Provence", "13090", 43.5297, 5.4474, 10, List.of("Jas de Bouffan", "Pont de l'Arc",
                    "Les Milles", "Encagnane")),
            new Town("AUB", "Aubagne", "13400", 43.2927, 5.5708, 8, List.of("Les Paluds", "Napollon", "Charrel")),
            new Town("VIT", "Vitrolles", "13127", 43.4600, 5.2486, 9, List.of("Estroublans", "Couperigne", "Griffon")),
            new Town("MRT", "Martigues", "13500", 43.4047, 5.0476, 6, List.of("Croix-Sainte", "Ferrières", "Lavéra")),
            new Town("LCT", "La Ciotat", "13600", 43.1748, 5.6046, 5, List.of("Athélia", "Garde", "Ceyreste")),
            new Town("FOS", "Fos-sur-Mer", "13270", 43.4376, 4.9446, 5, List.of("Port", "Feuillane", "Caban")),
            new Town("AVN", "Avignon", "84000", 43.9493, 4.8055, 7, List.of("Fontcouverte", "Agroparc", "Montfavet")),
            new Town("TLN", "Toulon", "83000", 43.1242, 5.9280, 6, List.of("Mourillon", "La Garde", "Pont du Las")),
            new Town("NCE", "Nice", "06200", 43.7102, 7.2620, 6, List.of("Saint-Isidore", "Arenas", "Riquier")),
            new Town("CBV", "Courbevoie", "92400", 48.8973, 2.2534, 12, List.of("La Défense", "Bécon", "Faubourg de l'Arche",
                    "Gambetta")),
            new Town("PAR", "Paris", "75015", 48.8417, 2.3000, 10, List.of("Vaugirard", "Grenelle", "Javel", "Convention")),
            new Town("LYS", "Lyon", "69007", 45.7310, 4.8281, 12, List.of("Gerland", "Part-Dieu", "Vaise", "Monplaisir")),
            new Town("LIL", "Lille", "59000", 50.6292, 3.0573, 9, List.of("Euralille", "Fives", "Wazemmes", "Moulins")),
            new Town("BOD", "Bordeaux", "33300", 44.8637, -0.5600, 9, List.of("Bordeaux-Lac", "Bacalan", "Chartrons")),
            new Town("NTE", "Nantes", "44200", 47.2072, -1.5507, 8, List.of("Île de Nantes", "Rezé", "Saint-Herblain")),
            new Town("TLS", "Toulouse", "31100", 43.5706, 1.3929, 9, List.of("Basso Cambo", "Purpan", "Montaudran")),
            new Town("SXB", "Strasbourg", "67000", 48.5981, 7.7623, 8, List.of("Wacken", "Meinau", "Neudorf")));

    /** City of a vehicle site, mapped to the region whose garages service it. */
    static final Map<String, Region> SITE_REGIONS = Map.ofEntries(
            Map.entry("Courbevoie", Region.IDF), Map.entry("Paris", Region.IDF), Map.entry("Lyon", Region.ARA),
            Map.entry("Lille", Region.HDF), Map.entry("Bordeaux", Region.NAQ), Map.entry("Nantes", Region.PDL),
            Map.entry("Toulouse", Region.OCC), Map.entry("Strasbourg", Region.GES));

    static final List<String> CAR_BRANDS = List.of("Renault", "Peugeot", "Citroën", "Volkswagen", "Toyota", "Dacia",
            "Ford", "Fiat", "Kia", "Skoda");
    static final List<String> TRUCK_BRANDS = List.of("Renault Trucks", "Iveco", "Mercedes-Benz Trucks", "Volvo Trucks",
            "Scania");
    static final List<String> SURNAMES = List.of("Martin", "Bernard", "Roux", "Fabre", "Blanc", "Garcia", "Mercier",
            "Fontaine", "Chevalier", "Robin", "Gauthier", "Morel", "Girard", "André", "Lefèvre", "Barbier", "Arnaud",
            "Giraud", "Brun", "Rey", "Perrin", "Michel", "Bonnet", "Lambert", "Vidal", "Caron", "Rossi", "Ferrari",
            "Pons", "Esposito");

    /** A line of a typical repair: hours for labour, units otherwise. */
    record LineSpec(LineKind kind, String code, String label, double quantity) {
    }

    /** A typical repair, the kind of workshop that does it, and on which vehicles. */
    record Scenario(String title, Workshop workshop, Applies applies, int weight, List<LineSpec> lines) {
    }

    enum Workshop {
        MAINTENANCE,
        BODYWORK,
        ANY
    }

    enum Applies {
        ALL,
        COMBUSTION,
        DIESEL,
        ELECTRIC,
        TRUCK
    }

    private static LineSpec part(String code, String label, double quantity) {
        return new LineSpec(LineKind.PART, code, label, quantity);
    }

    private static LineSpec labour(String code, String label, double hours) {
        return new LineSpec(LineKind.LABOUR, code, label, hours);
    }

    private static LineSpec supplies(String label) {
        return new LineSpec(LineKind.SUPPLIES, null, label, 1);
    }

    static final List<Scenario> SCENARIOS = List.of(
            new Scenario("Révision périodique avec vidange", Workshop.MAINTENANCE, Applies.COMBUSTION, 22, List.of(
                    labour("REVISION", "Révision constructeur", 1.0),
                    part("HUILE-5W30", "Huile moteur 5W30", 5),
                    part("FILT-HUILE", "Filtre à huile", 1),
                    part("FILT-AIR", "Filtre à air", 1),
                    part("FILT-HAB", "Filtre d'habitacle", 1),
                    supplies("Ingrédients et petites fournitures"))),
            new Scenario("Remplacement des disques et plaquettes avant", Workshop.MAINTENANCE, Applies.ALL, 14, List.of(
                    labour("FREIN-AV-DISQ", "Disques et plaquettes avant", 1.3),
                    part("DISQ-AV", "Disques avant (paire)", 1),
                    part("PLAQ-AV", "Plaquettes avant", 1),
                    supplies("Nettoyant frein, graisse"))),
            new Scenario("Remplacement des disques et plaquettes arrière", Workshop.MAINTENANCE, Applies.ALL, 7, List.of(
                    labour("FREIN-AR-DISQ", "Disques et plaquettes arrière", 1.2),
                    part("DISQ-AR", "Disques arrière (paire)", 1),
                    part("PLAQ-AR", "Plaquettes arrière", 1),
                    labour("PURGE-FREIN", "Purge du circuit de freinage", 0.6),
                    part("LIQ-FREIN", "Liquide de frein DOT 4", 1))),
            new Scenario("Remplacement de deux pneumatiques", Workshop.MAINTENANCE, Applies.ALL, 10, List.of(
                    part("PNEU", "Pneumatique", 2),
                    part("VALVE", "Valve", 2),
                    labour("PNEU-MONT-2", "Montage et équilibrage (2)", 0.6))),
            new Scenario("Remplacement de quatre pneumatiques et géométrie", Workshop.MAINTENANCE, Applies.ALL, 6, List.of(
                    part("PNEU", "Pneumatique", 4),
                    part("VALVE", "Valve", 4),
                    labour("PNEU-MONT-4", "Montage et équilibrage (4)", 1.2),
                    labour("GEOMETRIE", "Réglage de la géométrie", 0.8))),
            new Scenario("Remplacement de la batterie", Workshop.MAINTENANCE, Applies.ALL, 6, List.of(
                    labour("DIAG", "Diagnostic électronique", 0.5),
                    part("BATT-12V", "Batterie 12 V", 1),
                    labour("BATTERIE", "Remplacement batterie", 0.3))),
            new Scenario("Remplacement du kit de distribution", Workshop.MAINTENANCE, Applies.COMBUSTION, 5, List.of(
                    labour("DISTRIB", "Remplacement kit de distribution", 3.5),
                    part("KIT-DIST", "Kit de distribution et pompe à eau", 1),
                    supplies("Liquide de refroidissement, joints"))),
            new Scenario("Remplacement de l'embrayage", Workshop.MAINTENANCE, Applies.COMBUSTION, 3, List.of(
                    labour("EMBRAY", "Remplacement embrayage", 5.0),
                    part("KIT-EMBR", "Kit d'embrayage", 1),
                    supplies("Graisse, visserie"))),
            new Scenario("Entretien de la climatisation", Workshop.MAINTENANCE, Applies.ALL, 5, List.of(
                    labour("CLIM", "Contrôle et recharge climatisation", 0.8),
                    part("GAZ-CLIM", "Gaz R1234yf (100 g)", 5),
                    part("FILT-DESHY", "Filtre déshydrateur", 1))),
            new Scenario("Remplacement du filtre à particules", Workshop.MAINTENANCE, Applies.DIESEL, 3, List.of(
                    labour("DIAG", "Diagnostic électronique", 0.5),
                    labour("FAP", "Remplacement FAP", 2.0),
                    part("FAP", "Filtre à particules", 1))),
            new Scenario("Remplacement de la vanne EGR", Workshop.MAINTENANCE, Applies.DIESEL, 3, List.of(
                    labour("DIAG", "Diagnostic électronique", 0.5),
                    labour("EGR", "Remplacement vanne EGR", 2.2),
                    part("VANNE-EGR", "Vanne EGR", 1))),
            new Scenario("Remplacement des amortisseurs avant", Workshop.MAINTENANCE, Applies.ALL, 4, List.of(
                    labour("AMORT-AV", "Amortisseurs avant (paire)", 1.6),
                    part("AMORT-AV", "Amortisseur avant", 2),
                    part("BIEL-STAB", "Biellette de barre stabilisatrice", 2),
                    labour("GEOMETRIE", "Réglage de la géométrie", 0.8))),
            new Scenario("Remplacement de l'alternateur", Workshop.MAINTENANCE, Applies.ALL, 2, List.of(
                    labour("DIAG", "Diagnostic électronique", 0.5),
                    labour("ALTERN", "Remplacement alternateur", 1.5),
                    part("ALTERN", "Alternateur échange standard", 1))),
            new Scenario("Contrôle de la batterie de traction", Workshop.MAINTENANCE, Applies.ELECTRIC, 6, List.of(
                    labour("EV-BATT", "Contrôle batterie de traction", 1.0),
                    labour("DIAG", "Diagnostic électronique", 0.5),
                    part("FILT-HAB", "Filtre d'habitacle", 1),
                    part("BALAIS-ESS", "Balais d'essuie-glace", 1))),
            new Scenario("Contrôle périodique du chronotachygraphe", Workshop.MAINTENANCE, Applies.TRUCK, 6, List.of(
                    labour("TACHY", "Contrôle chronotachygraphe", 1.5),
                    labour("DIAG", "Diagnostic électronique", 1.0))),
            new Scenario("Remplacement du pare-brise", Workshop.BODYWORK, Applies.ALL, 7, List.of(
                    labour("PARE-BRISE", "Remplacement pare-brise", 1.5),
                    part("PARE-BRISE", "Pare-brise", 1),
                    supplies("Colle, joint, kit de pose"))),
            new Scenario("Réparation d'un choc avant", Workshop.BODYWORK, Applies.ALL, 8, List.of(
                    labour("PARE-CHOC", "Dépose et repose pare-chocs", 1.2),
                    part("PARE-CHOC-AV", "Pare-chocs avant", 1),
                    labour("PHARE", "Remplacement optique", 0.6),
                    part("PHARE-AV", "Optique de phare avant", 1),
                    labour("PEINTURE", "Peinture pare-chocs", 2.5),
                    part("PEINT-ELEM", "Ingrédients peinture", 1),
                    supplies("Petites fournitures carrosserie"))),
            new Scenario("Remplacement d'un rétroviseur et peinture", Workshop.BODYWORK, Applies.ALL, 4, List.of(
                    labour("RETRO", "Remplacement rétroviseur", 0.5),
                    part("RETRO-EXT", "Rétroviseur extérieur", 1),
                    labour("PEINTURE", "Peinture coque de rétroviseur", 1.0),
                    part("PEINT-ELEM", "Ingrédients peinture", 1))),
            new Scenario("Débosselage et peinture d'une porte", Workshop.BODYWORK, Applies.ALL, 4, List.of(
                    labour("DEBOSSELAGE", "Débosselage porte", 1.5),
                    labour("PEINTURE", "Peinture porte", 2.5),
                    part("PEINT-ELEM", "Ingrédients peinture", 1),
                    supplies("Mastic, abrasifs"))));

    /** Lines that are not in the tariff: the analysis asks a manager to check them. */
    static final List<LineSpec> UNKNOWN_LINES = List.of(
            new LineSpec(LineKind.PART, null, "Nettoyage intérieur complet", 1),
            new LineSpec(LineKind.PART, "KIT-ATTELAGE", "Kit d'attelage spécifique", 1),
            new LineSpec(LineKind.LABOUR, "RECHERCHE-PANNE", "Recherche de panne électrique", 1.5),
            new LineSpec(LineKind.PART, "SUPPORT-MOTEUR", "Support moteur", 1));

    /** Reasons written by managers, by situation. */
    static final List<String> ACCEPT_COMMENTS = List.of(
            "Véhicule immobilisé, intervention urgente validée.",
            "Écart justifié par le garage : pièce d'origine constructeur imposée sous garantie.",
            "Seul garage disponible à proximité du site, délai prioritaire.",
            "Écart accepté après échange avec le chef d'atelier, pièce non disponible en équivalent.");
    static final List<String> REJECT_REASONS = List.of(
            "Montant hors de proportion avec la valeur du véhicule, restitution au loueur prévue.",
            "Intervention couverte par le contrat de maintenance du loueur.",
            "Le garage refuse de s'aligner sur le tarif de référence.",
            "Doublon d'un devis déjà accepté pour ce véhicule.");
    static final List<String> COUNTER_MESSAGES = List.of(
            "Merci de vous aligner sur le tarif de référence négocié pour votre région.",
            "Le temps facturé dépasse le temps barème du constructeur : nous proposons ce montant.",
            "Prix des pièces supérieur au tarif convenu : voici notre proposition.");
    static final List<String> REFUSE_MESSAGES = List.of(
            "Nous ne pouvons pas descendre en dessous de ce prix, la pièce est d'origine.",
            "Temps justifié par l'état du véhicule (corrosion importante).");
}
