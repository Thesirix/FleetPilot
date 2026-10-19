# language: fr
Fonctionnalité: Extranet des loueurs
  L'application historique des loueurs (WAR sur Tomcat derrière Apache) passe par la connexion unique Keycloak et lit
  les contrats dans l'API de la flotte : un loueur ne voit que les siens.

  Scénario: Un loueur suit ses contrats et saisit un relevé de restitution
    Soit je suis connecté à l'extranet en tant que "extranet@arval.fr"
    Alors le loueur affiché est "Arval"
    Et tous les contrats listés commencent par "ARV-"
    Et l'indicateur "Contrats en cours" est supérieur à 100
    Quand je filtre sur "Dépassement kilométrique prévu"
    Et j'ouvre le premier contrat
    Et je saisis un relevé de 250 km de plus que le dernier
    Alors l'extranet confirme l'enregistrement du relevé
    Et le relevé saisi apparaît avec la source "Loueur"

  Scénario: Un loueur ne voit jamais le contrat d'un autre loueur
    Soit je suis connecté à l'extranet en tant que "extranet@arval.fr"
    Et j'ouvre le premier contrat
    Quand je me déconnecte de l'extranet
    Et je suis connecté à l'extranet en tant que "extranet@ayvens.fr"
    Et j'ouvre l'adresse du contrat précédent
    Alors l'extranet répond "Ce contrat n'existe pas ou n'est pas porté par votre société."
