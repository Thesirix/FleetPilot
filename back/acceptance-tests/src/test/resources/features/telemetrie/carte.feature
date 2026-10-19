# language: fr
Fonctionnalité: Véhicules connectés
  Les boîtiers des véhicules envoient leur position et leurs compteurs par Kafka ; la carte se met à jour en direct.

  Scénario: Suivre les véhicules connectés en direct
    Soit je suis connecté au portail en tant que "claire.morel"
    Quand j'ouvre la page "Carte en direct" depuis le menu
    Alors la page indique "véhicules connectés"
