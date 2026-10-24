// Administration by a platform administrator: client companies with their sites and cost centers, user accounts,
// analysis rules, electric models, low emission zones and the activity journal. Every form is sent once with valid
// values and once with a mistake, which must be explained in French next to the field or in a message.
// What it creates carries "Cypress" or "cy-" and is removed by tools/scripts/clean-test-data.sh.

const stamp = () => String(Date.now()).slice(-6)

describe('Administration', () => {
  beforeEach(() => cy.login('theo.lambert'))

  it('creates a client company, refuses a wrong SIREN, then edits it', () => {
    const name = `Cypress Logistique ${stamp()}`
    cy.visit('/administration/entreprises/nouvelle')
    cy.get('#c-name').type(name)
    cy.get('#c-legal').type(`${name} SAS`)
    cy.get('#c-siren').type('1234')
    cy.get('#c-city').type('Marseille')
    cy.contains('button', 'Créer').click()
    cy.get('#c-siren').should('have.attr', 'aria-invalid', 'true')
    cy.contains('.field-error', 'Le SIREN compte 9 chiffres.')

    cy.get('#c-siren').clear().type(`99${stamp()}0`)
    cy.get('#c-postal').type('13002')
    cy.get('#c-contact').type('Lucie Ferrand')
    cy.get('#c-email').type('l.ferrand@example.test')
    cy.contains('button', 'Créer').click()
    cy.contains(`Entreprise ${name} créée`)
    cy.contains('h1', name)

    cy.contains('button', 'Modifier').click()
    cy.get('#c-phone').type('04 91 00 00 00')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains(`Entreprise ${name} mise à jour`)
    cy.contains('04 91 00 00 00')
  })

  it('adds, edits and deletes a site and a cost center, and refuses a duplicate code', () => {
    cy.visit('/administration/entreprises')
    cy.get('.filters input').type('Cypress')
    cy.get('tbody tr').first().click()

    cy.contains('button', 'Ajouter un site').click()
    cy.get('#s-name').type('Marseille - La Joliette')
    cy.get('#s-address').type('10 place de la Joliette')
    cy.get('#s-postal').type('13002')
    cy.get('#s-city').type('Marseille')
    cy.get('#s-lat').type('43.3047')
    cy.get('#s-lon').type('5.3667')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('Site Marseille - La Joliette enregistré')
    cy.contains('td', 'Marseille - La Joliette').parents('tr').find('button[aria-label="Modifier"]').click()
    cy.get('#s-address').clear().type('12 place de la Joliette')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('12 place de la Joliette')

    cy.contains('button', 'Ajouter un centre de coût').click()
    cy.get('#cc-code').type('CY-OPS')
    cy.get('#cc-label').type('Exploitation')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('Centre de coût CY-OPS enregistré')
    cy.contains('button', 'Ajouter un centre de coût').click()
    cy.get('#cc-code').type('cy-ops')
    cy.get('#cc-label').type('Doublon')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('Ce code de centre de coût existe déjà.')
    cy.get('dialog[open]').contains('button', 'Annuler').click()

    cy.contains('td', 'CY-OPS').parents('tr').find('button[aria-label="Supprimer"]').click()
    cy.get('dialog[open]').contains('button', 'Supprimer').click()
    cy.contains('Centre de coût CY-OPS supprimé')
    cy.contains('td', 'Marseille - La Joliette').parents('tr').find('button[aria-label="Supprimer"]').click()
    cy.get('dialog[open]').contains('button', 'Supprimer').click()
    cy.contains('Site Marseille - La Joliette supprimé')
  })

  it('does not delete a site that still holds vehicles', () => {
    cy.visit('/administration/entreprises')
    cy.contains('tbody tr', 'Néolia Énergies').click()
    cy.contains('td', 'Marseille - Arenc').parents('tr').find('button[aria-label="Supprimer"]').should('be.disabled')
  })

  it('creates the account of a client manager, shows the temporary password once, then edits and disables it', () => {
    const email = `cy-${stamp()}@example.test`
    cy.visit('/administration/utilisateurs/nouveau')
    cy.get('#u-first').type('Lucie')
    cy.get('#u-last').type('Ferrand')
    cy.get('#u-email').type(email)
    cy.get('#u-role').select('client-manager')
    cy.get('#u-company').select('Métropole Azur Provence')
    cy.contains('button', 'Créer le compte').click()
    cy.contains('Compte créé')
    cy.get('[data-testid=temporary-password]').invoke('text').should('match', /^[A-Za-z0-9]{10}\d!$/)
    cy.get('dialog[open]').contains('button', 'Terminé').click()
    cy.contains('h1', 'Lucie Ferrand')
    cy.contains('Métropole Azur Provence')

    cy.contains('button', 'Modifier').click()
    cy.get('#u-last').clear().type('Ferrand-Roche')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('Compte de Lucie Ferrand-Roche mis à jour')

    cy.contains('button', 'Nouveau mot de passe').click()
    cy.get('dialog[open]').contains('button', 'Nouveau mot de passe').click()
    cy.get('[data-testid=temporary-password]').should('be.visible')
    cy.get('dialog[open]').contains('button', 'Terminé').click()

    cy.contains('button', 'Désactiver').click()
    cy.get('dialog[open]').contains('button', 'Désactiver').click()
    cy.contains('Compte de Lucie Ferrand-Roche désactivé')
    cy.get('.badge').contains('Désactivé')
  })

  it('refuses an account without what its role is attached to, or with an e-mail already used', () => {
    cy.visit('/administration/utilisateurs/nouveau')
    cy.get('#u-first').type('Marc')
    cy.get('#u-last').type('Giraud')
    cy.get('#u-email').type('atelier@garage-du-prado.fr')
    cy.get('#u-role').select('garage')
    cy.get('#u-garage').select(1)
    cy.contains('button', 'Créer le compte').click()
    cy.contains('Un compte utilise déjà cet e-mail.')

    cy.get('#u-email').clear().type(`cy-${stamp()}@example.test`)
    cy.get('#u-role').select('api-supplier')
    cy.get('#u-lessor').invoke('removeAttr', 'required')
    cy.contains('button', 'Créer le compte').click()
    cy.contains('Choisissez le loueur du compte.')
  })

  it('shows the staff accounts of the directory as read only', () => {
    cy.visit('/administration/utilisateurs?role=fleet-manager')
    cy.contains('tbody tr', 'Claire Morel').click()
    cy.contains("annuaire de l'entreprise (LDAP)")
    cy.contains('button', 'Modifier').should('not.exist')
  })

  it('changes a rule of the quote analysis, refuses an automatic approval above the delegation, then restores it', () => {
    cy.visit('/administration/parametres')
    // the form is filled once the saved rules have arrived: typing before would mix both values
    cy.get('#r-auto').should('not.have.value', '0').clear().type('9000')
    cy.contains('button', 'Enregistrer les règles').click()
    cy.contains('La validation automatique doit rester sous la délégation d\'un gestionnaire.')

    cy.get('#r-auto').clear().type('320')
    cy.contains('button', 'Enregistrer les règles').click()
    cy.contains('Règles d\'analyse enregistrées')
    cy.visit('/tarif')
    cy.contains(/conformes jusqu.à 320\s€ HT sont validés automatiquement/)

    cy.visit('/administration/parametres')
    cy.get('#r-auto').should('have.value', '320').clear().type('300')
    cy.contains('button', 'Enregistrer les règles').click()
    cy.contains('Règles d\'analyse enregistrées')
  })

  it('adds, edits and removes an electric model and a low emission zone', () => {
    const code = `CY-${stamp()}`
    cy.visit('/administration/parametres?tab=models')
    cy.contains('button', 'Ajouter un modèle').click()
    cy.get('#m-code').type(code)
    cy.get('#m-make').type('Cypress')
    cy.get('#m-model').type('Test 90 kWh')
    cy.get('#m-rent').clear().type('9999')
    cy.get('#m-range').invoke('removeAttr', 'min').clear().type('20')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('La valeur minimale est 50.')
    cy.get('#m-range').clear().type('520')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains('Cypress Test 90 kWh enregistré', { timeout: 30000 })
    cy.contains('td', code).parents('tr').find('button[aria-label="Supprimer"]').click()
    cy.get('dialog[open]').contains('button', 'Supprimer').click()
    cy.contains('Cypress Test 90 kWh supprimé', { timeout: 30000 })

    cy.visit('/administration/parametres?tab=zones')
    cy.contains('button', 'Ajouter une zone').click()
    cy.get('#z-city').type(`Cypressville ${code}`)
    cy.get('#z-zone').type('ZFE de Cypressville')
    cy.get('#z-critair').select('3')
    cy.get('dialog[open]').contains('button', 'Enregistrer').click()
    cy.contains(`Zone de Cypressville ${code} enregistrée`, { timeout: 30000 })
    cy.contains('td', `Cypressville ${code}`).parents('tr').find('button[aria-label="Supprimer"]').click()
    cy.get('dialog[open]').contains('button', 'Supprimer').click()
    cy.contains('supprimé', { timeout: 30000 })
  })

  it('lists the actions just done in the activity journal', () => {
    cy.visit('/administration/journal?period=1')
    cy.contains('td', 'Entreprise créée')
    cy.contains('td', 'Compte créé')
    cy.get('.filters select').first().select('Comptes')
    cy.contains('td', 'Compte créé')
    cy.contains('td', 'Mot de passe réinitialisé')
    cy.contains('td', 'Entreprise créée').should('not.exist')
  })

  it('opens the profile of the user', () => {
    cy.visit('/profil')
    cy.contains('h1', 'Mon profil')
    cy.contains('Théo Lambert')
    cy.contains('Toutes les entreprises clientes')
    cy.contains('a', 'Changer mon mot de passe').should('have.attr', 'href').and('include', '/realms/fleetpilot/account')
  })
})
