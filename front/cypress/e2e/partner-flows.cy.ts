// The integration bus seen from the portal: the journal of the partner flows, a garage quote dropped by hand and
// handled by the Camel route, and the fuel card transactions with their anomalies.

function quoteXml(reference: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<devis xmlns="urn:fleetpilot:devis-garage:1" version="1.0">
  <emetteur siret="45601270032976">Garage du Prado</emetteur>
  <reference>${reference}</reference>
  <dateEmission>2026-10-15</dateEmission>
  <vehicule immatriculation="FR-404-KZ" kilometrage="54210"/>
  <objet>Remplacement des plaquettes avant</objet>
  <lignes>
    <ligne nature="PIECE" code="PLAQ-AV">
      <libelle>Plaquettes de frein avant (jeu)</libelle>
      <quantite>1</quantite>
      <prixUnitaireHT>45.00</prixUnitaireHT>
    </ligne>
  </lignes>
  <totalHT>45.00</totalHT>
</devis>
`
}

describe('Partner flows', () => {
  beforeEach(() => {
    cy.login('theo.lambert')
  })

  it('shows the four flows and what became of each file, line by line', () => {
    cy.visit('/administration/flux')
    cy.contains('h1', 'Flux partenaires')
    cy.get('[data-cy=flows]').within(() => {
      cy.contains('Devis des garages')
      cy.contains('Factures des loueurs')
      cy.contains('Cartes carburant')
      cy.contains('Sinistres assureur')
    })
    cy.visit('/administration/flux?status=PARTIAL&flow=LESSOR_INVOICES')
    cy.contains('tbody tr', 'arv_factures_').click()
    cy.get('[data-cy=exchange-detail]').within(() => {
      cy.contains('lignes à contester')
      cy.contains('Loyer facturé')
      cy.contains('Contrat terminé le')
    })
    cy.get('[data-cy=replay]').should('be.visible')
  })

  it('handles a quote file dropped by hand through the Camel route', () => {
    const reference = `CY-${Date.now()}`
    cy.visit('/administration/flux')
    cy.get('[data-cy=drop-file]').click()
    cy.get('[data-cy=drop-input]').selectFile({ contents: Cypress.Buffer.from(quoteXml(reference)),
      fileName: `devis-${reference}.xml`, mimeType: 'application/xml' })
    cy.get('[data-cy=drop-send]').click()
    cy.contains('Fichier déposé')
    cy.contains('tbody tr', `devis-${reference}.xml`, { timeout: 20000 }).within(() => {
      cy.contains('Traité')
      cy.contains('véhicule à identifier')
    })
  })
})

describe('Fuel cards', () => {
  it('lists the transactions and filters the anomalies', () => {
    cy.login('claire.morel')
    cy.visit('/carburant')
    cy.contains('h1', 'Cartes carburant')
    cy.contains('Dépense carburant').parent().parent().should('contain.text', '€')
    cy.get('[data-cy=fuel-anomalies]').contains('button', 'Mauvais carburant').click()
    cy.location('search').should('contain', 'anomaly=WRONG_FUEL')
    cy.get('tbody tr').should('have.length', 3).each(row => cy.wrap(row).should('contain.text', 'Mauvais carburant'))
  })
})
