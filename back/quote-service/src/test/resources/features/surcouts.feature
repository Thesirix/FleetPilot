# language: fr
Fonctionnalité: Détection des surcoûts sur les devis des garages
  En tant que gestionnaire de flotte
  Je veux que chaque devis soit comparé ligne par ligne au tarif de référence
  Afin de ne payer que le juste prix et de mesurer les économies obtenues

  Contexte:
    Étant donné le garage "Garage du Prado" à Marseille
    Et le véhicule "AB-101-CD" de la flotte, une voiture

  Scénario: Un petit devis conforme est validé sans intervention humaine
    Quand le garage envoie un devis avec les lignes suivantes :
      | type          | code     | libellé                 | quantité | prix  |
      | pièce         | PLAQ-AV  | Plaquettes avant        | 1        | 45,00 |
      | main-d'oeuvre | FREIN-AV | Remplacement plaquettes | 0,8      | 72,00 |
    Alors le devis est "accepté" automatiquement pour 102,60 €
    Et aucune ligne n'est signalée

  Scénario: Une pièce trop chère et un temps hors barème sont signalés
    Quand le garage envoie un devis avec les lignes suivantes :
      | type          | code          | libellé                | quantité | prix   |
      | pièce         | DISQ-AV       | Disques avant (paire)  | 1        | 140,00 |
      | main-d'oeuvre | FREIN-AV-DISQ | Disques et plaquettes  | 2,0      | 72,00  |
    Alors le devis est "à négocier"
    Et la ligne "DISQ-AV" est signalée "pièce trop chère" avec un surcoût de 45,00 €
    Et la ligne "FREIN-AV-DISQ" est signalée "temps hors barème" avec un surcoût de 50,40 €
    Et le montant au tarif de référence est de 188,60 €

  Scénario: La négociation aboutit et l'économie est mesurée
    Étant donné un devis de 284,00 € à négocier envoyé par le garage
    Quand le gestionnaire propose 190,00 € au garage
    Et le garage accepte la contre-proposition
    Alors le devis est "accepté" pour 190,00 €
    Et l'économie réalisée est de 94,00 €

  Scénario: Le même article facturé deux fois n'est pas dû
    Quand le garage envoie un devis avec les lignes suivantes :
      | type          | code   | libellé                 | quantité | prix  |
      | main-d'oeuvre | DIAG   | Diagnostic électronique | 0,5      | 78,00 |
      | main-d'oeuvre | DIAG   | Diagnostic électronique | 0,5      | 78,00 |
      | pièce         | BATT-12V | Batterie 12 V         | 1        | 125,00 |
    Alors la ligne 2 est signalée "doublon" avec un surcoût de 39,00 €

  Scénario: Les gros devis demandent une double validation
    Étant donné un devis conforme de 5 700,00 € envoyé par le garage
    Quand un gestionnaire de flotte tente de l'accepter
    Alors il est refusé avec le message "APPROVAL_LIMIT_EXCEEDED"
    Mais un administrateur peut l'accepter

  Plan du scénario: La facture est rapprochée du montant accepté
    Étant donné un devis accepté de 102,60 €
    Quand le garage envoie une facture de <facture> €
    Alors la facture est <résultat>

    Exemples:
      | facture | résultat  |
      | 102,60  | conforme  |
      | 110,00  | conforme  |
      | 125,00  | contestée |
