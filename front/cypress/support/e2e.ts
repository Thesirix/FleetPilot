/// <reference types="cypress" />

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Signs in through the Keycloak login page (cached per user between tests). */
      login(username: string): Chainable<void>
    }
  }
}

/** Keycloak of the local platform and the password shared by every demo account. */
const KEYCLOAK_URL = 'http://localhost:8180'
const DEMO_PASSWORD = 'FleetPilot2026!'

Cypress.Commands.add('login', (username: string) => {
  cy.session(username, () => {
    cy.visit('/')
    cy.origin(KEYCLOAK_URL, { args: { username, password: DEMO_PASSWORD } },
      ({ username, password }) => {
        cy.get('#username').type(username)
        cy.get('#password').type(password, { log: false })
        cy.get('#kc-login').click()
      })
    cy.location('pathname', { timeout: 20000 }).should('not.include', '/auth/callback')
  }, {
    validate() {
      cy.window().then(win => {
        const stored = Object.keys(win.sessionStorage).some(key => key.startsWith('oidc.user'))
        expect(stored, 'signed in').to.equal(true)
      })
    },
  })
})

export {}
