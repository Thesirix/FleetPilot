// The forms of the fleet, each sent with a mistake (the service explains it in French) and then with valid values:
// editing a vehicle and a driver, recording and ending a lease contract, adding and editing a garage, and a garage
// sending a new version of its quote. What it creates carries "Cypress", "CY-" or "cy-" and is removed by
// tools/scripts/clean-test-data.sh.

const stamp = () => String(Date.now()).slice(-6)
const letters = 'ABCDEFGHJKLMNPRTVWXYZ' // no S: the pair SS is never issued
const pick = () => letters[Math.floor(Math.random() * letters.length)]

describe('Forms of the fleet', () => {
  beforeEach(() => cy.login('claire.morel'))

  it('edits a vehicle, refuses a wrong registration, then records, opens and ends its lease contract', () => {
    const plate = `ZC-${String(Math.floor(Math.random() * 900) + 100)}-${pick()}${pick()}`
    cy.visit('/vehicules/nouveau')
    cy.get('#registration').type(plate)
    cy.get('#vin').type(`VF1CY${String(Date.now()).slice(-12)}`)
    cy.get('#make').type('Renault')
    cy.get('#model').type('Kangoo E-Tech')
    cy.get('#energy').select('ELECTRIC')
    cy.get('#category').select('VAN')
    cy.contains('button', 'Créer').click()
    cy.contains('h1', plate, { timeout: 15000 })

    cy.contains('a', 'Modifier').click()
    cy.contains('h1', `Modifier ${plate}`)
    cy.get('#vin').should('be.disabled')
    cy.get('#registration').clear().type('AB-000-CD')
    cy.contains('button', 'Enregistrer').click()
    cy.contains('Immatriculation invalide (format AB-123-CD).')
    cy.get('#registration').clear().type(plate)
    cy.get('#trim').type('Grand Confort')
    cy.contains('button', 'Enregistrer').click()
    cy.contains(`Véhicule ${plate} mis à jour`)
    cy.contains('Kangoo E-Tech Grand Confort')

    cy.contains('button', 'Ajouter un contrat').click()
    cy.get('#number').type(`CY-2026-${stamp()}`)
    cy.get('#months').invoke('removeAttr', 'min').clear().type('3')
    cy.get('dialog[open]').contains('button', 'Créer').click()
    cy.contains('La valeur minimale est 6.')
    cy.get('#months').clear().type('48')
    cy.get('#km').clear().type('120000')
    cy.get('#rent').clear().type('512.40')
    cy.get('dialog[open]').contains('button', 'Créer').click()
    cy.contains(/Contrat CY-2026-\d+ enregistré pour/)

    cy.get('a[href^="/contrats/"]').first().click()
    cy.contains('h1', /CY-2026-/)
    cy.contains('Avancement')
    cy.contains('Conditions')
    cy.contains('button', 'Terminer le contrat').click()
    cy.get('dialog[open]').contains('button', 'Terminer le contrat').click()
    cy.contains('Contrat terminé')
  })

  it('creates a driver, refuses an e-mail already used, then edits and deactivates the driver', () => {
    const email = `cy-${stamp()}@example.test`
    cy.visit('/conducteurs/nouveau')
    cy.get('#fn').type('Inès')
    cy.get('#ln').type('Cypress')
    cy.get('#email').type('camille.martin@neolia-energies.fr')
    cy.get('#num').type(`CY-${stamp()}`)
    cy.get('#lic').type(`CY${stamp()}`)
    cy.get('#issued').type('2015-06-01')
    cy.contains('button', 'Créer').click()
    cy.contains('e-mail')
    cy.get('#email').clear().type(email)
    cy.contains('button', 'Créer').click()
    cy.contains('h1', 'Inès Cypress', { timeout: 15000 })

    cy.contains('a', 'Modifier').click()
    cy.get('#phone').type('06 12 34 56 78')
    cy.get('label.checkbox').contains('C1').click()
    cy.contains('button', 'Enregistrer').click()
    cy.contains('Conducteur Inès Cypress mis à jour')
    cy.contains('C1')

    cy.contains('a', 'Modifier').click()
    cy.get('#active').uncheck()
    cy.contains('button', 'Enregistrer').click()
    cy.contains('.badge', 'Inactif')
  })

  it('adds a garage, refuses a wrong SIRET, then edits its hourly rate', () => {
    const name = `Garage Cypress ${stamp()}`
    cy.visit('/garages')
    cy.contains('a', 'Nouveau garage').click()
    cy.get('#name').type(name)
    cy.get('#siret').type('123')
    cy.get('#rate').type('68')
    cy.get('#address').type('5 boulevard Rabatau')
    cy.get('#postal').type('13008')
    cy.get('#city').type('Marseille')
    cy.get('#lat').type('43.2747')
    cy.get('#lon').type('5.3921')
    cy.get('#email').type(`cy-${stamp()}@example.test`)
    cy.contains('button', 'Créer').click()
    cy.contains('Le SIRET compte 14 chiffres.')
    cy.get('#siret').clear().type(`99${String(Date.now()).slice(-12)}`)
    cy.contains('button', 'Créer').click()
    cy.contains(`Garage ${name} ajouté au réseau`)
    cy.contains('h1', name)
    cy.contains('Non agréé')

    cy.contains('a', 'Modifier').click()
    cy.get('#rate').clear().type('71.50')
    cy.contains('button', 'Enregistrer').click()
    cy.contains(`Garage ${name} mis à jour`)
    cy.contains(/71,50\s€/)
  })
})

describe('A garage sends a new version of its quote', () => {
  it('opens its quote, corrects a line and sends it again for analysis', () => {
    cy.login('atelier@garage-du-prado.fr')
    cy.visit('/espace-garage/nouveau')
    cy.get('#registration').type('AA-040-VR')
    cy.get('#garage-reference').type(`CY-${Date.now()}`)
    cy.get('#description').type('Remplacement des plaquettes avant')
    cy.get('tbody tr').eq(0).within(() => {
      cy.get('select').eq(1).select('FREIN-AV-DISQ')
      cy.get('input[aria-label="Heures"]').clear().type('2.5')
      cy.get('input[aria-label="Taux HT"]').clear().type('72')
    })
    cy.get('tbody tr').eq(1).within(() => {
      cy.get('select').eq(1).select('PLAQ-AV')
      cy.get('input[aria-label="Prix HT"]').clear().type('90')
    })
    cy.contains('button', 'Envoyer le devis').click()
    cy.location('pathname', { timeout: 15000 }).should('match', /^\/devis\//)

    cy.contains('a', 'Envoyer une nouvelle version').click()
    cy.contains('h1', 'Nouvelle version du devis')
    cy.get('#registration').should('be.disabled')
    cy.get('tbody tr').eq(1).within(() => {
      cy.get('input[aria-label="Prix HT"]').clear().type('58')
    })
    cy.get('#message').type('Plaquettes au prix du tarif')
    cy.contains('button', 'Envoyer le devis').click()
    cy.contains(/Version 2 envoyée/)
    cy.location('pathname').should('match', /^\/devis\//)
  })
})
