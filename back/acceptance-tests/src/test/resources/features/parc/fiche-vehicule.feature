# language: fr
Fonctionnalité: Fiche d'un véhicule
  En tant que gestionnaire de flotte, je retrouve un véhicule par son immatriculation et je lis ce que son conducteur
  a saisi depuis son téléphone.

  Scénario: Retrouver un véhicule et lire les pleins saisis par son conducteur
    Soit je suis connecté au portail en tant que "claire.morel"
    Quand j'ouvre la page "Véhicules" depuis le menu
    Et je recherche "GR-832-SJ"
    Et j'ouvre la première ligne
    Alors le titre de la page est "GR-832-SJ"
    Quand j'ouvre l'onglet "Pleins saisis"
    Alors le tableau contient "Jean Dupont"
