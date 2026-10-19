// Reports built in the background through ActiveMQ, and the journal of the e-mails sent by FleetPilot.

describe('Reports', () => {
  beforeEach(() => {
    cy.login('claire.morel')
  })

  it('shows the cost indicators of the fleet', () => {
    cy.visit('/rapports')
    cy.contains('h1', 'Rapports et coûts')
    cy.contains('Coût mensuel par véhicule').parent().parent().should('contain.text', '€')
    cy.contains('Coût total du parc sur 12 mois')
    cy.contains('Loyers')
  })

  it('builds a report in the background and offers it for download', () => {
    cy.visit('/rapports')
    cy.contains('[role="radio"]', 'Échéances à traiter').click()
    cy.contains('button', 'Générer le rapport').click()
    cy.contains('Rapport demandé, préparation en cours')
    cy.contains('tbody tr', 'Échéances à traiter', { timeout: 10000 }).within(() => {
      cy.contains('.badge', 'Prêt', { timeout: 30000 })
      cy.contains('button', /Ko|Mo/)
    })
  })

  it('asks for a period for the quote export', () => {
    cy.visit('/rapports')
    cy.contains('[role="radio"]', 'Devis garages').click()
    cy.get('#from').should('be.visible')
    cy.get('#to').should('be.visible')
  })
})

describe('Journal of the e-mails', () => {
  beforeEach(() => {
    cy.login('claire.morel')
  })

  it('lists the e-mails and shows one as it was received', () => {
    cy.visit('/administration/envois?template=quote-accepted')
    cy.contains('tbody tr', 'Bon de commande : devis DEV-').click()
    cy.get('dialog[open] iframe').should('have.attr', 'srcdoc').and('contain', 'Bon de commande')
  })

  it('is not offered to a client manager', () => {
    cy.login('m.roux@neolia-energies.fr')
    cy.visit('/rapports')
    cy.contains('a', 'Journal des envois').should('not.exist')
    cy.get('select[aria-label="Entreprise"]').should('not.exist')
  })
})
