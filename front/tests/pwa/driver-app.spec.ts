import { expect, test, type Page } from '@playwright/test'

// The driver app, on an iPhone (WebKit) and an Android phone (Chromium), against the real platform: every record
// goes through the GraphQL gateway to the vehicle service and is read back from it.

/** A 64 x 48 PNG photo (two flat bands), enough for the resizing and the upload. */
const PHOTO = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAEAAAAAwCAIAAAAuKetIAAAASElEQVR42u3PQQ0AMAgAMRxgY+p4oh8XSwi9nIFGdq0+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAP4D3vIAAAAAAAAAAAAAAO4CBhBEWchH2KQsAAAAAElFTkSuQmCC',
  'base64')

async function login(page: Page) {
  await page.goto('/conducteur')
  await page.locator('#username').fill('jean.dupont@neolia-energies.fr')
  await page.locator('#password').fill('FleetPilot2026!')
  await page.locator('#kc-login').click()
  await page.waitForURL(url => /^\/conducteur\/?$/.test(url.pathname), { timeout: 30_000 })
  await expect(page.getByRole('heading', { name: 'Bonjour Jean' })).toBeVisible()
}

/** A plausible receipt for the kind of energy the vehicle takes. */
async function fillReceipt(page: Page, quantity: number) {
  const charge = await page.getByText('Quantité (kWh)').isVisible()
  await page.locator('#quantity').fill(String(quantity))
  await page.locator('#amount').fill((quantity * (charge ? 0.32 : 1.86)).toFixed(2))
  await page.locator('#station').fill(`Station test ${quantity}`)
}

