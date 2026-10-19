# language: fr
Fonctionnalité: Administration des entreprises clientes
  En tant qu'administrateur de la plateforme, j'ajoute une entreprise cliente, ses sites et ses centres de coût, et
  le journal d'activité garde la trace de qui l'a fait. Un gestionnaire client n'a pas accès à l'administration.

  Scénario: Un administrateur ajoute une entreprise cliente et la retrouve dans le journal
    Soit je suis connecté au portail en tant que "theo.lambert"
    Quand j'ouvre la page "Entreprises clientes" depuis le menu
    Et je clique sur "Nouvelle entreprise"
    Et je remplis le champ "Entreprise" avec "Cypress Selenium {n}"
    Et je remplis le champ "Raison sociale" avec "Cypress Selenium SAS"
    Et je remplis le champ "SIREN" avec "97{n}"
    Et je remplis le champ "Ville" avec "Marseille"
    Et je clique sur "Créer"
    Alors un message confirme "Entreprise Cypress Selenium {n} créée"
    Et le titre de la page est "Cypress Selenium {n}"
    Quand j'ouvre la page "Journal d'activité" depuis le menu
    Alors le tableau contient "Entreprise créée"
    Et le tableau contient "Cypress Selenium"

  Scénario: Un gestionnaire client ne voit pas l'administration de la plateforme
    Soit je suis connecté au portail en tant que "m.roux@neolia-energies.fr"
    Alors le menu propose "Véhicules"
    Et le menu ne propose pas "Entreprises clientes"
    Et le menu ne propose pas "Utilisateurs"
