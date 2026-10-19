# language: fr
Fonctionnalité: Échéances réglementaires et contrats de location longue durée
  En tant que gestionnaire de flotte
  Je veux que les échéances de chaque véhicule soient calculées et suivies automatiquement
  Afin de ne jamais laisser passer un contrôle technique ni payer un dépassement de kilométrage

  Contexte:
    Étant donné l'entreprise cliente "Néolia Énergies"
    Et nous sommes le "14/10/2026"

  Scénario: Les échéances d'une voiture neuve sont planifiées à son entrée dans la flotte
    Quand j'ajoute une voiture "ZB-201-AA" à essence immatriculée pour la première fois le "01/09/2026" avec 50 km
    Alors son "contrôle technique" est prévu le "01/09/2030"
    Et sa "révision" est prévue le "01/09/2027" ou à 20000 km
    Et son "remplacement des pneus" est prévu à 40000 km

  Scénario: Un poids lourd passe au contrôle technique chaque année et fait étalonner son chronotachygraphe
    Quand j'ajoute un camion "ZB-202-AA" immatriculé pour la première fois le "01/02/2024" avec 150000 km
    Alors son "contrôle technique" est prévu le "01/02/2027"
    Et son "chronotachygraphe" est prévu le "01/02/2028"

  Scénario: Une révision faite planifie la suivante
    Étant donné une voiture "ZB-203-AA" électrique immatriculée pour la première fois le "01/03/2025" avec 18000 km
    Quand la "révision" est réalisée aujourd'hui à 21000 km
    Alors sa "révision" est prévue le "14/10/2028" ou à 51000 km

  Scénario: Un contrat LLD parti pour dépasser son kilométrage est signalé une seule fois
    Étant donné une voiture "ZB-204-AA" électrique immatriculée pour la première fois le "01/01/2025" avec 0 km
    Et un contrat LLD chez "Arval" de 36 mois et 90000 km démarré le "01/01/2025"
    Quand le conducteur déclare 70000 km au compteur
    Alors le contrat est en risque de dépassement de plus de 25000 km
    Et le balayage quotidien signale le dépassement du contrat
    Mais un second balayage ne le signale pas à nouveau
