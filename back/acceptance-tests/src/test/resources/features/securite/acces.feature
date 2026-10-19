# language: fr
Fonctionnalité: Chacun accède à son espace, et seulement au sien
  Les rôles viennent de Keycloak : une gestionnaire cliente ne voit que la flotte de son entreprise, un conducteur
  ne voit que son espace.

  Scénario: Une gestionnaire cliente ne voit que la flotte de son entreprise
    Soit je suis connecté au portail en tant que "m.roux@neolia-energies.fr"
    Quand j'ouvre la page "Véhicules" depuis le menu
    Alors le titre de la page est "Véhicules"
    Et le choix de l'entreprise n'est pas proposé

  Scénario: Un conducteur est ramené à son espace, basé à Marseille
    Soit je suis connecté au portail en tant que "jean.dupont@neolia-energies.fr" sur un téléphone
    Quand j'ouvre l'adresse "/vehicules"
    Alors je suis dans l'espace conducteur et je lis "Bonjour Jean"
    Et le site de mon véhicule est "Marseille - Arenc"
