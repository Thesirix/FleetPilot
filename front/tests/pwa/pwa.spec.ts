import { expect, test, type Page } from '@playwright/test'

async function login(page: Page, username: string) {
  await page.goto('/')
  await page.locator('#username').fill(username)
  await page.locator('#password').fill('FleetPilot2026!')
  await page.locator('#kc-login').click()
  await page.waitForURL(url => !url.pathname.startsWith('/auth/'), { timeout: 20_000 })
}

test('the app can be installed: manifest, icons and Apple meta tags', async ({ page, request }) => {
  await page.goto('/auth/callback')
  const manifestHref = await page.locator('link[rel="manifest"]').getAttribute('href')
  expect(manifestHref).toBeTruthy()

  const manifest = await (await request.get(manifestHref!)).json()
  expect(manifest.name).toBe('FleetPilot')
  expect(manifest.display).toBe('standalone')
  expect(manifest.icons.map((icon: { sizes: string }) => icon.sizes)).toEqual(expect.arrayContaining(['192x192', '512x512']))
  for (const icon of manifest.icons) {
    expect((await request.get(`/${icon.src}`)).ok(), icon.src).toBeTruthy()
  }

  await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveAttribute('href', '/apple-touch-icon-180x180.png')
  await expect(page.locator('meta[name="apple-mobile-web-app-capable"]')).toHaveAttribute('content', 'yes')
  expect((await request.get('/apple-touch-icon-180x180.png')).ok()).toBeTruthy()
})

test('a driver uses their space on a phone', async ({ page }) => {
  await login(page, 'camille.martin@neolia-energies.fr')
  await expect(page).toHaveURL(/\/conducteur$/)
  await expect(page.getByRole('heading', { name: 'Bonjour Camille' })).toBeVisible()
  await expect(page.getByText('Mon véhicule')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Déclarer mon kilométrage' })).toBeVisible()

  // Nothing overflows horizontally on a narrow screen
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow).toBeLessThanOrEqual(1)
})

test('a fleet manager opens the menu on a phone', async ({ page }) => {
  await login(page, 'm.roux@neolia-energies.fr')
  await expect(page.getByRole('heading', { name: 'Tableau de bord' })).toBeVisible()
  await page.getByRole('button', { name: 'Parc' }).click()
  await page.getByRole('navigation').getByRole('link', { name: 'Véhicules', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Véhicules' })).toBeVisible()
})

test('the service worker keeps the application shell available', async ({ page, browserName }) => {
  test.skip(browserName === 'webkit', 'Playwright does not run service workers in WebKit on Windows')
  await login(page, 'm.roux@neolia-energies.fr')
  const registered = await page.evaluate(async () => {
    const registration = await navigator.serviceWorker.ready
    return Boolean(registration.active)
  })
  expect(registered).toBe(true)
})
