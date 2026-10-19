// Connected vehicles: the live map fed by the simulator, a serious fault injected in a box, the alert centre.

/** The access token of the signed-in user, read from the session storage of the portal. */
function accessToken(): Cypress.Chainable<string> {
  return cy.window().then((win) => {
    const key = Object.keys(win.sessionStorage).find(k => k.startsWith('oidc.user'))!
    return JSON.parse(win.sessionStorage.getItem(key)!).access_token as string
  })
}

/** Polls Mailpit until an e-mail matches: it leaves through Kafka, ActiveMQ and SMTP within a few seconds. */
function waitForMail(query: string, attempts = 20): void {
  cy.request(`http://localhost:8025/api/v1/search?query=${encodeURIComponent(query)}`).then((response) => {
    if (response.body.messages_count > 0) return
    expect(attempts, `e-mail matching ${query}`).to.be.greaterThan(1)
    cy.wait(1000)
    waitForMail(query, attempts - 1)
  })
}

describe('Live map', () => {
  beforeEach(() => {
    cy.login('claire.morel')
  })

  it('follows the connected vehicles live and shows the track of one of them', () => {
    cy.visit('/telemetrie')
    cy.contains('h1', 'Carte en direct')
    cy.get('[data-cy=live-indicator]', { timeout: 15000 }).should('contain.text', 'En direct')
    cy.contains(/\d+ véhicules connectés/)
    cy.get('[data-cy=fleet-map] canvas').should('exist')
    cy.get('[data-cy=vehicle-list] button').should('have.length.greaterThan', 50)

    cy.get('[data-cy=chip-MOVING]').click()
    cy.get('[data-cy=vehicle-list] button').first().click()
    cy.get('[data-cy=vehicle-detail]').within(() => {
      cy.contains('En mouvement')
      cy.contains(/\d+ km\/h/)
      cy.contains('a', 'Fiche télémétrie').click()
    })
    cy.location('search').should('contain', 'tab=telemetry')
    cy.get('[data-cy=vehicle-telemetry]', { timeout: 15000 }).within(() => {
      cy.contains('Distance sur 30 jours')
      cy.contains('Distance parcourue par jour')
      cy.contains('Derniers trajets')
    })
  })

  it('is restricted to their own company for a client manager', () => {
    cy.login('m.roux@neolia-energies.fr')
    cy.visit('/telemetrie')
    cy.get('[data-cy=live-indicator]', { timeout: 15000 }).should('contain.text', 'En direct')
    accessToken().then((token) => {
      cy.request({ url: '/api/v1/telemetry/live', headers: { Authorization: `Bearer ${token}` } }).then((response) => {
        const companies = new Set((response.body as { companyId: string }[]).map(s => s.companyId))
        expect(companies.size).to.equal(1)
      })
    })
  })
})

describe('Alert centre', () => {
  it('raises a critical alert when a box reports a serious fault, and closes it with a comment', () => {
    cy.login('theo.lambert')
    cy.visit('/telemetrie')
    accessToken().then((token) => {
      const auth = { Authorization: `Bearer ${token}` }
      // a moving vehicle without any open engine alert
      cy.request({ url: '/api/v1/telemetry/live', headers: auth }).then((live) => {
        const vehicle = (live.body as { registration: string; activity: string; openAlerts: number }[])
          .find(s => s.activity === 'MOVING' && s.openAlerts === 0)!
        cy.request({ method: 'POST', url: `http://localhost:8087/api/v1/simulator/vehicles/${vehicle.registration}/fault`,
          headers: auth, body: {} }).its('body.severity').should('eq', 'HIGH')

        cy.visit('/alertes?type=ENGINE_FAULT')
        cy.contains('tbody tr', vehicle.registration, { timeout: 20000 }).within(() => {
          cy.contains('Défaut grave')
          cy.contains('Ouverte')
          cy.contains('Défaut grave').click()
        })
        cy.get('[data-cy=alert-detail]').should('contain.text', vehicle.registration)
        cy.get('[data-cy=acknowledge]').click()
        cy.contains('Alerte prise en charge')
        cy.get('[data-cy=alert-detail]').should('contain.text', 'theo.lambert')
        cy.get('[data-cy=alert-comment]').type('Conducteur arrêté, dépanneuse envoyée au garage du Prado')
        cy.get('[data-cy=resolve]').click()
        cy.contains('Alerte close')
        cy.contains('tbody tr', vehicle.registration).should('not.exist')

        cy.visit('/alertes?status=RESOLVED&type=ENGINE_FAULT')
        cy.contains('tbody tr', vehicle.registration).should('contain.text', 'Résolue')

        // the fleet management team got the e-mail of the critical alert
        waitForMail(`subject:"Alerte critique" subject:"${vehicle.registration}"`)
      })
    })
  })
})
