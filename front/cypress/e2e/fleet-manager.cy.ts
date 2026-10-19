// A FleetPilot fleet manager (staff account from the LDAP directory) runs the fleet of every client company.

describe('Fleet manager', () => {
  beforeEach(() => {
    cy.login('claire.morel')
  })

  it('sees the indicators of the whole fleet on the dashboard', () => {
    cy.visit('/')
    cy.contains('h1', 'Tableau de bord')
    cy.contains('Véhicules en parc').parent().parent().should('contain.text', '1')
    cy.contains('Échéances en retard')
    cy.contains('Énergies du parc')
    cy.contains('Vignettes Crit\'Air')
    // Every client company of the platform, the three of the demo at least
    cy.get('select[aria-label="Entreprise"] option').should('have.length.at.least', 4)
    for (const company of ['Néolia Énergies', 'Métropole Azur Provence', 'Transports Calanques Logistique']) {
      cy.get('select[aria-label="Entreprise"]').contains('option', company)
    }
  })

  it('finds a vehicle by its registration number, whatever the spelling, and opens its file', () => {
    cy.visit('/vehicules')
    cy.get('tbody tr').first().find('.plate').invoke('text').then((plate) => {
      cy.get('input[type="search"]').type(plate.replaceAll('-', ' ').toLowerCase())
      cy.location('search').should('include', 'q=')
      cy.get('tbody tr').should('have.length', 1).click()
      cy.contains('h1', plate)
      cy.contains('Identification')
      cy.contains('[role="tab"]', 'Échéances').click()
      cy.contains('th', 'Date limite')
    })
  })

  it('filters are kept in the address, so a filtered list can be shared', () => {
    cy.visit('/vehicules?energy=ELECTRIC&status=IN_REPAIR')
    cy.get('select[aria-label="Énergie"]').should('have.value', 'ELECTRIC')
    cy.get('tbody tr').should(($rows) => {
      expect($rows.length).to.be.greaterThan(0)
      $rows.each((_, row) => {
        expect(row.textContent).to.contain('Électrique').and.to.contain('Au garage')
      })
    })
  })

  it('adds a vehicle to the fleet and gets its deadlines scheduled', () => {
    const letters = 'ABCDEFGHJKLMNPRSTVWXYZ'
    const pick = () => letters[Math.floor(Math.random() * letters.length)]
    const plate = `ZC-${String(Math.floor(Math.random() * 900) + 100)}-${pick()}${pick()}`
    const vin = `VF1CY${String(Date.now()).slice(-12)}`

    cy.visit('/vehicules/nouveau')
    cy.get('#registration').type(plate)
    cy.get('#vin').type(vin)
    cy.get('#make').type('Renault')
    cy.get('#model').type('Megane E-Tech')
    cy.get('#energy').select('ELECTRIC')
    cy.get('#critair').should('be.disabled')
    cy.get('#mileage').clear().type('25')
    cy.contains('button', 'Créer').click()

    cy.contains('h1', plate, { timeout: 15000 })
    cy.contains(`Véhicule ${plate} ajouté au parc`)
    cy.contains('[role="tab"]', 'Échéances').click()
    cy.contains('td', 'Contrôle technique')
    cy.contains('td', 'Révision')
  })

  it('refuses an invalid registration number with a clear message', () => {
    cy.visit('/vehicules/nouveau')
    cy.get('#registration').type('AB-000-CD')
    cy.get('#vin').type('VF1RJA00X68123456')
    cy.get('#make').type('Peugeot')
    cy.get('#model').type('208')
    cy.contains('button', 'Créer').click()
    cy.contains('Certains champs sont invalides')
    cy.get('#registration').should('have.attr', 'aria-invalid', 'true')
  })

  it('handles an overdue deadline from the to-do list', () => {
    cy.visit('/echeances')
    cy.contains('[role="tab"]', 'En retard').should('have.attr', 'aria-selected', 'true')
    cy.get('tbody tr').first().within(() => {
      cy.contains('jours de retard')
      cy.contains('button', 'Marquer comme réalisée').click()
    })
    cy.get('dialog[open]').within(() => {
      cy.contains('button', 'Marquer comme réalisée').click()
    })
    cy.contains('Prochaine échéance planifiée')
  })

  it('switches the interface to English', () => {
    cy.visit('/')
    cy.get('.user-menu .trigger').click()
    cy.contains('[role="menuitemradio"]', 'English').click()
    cy.contains('h1', 'Dashboard')
    cy.get('.user-menu .trigger').click()
    cy.contains('[role="menuitemradio"]', 'Français').click()
    cy.contains('h1', 'Tableau de bord')
  })
})
