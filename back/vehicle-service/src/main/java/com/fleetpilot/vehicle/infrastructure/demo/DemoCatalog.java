package com.fleetpilot.vehicle.infrastructure.demo;

import java.util.List;

import com.fleetpilot.vehicle.domain.model.CompanySegment;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;

/**
 * Reference data of the demo: fictitious client companies, real vehicle models with their real-world
 * characteristics (CO2, fiscal power, typical lease rent) and French first and last names.
 */
final class DemoCatalog {

    // Every company, SIREN number and e-mail domain below is fictitious (SIREN numbers pass the Luhn check only).

    private DemoCatalog() {
    }

    record SiteSpec(String name, String address, String postalCode, String city, double latitude, double longitude) {
    }

    /**
     * @param vehicles      number of vehicles
     * @param drivers       number of drivers
     * @param leaseShare    share of leased vehicles (the rest is owned)
     * @param categoryMix   weights of CAR, VAN, TRUCK, MACHINE
     * @param annualKm      typical yearly mileage of a car of this company
     */
    record CompanySpec(String code, String name, String legalName, String siren, CompanySegment segment, String city,
            String emailDomain, int vehicles, int drivers, double leaseShare, List<Integer> categoryMix, int annualKm,
            List<SiteSpec> sites, List<String[]> costCenters) {
    }

    /**
     * @param rent typical monthly rent of a 48-month / 120,000 km lease, in euros
     */
    record ModelSpec(VehicleCategory category, String make, String model, String trim, Energy energy, int co2,
            Integer fiscalPower, int rent, int weight, String wmi) {
    }

    static final List<CompanySpec> COMPANIES = List.of(
            new CompanySpec("NEO", "Néolia Énergies", "Néolia Énergies SA", "938417623", CompanySegment.LARGE_ACCOUNT,
                    "Paris", "neolia-energies.fr", 860, 700, 0.95, List.of(62, 33, 3, 2), 24_000,
                    List.of(
                            new SiteSpec("Paris - La Défense", "12 place de la Défense", "92400", "Courbevoie", 48.8918, 2.2385),
                            new SiteSpec("Lyon - Gerland", "64 avenue Tony Garnier", "69007", "Lyon", 45.7310, 4.8281),
                            new SiteSpec("Marseille - Arenc", "40 boulevard de Dunkerque", "13002", "Marseille", 43.3089, 5.3693),
                            new SiteSpec("Lille - Euralille", "3 avenue Le Corbusier", "59000", "Lille", 50.6369, 3.0716),
                            new SiteSpec("Bordeaux - Lac", "18 cours Jules Ladoumègue", "33300", "Bordeaux", 44.8853, -0.5649),
                            new SiteSpec("Nantes - Île de Nantes", "5 boulevard Vincent Gâche", "44200", "Nantes", 47.2072, -1.5507),
                            new SiteSpec("Toulouse - Basso Cambo", "2 avenue de l'Europe", "31100", "Toulouse", 43.5706, 1.3929),
                            new SiteSpec("Strasbourg - Wacken", "9 place Adrien Zeller", "67000", "Strasbourg", 48.5981, 7.7623)),
                    List.<String[]>of(
                            new String[] {"NEO-DIR", "Direction générale"},
                            new String[] {"NEO-COM", "Force commerciale"},
                            new String[] {"NEO-TEC", "Interventions techniques"},
                            new String[] {"NEO-RES", "Exploitation des réseaux"},
                            new String[] {"NEO-SUP", "Fonctions support"})),
            new CompanySpec("MAP", "Métropole Azur Provence", "Métropole Azur Provence", "275193647",
                    CompanySegment.PUBLIC_SECTOR, "Marseille", "azur-provence-metropole.fr", 440, 330, 0.5,
                    List.of(38, 47, 7, 8), 14_000,
                    List.of(
                            new SiteSpec("Marseille - Hôtel de Métropole", "21 rue Sainte", "13007", "Marseille", 43.2924, 5.3592),
                            new SiteSpec("Aix-en-Provence - Centre technique", "220 rue Jean Perrin",
                                    "13290", "Aix-en-Provence", 43.4925, 5.3611),
                            new SiteSpec("Aubagne - Dépôt des Paluds", "300 avenue du Douard", "13400", "Aubagne", 43.2917, 5.6012),
                            new SiteSpec("La Ciotat - Dépôt municipal", "12 chemin de la Garde", "13600", "La Ciotat", 43.1915, 5.6181),
                            new SiteSpec("Vitrolles - Centre d'exploitation", "4 avenue de Rome", "13127", "Vitrolles", 43.4483, 5.2451),
                            new SiteSpec("Martigues - Dépôt de Croix-Sainte", "15 route de Port-de-Bouc",
                                    "13500", "Martigues", 43.4140, 5.0285)),
                    List.<String[]>of(
                            new String[] {"MAP-VOI", "Voirie et espaces publics"},
                            new String[] {"MAP-PRO", "Propreté urbaine"},
                            new String[] {"MAP-EAU", "Eau et assainissement"},
                            new String[] {"MAP-ADM", "Administration générale"},
                            new String[] {"MAP-SEC", "Police municipale et sécurité"})),
            new CompanySpec("TCL", "Transports Calanques Logistique", "Transports Calanques Logistique SAS", "814620936",
                    CompanySegment.SME, "Vitrolles", "calanques-logistique.fr", 200, 170, 0.7, List.of(10, 55, 35, 0),
                    45_000,
                    List.of(
                            new SiteSpec("Vitrolles - Plateforme logistique", "Zone des Estroublans, 25 rue de Copenhague",
                                    "13127", "Vitrolles", 43.4312, 5.2541),
                            new SiteSpec("Fos-sur-Mer - Terminal", "Port de Fos, quai Gloria", "13270", "Fos-sur-Mer", 43.4377, 4.8566),
                            new SiteSpec("Avignon - Agence", "160 avenue de l'Amandier", "84000", "Avignon", 43.9218, 4.8605)),
                    List.<String[]>of(
                            new String[] {"TCL-MES", "Messagerie"},
                            new String[] {"TCL-LOT", "Lots complets"},
                            new String[] {"TCL-ADM", "Direction et administration"})));

