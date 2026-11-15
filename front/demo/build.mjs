// Builds the static demo for GitHub Pages: `npm run generate:demo`
// Output: .output/public, served under /FleetPilot/, with the recorded data next to the pages.
import { spawnSync } from 'node:child_process'
import { copyFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const env = {
  ...process.env,
  NUXT_PUBLIC_DEMO: 'true',
  NUXT_APP_BASE_URL: process.env.NUXT_APP_BASE_URL ?? '/FleetPilot/',
}
const run = spawnSync('npx', ['nuxt', 'generate'], { cwd: root, env, stdio: 'inherit', shell: true })
if (run.status !== 0) process.exit(run.status ?? 1)
copyFileSync(path.join(root, 'demo', 'demo-data.json'), path.join(root, '.output', 'public', 'demo-data.json'))
console.log('Demo ready in .output/public')
