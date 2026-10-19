import { readFileSync } from 'node:fs'
import { expect, type Page, test } from '@playwright/test'

/**
 * Signs in with each demo account and opens every page of the portal it can reach by following the links, the way a
 * curious user would. On each page it checks that nothing went wrong: no server error, no JavaScript error, no error
 * screen, no broken image, no text left untranslated. A detail page (vehicle, driver, quote...) is opened for a few
 * records only, not for the 1,500 vehicles.
 */

const PASSWORD = 'FleetPilot2026!'
const ACCOUNTS = [
  { username: 'claire.morel', start: '/', label: 'fleet manager (staff)' },
  { username: 'theo.lambert', start: '/administration/entreprises', label: 'platform administrator' },
  { username: 'm.roux@neolia-energies.fr', start: '/', label: 'client fleet manager' },
  { username: 'atelier@garage-du-prado.fr', start: '/espace-garage', label: 'garage' },
  { username: 'jean.dupont@neolia-energies.fr', start: '/conducteur', label: 'driver' },
]
/** Pages of one kind opened at most, and pages at most per account. */
const PER_PATTERN = 2
const MAX_PAGES = 140
const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi
/** A translation key shown instead of its text, such as "users.roleHint.garage": a family of the locale file first. */
const FAMILIES = Object.keys(JSON.parse(readFileSync(new URL('../../i18n/locales/fr.json', import.meta.url), 'utf8')))
const RAW_KEY = new RegExp(`(^|\\s)(${FAMILIES.join('|')})(\\.[a-zA-Z0-9_-]+)+(\\s|$)`)

async function signIn(page: Page, username: string, start: string) {
  await page.goto(start)
  await page.waitForSelector('#username', { timeout: 30_000 })
  await page.fill('#username', username)
  await page.fill('#password', PASSWORD)
  await page.click('#kc-login')
  // The first sign-in of a run warms Keycloak and the portal up: a longer wait than for a page
  await page.waitForURL(url => !url.href.includes('/realms/') && !url.pathname.includes('/auth/callback'), { timeout: 150_000 })
}

/** The internal links of the page, without the ones that leave the portal or sign out. */
async function links(page: Page): Promise<string[]> {
  const hrefs = await page.$$eval('a[href]', anchors => anchors.map(a => (a as HTMLAnchorElement).href))
  return hrefs
    .map(h => new URL(h))
    .filter(u => u.origin === new URL(page.url()).origin)
    .filter(u => !u.pathname.startsWith('/api/') && !u.pathname.startsWith('/auth') && !u.pathname.startsWith('/_nuxt'))
    .map(u => u.pathname + u.search)
}

for (const account of ACCOUNTS) {
  test(`${account.label}: every reachable page opens without error`, async ({ page }) => {
    const problems: string[] = []
    let current = ''
    page.on('pageerror', e => problems.push(`${current}: JavaScript error: ${e.message}`))
    page.on('console', (m) => {
      // The test browser injects its own script in every frame: the sandboxed preview of an e-mail refuses it
      const tool = m.text().includes('about:srcdoc') && m.text().includes('sandboxed')
      if (m.type() === 'error' && !tool && !m.text().includes('favicon')) problems.push(`${current}: console: ${m.text().slice(0, 200)}`)
    })
    page.on('response', (r) => {
      const url = new URL(r.url())
      if (r.status() >= 400 && url.hostname === 'localhost' && url.port !== '8180' && !url.pathname.includes('favicon')) {
        problems.push(`${current}: HTTP ${r.status()} ${url.pathname}`)
      }
    })
    page.on('response', async (r) => {
      if (!r.url().endsWith('/graphql') || r.status() !== 200) return
      try {
        const body = await r.json() as { errors?: { message: string; extensions?: { code?: string } }[] }
        body.errors?.forEach(e => problems.push(`${current}: GraphQL ${e.extensions?.code ?? ''} ${e.message}`))
      } catch {
        // a stream or an empty body
      }
    })

    await signIn(page, account.username, account.start)
    const queue = [account.start]
    const seen = new Set<string>()
    const perPattern = new Map<string, number>()
    let visited = 0

    while (queue.length && visited < MAX_PAGES) {
      const path = queue.shift()!
      const pattern = path.split('?')[0]!.replace(UUID, ':id').replace(/\/\d+$/, '/:n')
      if (seen.has(path) || (perPattern.get(pattern) ?? 0) >= PER_PATTERN) continue
      seen.add(path)
      perPattern.set(pattern, (perPattern.get(pattern) ?? 0) + 1)
      current = path
      visited++

      const response = await page.goto(path)
      expect(response?.status(), `${path} answers`).toBeLessThan(400)
      // Pages with a live stream never become idle: wait a little, then take them as they are
      await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => {})
      await page.waitForTimeout(400)

      if (await page.locator('.error.card').count()) {
        problems.push(`${path}: error screen "${(await page.locator('.error.card').first().innerText()).slice(0, 120)}"`)
      }
      if (await page.getByText('Introuvable', { exact: true }).count()) problems.push(`${path}: "Introuvable" shown`)
      // On a phone, the page must fit the width of the screen (tables scroll inside their own frame)
      if (test.info().project.name === 'site-phone') {
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
        if (overflow > 1) problems.push(`${path}: ${overflow} px wider than the phone screen`)
      }
      if (await page.locator('h1:has-text("404"), h1:has-text("Page introuvable")').count()) problems.push(`${path}: page not found`)
      const brokenImages = await page.$$eval('img', images => images
        .filter(i => (i as HTMLImageElement).complete && (i as HTMLImageElement).naturalWidth === 0)
        .map(i => (i as HTMLImageElement).src))
      brokenImages.forEach(src => problems.push(`${path}: broken image ${src}`))
      const texts = await page.$$eval('main *:not(script):not(style)', nodes => nodes
        .flatMap(n => Array.from(n.childNodes).filter(c => c.nodeType === 3).map(c => (c.textContent ?? '').trim()))
        .filter(Boolean))
      texts.filter(text => RAW_KEY.test(` ${text} `))
        .slice(0, 3).forEach(text => problems.push(`${path}: untranslated text "${text}"`))
      if (texts.some(text => text.includes('[object Object]') || /\bundefined\b|\bNaN\b/.test(text))) {
        problems.push(`${path}: "undefined", "NaN" or "[object Object]" shown`)
      }

      for (const link of await links(page)) {
        if (!seen.has(link)) queue.push(link)
      }
      // Lists open a record by a click on the row, not by a link: open the first row to reach the detail pages
      const rows = page.locator('tbody tr.clickable')
      if (await rows.count()) {
        const before = page.url()
        await rows.first().click()
        await page.waitForURL(url => url.href !== before, { timeout: 5000 }).catch(() => {})
        const target = new URL(page.url())
        if (page.url() !== before && !seen.has(target.pathname + target.search)) queue.push(target.pathname + target.search)
      }
    }

    console.log(`${account.label}: ${visited} pages opened, ${perPattern.size} kinds of page`)
    expect(visited, 'pages opened').toBeGreaterThan(account.start.startsWith('/administration') || account.start === '/' ? 8 : 3)
    expect([...new Set(problems)], 'problems found').toEqual([])
  })
}
