import { defineConfig, devices } from '@playwright/test'

// Runs against the platform (docker compose up).
// - tests/pwa: the installable app (PWA) on the engine of Safari (WebKit) on an iPhone, and on Chrome on Android.
//   There is no native iOS app: iPhone users install the PWA.
// - tests/site: a crawler that signs in with each demo account, opens every page it can reach by following the links
//   and reports server errors, JavaScript errors, broken links and images, error screens and untranslated texts,
//   on a desktop screen (site) and on a phone (site-phone), where no page may scroll sideways.
export default defineConfig({
  testDir: 'tests',
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost',
    locale: 'fr-FR',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'iphone-webkit', testDir: 'tests/pwa', use: { ...devices['iPhone 15'] } },
    { name: 'android-chromium', testDir: 'tests/pwa', use: { ...devices['Pixel 7'] } },
    { name: 'site', testDir: 'tests/site', timeout: 15 * 60_000,
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    // The same crawl on a phone: the portal installed as a PWA must fit the screen, without sideways scrolling
    { name: 'site-phone', testDir: 'tests/site', timeout: 15 * 60_000, use: { ...devices['Pixel 7'] } },
  ],
})