    static final List<ModelSpec> MODELS = List.of(
            // Cars
            new ModelSpec(VehicleCategory.CAR, "Renault", "Clio V", "TCe 90 Evolution", Energy.PETROL, 118, 5, 289, 9, "VF1"),
            new ModelSpec(VehicleCategory.CAR, "Renault", "Megane E-Tech", "EV60 Techno", Energy.ELECTRIC, 0, 4, 449, 9, "VF1"),
            new ModelSpec(VehicleCategory.CAR, "Renault", "Scenic E-Tech", "Long Range Esprit Alpine",
                    Energy.ELECTRIC, 0, 5, 569, 4, "VF1"),
            new ModelSpec(VehicleCategory.CAR, "Peugeot", "208", "PureTech 100 Allure", Energy.PETROL, 121, 5, 299, 8, "VR3"),
            new ModelSpec(VehicleCategory.CAR, "Peugeot", "e-208", "156 ch Allure", Energy.ELECTRIC, 0, 4, 399, 7, "VR3"),
            new ModelSpec(VehicleCategory.CAR, "Peugeot", "308 SW", "Hybrid 180 e-EAT8 Allure",
                    Energy.PLUG_IN_HYBRID, 26, 8, 529, 6, "VR3"),
            new ModelSpec(VehicleCategory.CAR, "Citroën", "C4", "BlueHDi 130 EAT8 Feel", Energy.DIESEL, 117, 7, 389, 6, "VR7"),
            new ModelSpec(VehicleCategory.CAR, "Toyota", "Yaris", "Hybride 116h Dynamic", Energy.HYBRID, 92, 4, 329, 8, "VNK"),
            new ModelSpec(VehicleCategory.CAR, "Toyota", "Corolla Touring Sports", "Hybride 140h Design",
                    Energy.HYBRID, 101, 6, 419, 6, "SB1"),
            new ModelSpec(VehicleCategory.CAR, "Tesla", "Model 3", "Propulsion", Energy.ELECTRIC, 0, 3, 549, 3, "LRW"),
            new ModelSpec(VehicleCategory.CAR, "Volkswagen", "ID.3", "Pro 58 kWh Life", Energy.ELECTRIC, 0, 4, 459, 4, "WVW"),
            new ModelSpec(VehicleCategory.CAR, "Dacia", "Sandero", "ECO-G 100 Expression", Energy.LPG, 108, 5, 239, 6, "UU1"),
            new ModelSpec(VehicleCategory.CAR, "Skoda", "Octavia Combi", "2.0 TDI 116 Business", Energy.DIESEL, 112, 7, 409, 6, "TMB"),
            new ModelSpec(VehicleCategory.CAR, "Kia", "Niro EV", "Active", Energy.ELECTRIC, 0, 4, 469, 4, "KNA"),
            new ModelSpec(VehicleCategory.CAR, "BMW", "Série 3 Touring", "330e xDrive M Sport",
                    Energy.PLUG_IN_HYBRID, 37, 10, 749, 2, "WBA"),
            // Light commercial vehicles
            new ModelSpec(VehicleCategory.VAN, "Renault", "Kangoo Van", "Blue dCi 95 Grand Confort", Energy.DIESEL, 140, 6, 339, 9, "VF1"),
            new ModelSpec(VehicleCategory.VAN, "Renault", "Kangoo Van E-Tech", "Electric 45 kWh", Energy.ELECTRIC, 0, 4, 419, 6, "VF1"),
            new ModelSpec(VehicleCategory.VAN, "Citroën", "Berlingo Van", "BlueHDi 100 Club", Energy.DIESEL, 145, 6, 329, 9, "VR7"),
            new ModelSpec(VehicleCategory.VAN, "Peugeot", "e-Expert", "Standard 75 kWh", Energy.ELECTRIC, 0, 5, 589, 4, "VR3"),
            new ModelSpec(VehicleCategory.VAN, "Renault", "Trafic Fourgon", "L2H1 Blue dCi 130", Energy.DIESEL, 175, 8, 459, 7, "VF1"),
            new ModelSpec(VehicleCategory.VAN, "Renault", "Master Fourgon", "L3H2 Blue dCi 150", Energy.DIESEL, 230, 9, 529, 6, "VF1"),
            new ModelSpec(VehicleCategory.VAN, "Ford", "Transit Custom", "2.0 EcoBlue 136 Trend", Energy.DIESEL, 190, 8, 469, 5, "WF0"),
            new ModelSpec(VehicleCategory.VAN, "Fiat", "Ducato Fourgon", "3.3 L2H2 140 ch", Energy.DIESEL, 220, 9, 499, 4, "ZFA"),
            new ModelSpec(VehicleCategory.VAN, "Mercedes-Benz", "eSprinter", "Fourgon 113 kWh", Energy.ELECTRIC, 0, 7, 849, 2, "WDB"),
            new ModelSpec(VehicleCategory.VAN, "Goupil", "G6", "Benne électrique", Energy.ELECTRIC, 0, 1, 690, 2, "VJ6"),
            // Trucks (CO2 is an estimate: heavy vehicles have no WLTP value on their registration certificate)
            new ModelSpec(VehicleCategory.TRUCK, "Renault Trucks", "D Wide", "280 ch 19 t porteur",
                    Energy.DIESEL, 780, null, 2190, 6, "VF6"),
            new ModelSpec(VehicleCategory.TRUCK, "Renault Trucks", "E-Tech D", "16 t porteur", Energy.ELECTRIC, 0, null, 3990, 2, "VF6"),
            new ModelSpec(VehicleCategory.TRUCK, "Iveco", "Eurocargo", "ML120 160 ch", Energy.DIESEL, 620, null, 1690, 4, "ZCF"),
            new ModelSpec(VehicleCategory.TRUCK, "Iveco", "S-Way", "AS440 NP 460 GNV", Energy.CNG, 690, null, 2890, 2, "WJM"),
            new ModelSpec(VehicleCategory.TRUCK, "Mercedes-Benz", "Atego", "1530 L porteur", Energy.DIESEL, 700, null, 1990, 4, "WDB"),
            new ModelSpec(VehicleCategory.TRUCK, "Volvo Trucks", "FE Electric", "4x2 porteur", Energy.ELECTRIC, 0, null, 4290, 1, "YV2"),
            // Site machinery
            new ModelSpec(VehicleCategory.MACHINE, "JCB", "3CX", "Tractopelle Compact", Energy.DIESEL, 0, null, 1290, 3, "JCB"),
            new ModelSpec(VehicleCategory.MACHINE, "Manitou", "MT 625", "Chariot télescopique", Energy.DIESEL, 0, null, 990, 2, "MAN"),
            new ModelSpec(VehicleCategory.MACHINE, "Kubota", "U27-4", "Mini-pelle", Energy.DIESEL, 0, null, 690, 2, "JKU"));

