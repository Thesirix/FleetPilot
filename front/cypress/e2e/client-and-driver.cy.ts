// A client company only sees its own fleet; a driver only sees their own space.

describe('Client fleet manager', () => {
  beforeEach(() => {
    cy.login('m.roux@neolia-energies.fr')
  })

  it('only sees the fleet of Néolia Énergies', () => {
    cy.visit('/vehicules')
    cy.get('select[aria-label="Entreprise"]').should('not.exist')
    cy.get('tbody tr').should('have.length.greaterThan', 5)
    cy.visit('/conducteurs')
    cy.get('tbody tr').first().should('contain.text', '@neolia-energies.fr')
  })

  it('reads the fill-ups and the inspections sent by the driver of a vehicle', () => {
    // GR-832-SJ, driven by Jean Dupont in Marseille, who uses the driver app in the demo data set
    cy.visit('/vehicules/affc7578-af6d-3f9a-b9be-6c3aaa1ac63c?tab=fuel')
    cy.contains('[role=tab]', 'Pleins saisis').should('have.attr', 'aria-selected', 'true')
    cy.get('tbody tr').should('have.length.greaterThan', 2)
    cy.get('tbody tr').first().should('contain.text', 'Jean Dupont').and('contain.text', '€')
    cy.contains('[role=tab]', 'États des lieux').click()
    cy.get('tbody tr').first().click()
    cy.get('.detail').should('contain.text', 'Carrosserie').and('contain.text', 'Papiers du véhicule')
  })

  it('is told that a vehicle of another company does not exist', () => {
    // First vehicle of Métropole Azur Provence in the deterministic demo data set
    cy.visit('/vehicules/1436b3c0-38bd-343b-9618-6d8556704694')
    cy.contains('Introuvable')
  })
})

describe('Driver', () => {
  beforeEach(() => {
    cy.viewport('iphone-x')
    cy.login('camille.martin@neolia-energies.fr')
  })

  it('lands on their own space and cannot reach the fleet pages', () => {
    cy.visit('/vehicules')
    cy.location('pathname').should('eq', '/conducteur')
    cy.contains('h1', 'Bonjour Camille')
    cy.contains('Mon véhicule')
    cy.contains('Prochaines échéances')
  })

  it('reports the mileage of their vehicle', () => {
    cy.visit('/conducteur')
    cy.get('.facts strong').first().invoke('text').then((text) => {
      const current = Number(text.replace(/\D/g, ''))
      cy.contains('button', 'Déclarer mon kilométrage').click()
      cy.get('#km').clear().type(String(current + 37))
      cy.get('dialog[open]').contains('button', 'Enregistrer').click()
      cy.contains('Kilométrage enregistré')
      cy.get('.facts strong').first().should('contain.text', (current + 37).toLocaleString('fr-FR').slice(-3))
    })
  })
})
