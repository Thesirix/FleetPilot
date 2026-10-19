// Savings module, end to end: a garage sends an overpriced quote, the analysis flags it, the fleet manager negotiates,
// the garage accepts the counter-offer and the saving shows up.

const GARAGE = 'atelier@garage-du-prado.fr'
const MANAGER = 'claire.morel'
/** A Renault Trafic of Métropole Azur Provence (Marseille), stable across installations (deterministic demo data). */
const VAN = 'AA-040-VR'

describe('Garage quote, from submission to saving', () => {
  let quotePath = ''
  let reference = ''

  it('the garage sends a quote and sees the lines above the reference tariff', () => {
    cy.login(GARAGE)
    cy.visit('/espace-garage/nouveau')
    cy.get('#registration').type(VAN)
    cy.get('#garage-reference').type(`CY-${Date.now()}`)
    cy.get('#mileage').type('98520')
    cy.get('#description').type('Remplacement des disques et plaquettes avant')

    cy.get('tbody tr').eq(0).within(() => {
      cy.get('select').eq(1).select('FREIN-AV-DISQ')
      cy.get('input[aria-label="Heures"]').clear().type('2.5')
      cy.get('input[aria-label="Taux HT"]').clear().type('72')
    })
    cy.get('tbody tr').eq(1).within(() => {
      cy.get('select').eq(1).select('DISQ-AV')
      cy.get('input[aria-label="Prix HT"]').clear().type('180')
    })
    cy.contains('button', 'Ajouter une ligne').click()
    cy.get('tbody tr').eq(2).within(() => {
      cy.get('select').eq(1).select('PLAQ-AV')
      cy.get('input[aria-label="Prix HT"]').clear().type('58')
    })
    cy.contains('button', 'Envoyer le devis').click()

    cy.location('pathname', { timeout: 15000 }).should('match', /^\/devis\//)
    cy.contains('Pièce trop chère')
    cy.contains('Temps hors barème')
    cy.contains('.badge', 'À négocier')
    cy.get('h1').invoke('text').then((text) => { reference = text.trim() })
    cy.location('pathname').then((path) => { quotePath = path })
  })

  it('the fleet manager finds it in the queue and sends a counter-offer at the reference tariff', () => {
    cy.login(MANAGER)
    cy.visit('/devis?status=TO_NEGOTIATE')
    cy.get('input[type="search"]').type(reference)
    cy.get('tbody tr').should('have.length', 1).click()
    cy.contains('h1', reference)
    cy.contains('button', 'Contre-proposer').click()
    cy.get('#counter-amount').should('not.have.value', '')
    cy.get('#counter-message').type('Alignement sur le tarif de référence de la région')
    cy.get('dialog[open]').contains('button', 'Contre-proposer').click()
    cy.contains('Contre-proposition envoyée au garage')
    cy.contains('en attente de réponse du garage')
  })

  it('the garage accepts the counter-offer and the saving is recorded', () => {
    cy.login(GARAGE)
    cy.visit('/espace-garage')
    cy.contains('Contre-propositions à étudier')
    cy.visit(quotePath)
    cy.contains('button', 'Répondre à la contre-proposition').click()
    cy.get('dialog[open]').contains('button', 'Accepter ce montant').click()
    cy.contains('.badge', 'Accepté')
    cy.contains('Économie')
    cy.contains('Contre-proposition acceptée')
  })

  it('the savings appear for the fleet manager', () => {
    cy.login(MANAGER)
    cy.visit('/economies')
    cy.contains('h1', 'Économies réalisées')
    cy.contains('Économies obtenues')
    cy.contains('Garages à surveiller')
    cy.get('canvas').should('exist')
  })
})

describe('Garage space', () => {
  beforeEach(() => {
    cy.login(GARAGE)
  })

  it('keeps the garage in its own space', () => {
    cy.visit('/vehicules')
    cy.location('pathname').should('eq', '/espace-garage')
    cy.contains('h1', 'Garage du Prado')
    cy.contains('Mes devis')
  })

  it('shows the reference tariff and its rules to the garage', () => {
    cy.visit('/tarif')
    cy.contains('Règles d\'analyse')
    cy.contains('td', 'PLAQ-AV')
    cy.contains('[role="tab"]', 'Taux horaires').click()
    cy.contains('td', 'Provence-Alpes-Côte d\'Azur')
  })
})

describe('Fleet manager and the garage network', () => {
  beforeEach(() => {
    cy.login(MANAGER)
  })

  it('reads the track record of a garage', () => {
    cy.visit('/garages?q=prado')
    cy.contains('tbody tr', 'Garage du Prado').click()
    cy.contains('h1', 'Garage du Prado')
    cy.contains('Taux de surcoût')
    cy.contains('Derniers devis')
  })

  it('sees the garage quotes of a vehicle in its file', () => {
    cy.visit('/vehicules?q=AA040VR')
    cy.get('tbody tr').should('have.length', 1).click()
    cy.contains('h1', VAN)
    cy.contains('[role="tab"]', 'Devis').click()
    cy.get('tbody tr').should('have.length.greaterThan', 0)
  })
})