    /** Drivers of Néolia Énergies who have a demo login in Keycloak: their identity never changes. */
    private static final List<String[]> FEATURED_DRIVERS = List.of(
            new String[] {"Camille", "Martin"}, new String[] {"Jean", "Dupont"}, new String[] {"Julie", "Faure"});

    /** The featured drivers and their vehicles are based in Marseille. */
    static final String FEATURED_SITE = "Marseille - Arenc";

    /** First and last name of a featured driver, or null for a generated one. */
    static String[] featuredDriver(String companyCode, int index) {
        return "NEO".equals(companyCode) && index < FEATURED_DRIVERS.size() ? FEATURED_DRIVERS.get(index) : null;
    }

    /** Employee numbers of the featured drivers: the first ones of Néolia Énergies (NEO-00001...). */
    static boolean isFeaturedEmployee(String employeeNumber) {
        for (int i = 1; i <= FEATURED_DRIVERS.size(); i++) {
            if ("NEO-%05d".formatted(i).equals(employeeNumber)) {
                return true;
            }
        }
        return false;
    }

    static final List<String> LESSORS = List.of("Ayvens", "Arval", "Leasys", "Alphabet", "Free2Move Lease", "Athlon");

    static final List<String> FIRST_NAMES = List.of(
            "Camille", "Léa", "Manon", "Chloé", "Inès", "Sarah", "Julie", "Emma", "Lucie", "Clara", "Laura", "Marine",
            "Nadia", "Sofia", "Amira", "Fatima", "Aurélie", "Céline", "Sandrine", "Nathalie", "Isabelle", "Valérie",
            "Sophie", "Elodie", "Mélanie", "Karima", "Yasmine", "Anaïs", "Pauline", "Margaux",
            "Thomas", "Nicolas", "Julien", "Maxime", "Alexandre", "Antoine", "Kevin", "Romain", "Mathieu", "Sébastien",
            "Guillaume", "Florian", "Mehdi", "Karim", "Yanis", "Sofiane", "Rachid", "Lucas", "Hugo", "Louis", "Théo",
            "Jérôme", "Frédéric", "Stéphane", "Laurent", "Philippe", "David", "Olivier", "Christophe", "Cédric",
            "Baptiste", "Adrien", "Quentin", "Benoît", "Vincent", "Franck", "Pascal", "Moussa", "Ibrahim", "Hamza");

