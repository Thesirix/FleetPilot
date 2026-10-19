# language: fr
Fonctionnalité: Cloisonnement des données entre entreprises clientes
  En tant qu'entreprise cliente
  Je veux que mes gestionnaires ne voient que ma flotte
  Afin que les données de mes véhicules et de mes salariés restent confidentielles

  Scénario: Un gestionnaire client ne voit que la flotte de son entreprise
    Étant donné que je suis connecté en tant que gestionnaire de "Néolia Énergies"
    Quand je consulte la liste des véhicules
    Alors je ne vois que des véhicules de "Néolia Énergies"

  Scénario: Un véhicule d'une autre entreprise est introuvable, pas seulement interdit
    Étant donné que je suis connecté en tant que gestionnaire de "Néolia Énergies"
    Quand je consulte un véhicule de "Métropole Azur Provence"
    Alors le véhicule est introuvable

  Scénario: Un gestionnaire de flotte FleetPilot voit toutes les entreprises clientes
    Étant donné que je suis connecté en tant que gestionnaire de flotte FleetPilot
    Quand je consulte la liste des véhicules
    Alors je vois des véhicules de "Néolia Énergies", "Métropole Azur Provence" et "Transports Calanques Logistique"
