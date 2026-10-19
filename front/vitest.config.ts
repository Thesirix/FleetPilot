import { fileURLToPath } from 'node:url'
import { defineVitestConfig } from '@nuxt/test-utils/config'

// Unit tests of the portal logic, run in a Nuxt environment (auto-imports, i18n) with happy-dom.
export default defineVitestConfig({
  test: {
    environment: 'nuxt',
    include: ['tests/unit/**/*.test.ts'],
    // One Nuxt environment at a time: suites booting it in parallel race on the generated files
    fileParallelism: false,
    // Booting the Nuxt environment can take a while on a loaded machine (Docker builds running)
    hookTimeout: 60_000,
    environmentOptions: {
      nuxt: { rootDir: fileURLToPath(new URL('.', import.meta.url)), domEnvironment: 'happy-dom' },
    },
  },
})
