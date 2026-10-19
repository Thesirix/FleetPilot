# language: fr
Fonctionnalité: Affectation d'un véhicule à un conducteur
  En tant que gestionnaire de flotte
  Je veux confier chaque véhicule à un conducteur habilité
  Afin que chaque véhicule ait un responsable et que personne ne conduise sans le bon permis

  Contexte:
    Étant donné l'entreprise cliente "Néolia Énergies"

  Scénario: Affecter une voiture disponible à un conducteur titulaire du permis B
    Étant donné un véhicule "ZA-101-AA" de catégorie "VL" au statut "en service" avec 15000 km au compteur
    Et un conducteur "Camille Martin" titulaire du permis "B"
    Quand j'affecte le véhicule "ZA-101-AA" à "Camille Martin"
    Alors l'affectation est acceptée
    Et le conducteur actuel du véhicule "ZA-101-AA" est "Camille Martin"
    Et le kilométrage de prise en charge est de 15000 km

  Plan du scénario: Refuser une affectation contraire aux règles
    Étant donné un véhicule "<véhicule>" de catégorie "<catégorie>" au statut "<statut>" avec 30000 km au compteur
    Et un conducteur "Jean Dupont" titulaire du permis "<permis>"
    Quand j'affecte le véhicule "<véhicule>" à "Jean Dupont"
    Alors l'affectation est refusée avec le motif "<motif>"

    Exemples:
      | véhicule  | catégorie | statut     | permis | motif                 |
      | ZA-102-AA | PL        | en service | B      | LICENSE_NOT_VALID     |
      | ZA-103-AA | VL        | au garage  | B      | VEHICLE_NOT_AVAILABLE |
      | ZA-104-AA | VU        | immobilisé | B      | VEHICLE_NOT_AVAILABLE |

  Scénario: Un chauffeur poids lourd peut conduire un camion
    Étant donné un véhicule "ZA-105-AA" de catégorie "PL" au statut "en service" avec 120000 km au compteur
    Et un conducteur "Rachid Haddad" titulaire du permis "B,C,CE"
    Quand j'affecte le véhicule "ZA-105-AA" à "Rachid Haddad"
    Alors l'affectation est acceptée

  Scénario: Un véhicule n'a qu'un seul conducteur à la fois
    Étant donné un véhicule "ZA-106-AA" de catégorie "VU" au statut "en service" avec 42000 km au compteur
    Et un conducteur "Sophie Rossi" titulaire du permis "B"
    Et un conducteur "Thomas Garnier" titulaire du permis "B"
    Et le véhicule "ZA-106-AA" est affecté à "Sophie Rossi"
    Quand j'affecte le véhicule "ZA-106-AA" à "Thomas Garnier"
    Alors l'affectation est refusée avec le motif "VEHICLE_ALREADY_ASSIGNED"

  Scénario: La restitution enregistre les kilomètres parcourus
    Étant donné un véhicule "ZA-107-AA" de catégorie "VL" au statut "en service" avec 20000 km au compteur
    Et un conducteur "Julie Faure" titulaire du permis "B"
    Et le véhicule "ZA-107-AA" est affecté à "Julie Faure"
    Quand "Julie Faure" restitue le véhicule avec 23450 km au compteur
    Alors le véhicule "ZA-107-AA" n'a plus de conducteur
    Et l'affectation totalise 3450 km parcourus
    Et le kilométrage du véhicule "ZA-107-AA" est de 23450 km