test.describe('driver app', () => {
  test.beforeEach(async ({ page }) => login(page))

  test('enters a fill-up and finds it in the history', async ({ page }) => {
    await page.getByRole('link', { name: 'Plein', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Saisir un plein' })).toBeVisible()
    const quantity = 30 + Math.floor(Math.random() * 20)
    await fillReceipt(page, quantity)
    await expect(page.getByText(/Soit .* € par/)).toBeVisible()

    await page.getByRole('button', { name: 'Enregistrer le plein' }).click()
    await expect(page.getByText('Plein enregistré')).toBeVisible()
    await expect(page.getByText(`Station test ${quantity}`).first()).toBeVisible()
  })

  test('catches a typo in the quantity before sending', async ({ page }) => {
    await page.goto('/conducteur/plein')
    await page.locator('#quantity').fill('4')
    await page.locator('#amount').fill('80')
    await expect(page.getByText(/vérifiez la quantité et le montant/)).toBeVisible()
    await expect(page.getByRole('button', { name: 'Enregistrer le plein' })).toBeDisabled()
  })

  test('records an inspection with a damaged point and a photo', async ({ page }) => {
    await page.getByRole('link', { name: 'État des lieux', exact: true }).first().click()
    await page.getByRole('radio', { name: 'Restitution' }).click()
    const tyres = page.locator('[data-item="TYRES"]')
    await tyres.getByRole('radio', { name: 'Abîmé' }).click()
    await expect(page.getByRole('button', { name: "Enregistrer l'état des lieux" })).toBeDisabled()
    await tyres.getByRole('textbox').fill('Pneu avant droit usé sur le flanc')
    await page.getByTestId('photo-input').setInputFiles({ name: 'pneu.png', mimeType: 'image/png', buffer: PHOTO })
    await expect(page.getByText('1 sur 10')).toBeVisible()

    await page.getByRole('button', { name: "Enregistrer l'état des lieux" }).click()
    await expect(page.getByText('État des lieux enregistré')).toBeVisible()
    const latest = page.locator('.list > li').filter({ hasText: 'Pneu avant droit usé sur le flanc' }).first()
    await expect(latest).toContainText('1 défaut')
    // The photo is read back from the server with the access token
    await expect(latest.locator('.thumb img')).toBeVisible()
  })

  test('keeps a fill-up on the phone without network and sends it when the network is back', async ({ page, context }) => {
    await page.goto('/conducteur/plein')
    await expect(page.locator('#quantity')).toBeVisible()
    const quantity = 50 + Math.floor(Math.random() * 20)
    await fillReceipt(page, quantity)

    await context.setOffline(true)
    await page.getByRole('button', { name: 'Enregistrer le plein' }).click()
    await expect(page.getByText("Enregistré sur le téléphone")).toBeVisible()
    await expect(page.locator('.list').getByText('En attente')).toBeVisible()
    await expect(page.locator('.outbox-pill')).toHaveText('1')

    await context.setOffline(false)
    await expect(page.locator('.outbox-pill')).toHaveCount(0, { timeout: 15_000 })
    await page.reload()
    await expect(page.getByText(`Station test ${quantity}`).first()).toBeVisible()
  })

  test('declares a claim with its GPS position and a photo, which the fleet manager then sees', async ({ page, context, browser }) => {
    await context.grantPermissions(['geolocation'])
    await context.setGeolocation({ latitude: 43.2965, longitude: 5.3698 })
    await page.goto('/conducteur/sinistre')
    await page.locator('#type').selectOption('VANDALISM')
    await page.getByRole('button', { name: 'Utiliser ma position' }).click()
    await expect(page.locator('#where')).toHaveValue('Position GPS 43.29650, 5.36980')
    const description = `Rétroviseur arraché sur le parking, test ${Date.now()}`
    await page.locator('#what').fill(description)
    await page.getByTestId('photo-input').setInputFiles({ name: 'retro.png', mimeType: 'image/png', buffer: PHOTO })
    await expect(page.getByText('1 sur 10')).toBeVisible()
    // The reference is given by the server; both phones of the test run declare claims at the same time
    const answer = page.waitForResponse(response => response.url().endsWith('/graphql')
      && (response.request().postData() ?? '').includes('declareMyIncidentWithId'))
    await page.getByRole('button', { name: 'Envoyer la déclaration' }).click()
    const reference: string = (await (await answer).json()).data.declareMyIncidentWithId.reference
    await expect(page.getByText('Sinistre déclaré')).toBeVisible()
    await expect(page.locator('.list').getByText(reference)).toBeVisible()

    // The fleet manager opens the claim and sees the photo taken by the driver
    const manager = await browser.newContext({ locale: 'fr-FR' })
    const desk = await manager.newPage()
    await desk.goto('/sinistres?sort=reference,desc')
    await desk.locator('#username').fill('m.roux@neolia-energies.fr')
    await desk.locator('#password').fill('FleetPilot2026!')
    await desk.locator('#kc-login').click()
    await desk.getByText(reference).first().click()
    await expect(desk.getByText(description)).toBeVisible()
    await expect(desk.getByRole('heading', { name: 'Photos du conducteur' })).toBeVisible()
    await expect(desk.locator('.thumb img')).toHaveCount(1)
    await manager.close()
  })

  test('finds the approved garages around the phone', async ({ page, context }) => {
    await context.grantPermissions(['geolocation'])
    await context.setGeolocation({ latitude: 43.2951, longitude: 5.3741 })
    await page.goto('/conducteur/garages')
    await expect(page.getByText(/garages agréés à moins de 25 km de votre position/)).toBeVisible()
    const first = page.locator('.garage').first()
    await expect(first).toContainText('km')
    await expect(first.getByRole('link', { name: 'Itinéraire' })).toHaveAttribute('href', /destination=43\./)
    await expect(page.locator('.garage-pin')).not.toHaveCount(0)
  })

  test('lists the notifications and fits a phone screen', async ({ page }) => {
    await page.goto('/conducteur/notifications')
    await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible()
    for (const path of ['/conducteur', '/conducteur/plein', '/conducteur/etat-des-lieux', '/conducteur/sinistre', '/conducteur/garages']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow, path).toBeLessThanOrEqual(1)
    }
  })
})
