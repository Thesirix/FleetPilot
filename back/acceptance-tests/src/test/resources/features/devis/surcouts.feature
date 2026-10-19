# language: fr
Fonctionnalité: Économies sur les devis des garages
  Chaque devis est comparé ligne à ligne au tarif de référence : les pièces trop chères, les temps hors barème et les
  doublons sont signalés au gestionnaire avant qu'il négocie.

  Scénario: Repérer les lignes à négocier d'un devis
    Soit je suis connecté au portail en tant que "claire.morel"
    Quand j'ouvre la page "Devis garages" depuis le menu
    Et je choisis "À négocier" dans la liste "Statut"
    Et j'ouvre la première ligne
    Alors au moins une ligne du devis est signalée
