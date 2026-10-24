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

// The portal follows the language of the browser: the tests read French labels, whatever the machine
// (the GitHub runners are in English)
const BROWSER_LANGUAGE = 'fr-FR'
Cypress.on('window:before:load', win => {
  Object.defineProperty(win.navigator, 'language', { value: BROWSER_LANGUAGE })
  Object.defineProperty(win.navigator, 'languages', { value: [BROWSER_LANGUAGE, 'fr'] })
})

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
