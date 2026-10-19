// The energy transition: eligibility of the fleet from its real usage, the greening plan and the quotas, and the
// reasoning behind the proposal for one vehicle.

describe('Energy transition', () => {
  beforeEach(() => {
    cy.login('claire.morel')
  })

  it('shows the fleet eligibility, the plan and the quotas of the law', () => {
    cy.visit('/transition')
    cy.contains('h1', 'Transition énergétique')
    cy.contains('Part zéro émission').parent().parent().should('contain.text', '%')
    cy.contains('Économie annuelle possible').parent().parent().should('contain.text', '€')
    cy.get('[data-cy=quota]').should('have.length.at.least', 2).first().should('contain.text', 'renouvellements')
    cy.contains('Plan de verdissement').parents('.card').find('tbody tr').should('have.length.at.least', 4)
  })

  it('filters the vehicles by eligibility and explains a proposal', () => {
    cy.visit('/transition')
    cy.get('[data-cy=eligibility]').contains('button', 'Éligible avec recharge').click()
    cy.location('search').should('contain', 'status=ELIGIBLE_WITH_CHARGING')
    // the table is reloaded with the filter: the assertion retries until every row is of the right kind
    cy.get('[data-cy=assessments] tbody tr').should((rows) => {
      expect(rows.length).to.be.at.least(5)
      rows.each((_, row) => { expect(row.innerText).to.contain('Éligible avec recharge') })
    })
    cy.get('[data-cy=assessments] tbody tr').first().find('td').first().click()
    cy.get('[data-cy=assessment]').within(() => {
      cy.contains('dépasse')
      cy.contains('Modèle électrique proposé')
      cy.contains('Coût annuel en électrique')
      cy.contains('a', 'Ouvrir la fiche du véhicule')
    })
  })

  it('never proposes a smaller vehicle: a large van gets a large electric van', () => {
    cy.visit('/transition?q=Master&category=VAN&status=ELIGIBLE')
    cy.get('[data-cy=assessments] tbody tr').should((rows) => {
      expect(rows.length).to.be.at.least(1)
      rows.each((_, row) => { expect(row.innerText).to.contain('Master Van E-Tech') })
    })
  })

  it('shows a client manager their own fleet only', () => {
    cy.login('m.roux@neolia-energies.fr')
    cy.visit('/transition')
    cy.get('select[aria-label="Entreprise"]').should('not.exist')
    cy.get('[data-cy=assessments] tbody tr').first().should('be.visible')
  })
})