    static final List<String> LAST_NAMES = List.of(
            "Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau", "Simon",
            "Laurent", "Lefebvre", "Michel", "Garcia", "David", "Bertrand", "Roux", "Vincent", "Fournier", "Morel",
            "Girard", "André", "Mercier", "Dupont", "Lambert", "Bonnet", "François", "Martinez", "Legrand", "Garnier",
            "Faure", "Rousseau", "Blanc", "Guerin", "Muller", "Henry", "Roussel", "Nicolas", "Perrin", "Morin",
            "Mathieu", "Clement", "Gauthier", "Dumont", "Lopez", "Fontaine", "Chevalier", "Robin", "Masson", "Sanchez",
            "Benali", "Haddad", "Mansouri", "Bouzid", "Cherif", "Nguyen", "Tran", "Da Silva", "Ferreira", "Rossi",
            "Esposito", "Bianchi", "Marchetti", "Giraud", "Olivier", "Barbier", "Arnaud", "Brun", "Rey", "Fabre",
            "Mallet", "Aubert", "Pons", "Castellan", "Maurel", "Baldacchino", "Agostini", "Santoni", "Ravel", "Meyer");

    /** Street names found in every French town, so that any of them fits any site. */
    static final List<String> STREETS = List.of("avenue de la République", "boulevard National", "rue de la Paix",
            "avenue Jean Jaurès", "rue Victor Hugo", "boulevard Gambetta", "rue Pasteur", "avenue du Général de Gaulle",
            "rue de la Gare", "place de l'Hôtel de Ville", "rue du Commerce", "avenue des Martyrs",
            "parking du centre commercial", "zone industrielle", "rond-point de l'Europe", "rue Jules Ferry");
}
