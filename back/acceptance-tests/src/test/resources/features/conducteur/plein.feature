# language: fr
Fonctionnalité: Application du conducteur
  Le conducteur recopie le ticket de son plein depuis son téléphone ; une faute de frappe est repérée avant l'envoi.

  Scénario: Un conducteur saisit un plein depuis son téléphone
    Soit je suis connecté au portail en tant que "jean.dupont@neolia-energies.fr" sur un téléphone
    Quand j'ouvre l'onglet "Plein" de l'application conducteur
    Et je saisis un plein de "42.5" litres pour "79.05" euros à la station "Station test Selenium"
    Alors le prix au litre affiché est "Soit 1,860 € par L"
    Quand j'enregistre avec "Enregistrer le plein"
    Alors le message "Plein enregistré" s'affiche
    Et l'historique contient "Station test Selenium"
