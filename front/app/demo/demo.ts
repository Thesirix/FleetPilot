// Static demo (GitHub Pages): no Keycloak, no services. The answers of the GraphQL gateway and of the REST
// endpoints were recorded from the real platform (demo dataset) and are replayed here. Changes are refused.

interface Recording {
  gql: Record<string, unknown>
  rest: Record<string, { status: number; type: string; body: string }>
}

let recording: Promise<Recording> | null = null

export const isDemo = () => String(useRuntimeConfig().public.demo) === 'true'

function load(): Promise<Recording> {
  if (!recording) {
    const base = useRuntimeConfig().app.baseURL
    recording = window.fetch(`${base}demo-data.json`).then(r => r.json() as Promise<Recording>)
  }
  return recording
}

const operationName = (query: string) => query.replace(/\s+/g, ' ').match(/^\s*(?:query\s*\w*\s*(?:\([^)]*\))?\s*)?\{\s*(\w+)/)?.[1] ?? query

/** The recorded answer of a query: same query and variables, otherwise the closest recorded variables. */
export async function demoGraphql(query: string, variables: Record<string, unknown>): Promise<unknown> {
  if (/^\s*mutation/.test(query)) {
    return { errors: [{ message: 'Demo: changes are disabled', extensions: { code: 'DEMO_READ_ONLY' } }] }
  }
  const { gql } = await load()
  const exact = gql[JSON.stringify([query, variables])]
  if (exact) {
    return exact
  }
  let best: unknown = null
  let bestScore = -1
  const name = operationName(query)
  for (const [key, answer] of Object.entries(gql)) {
    const [recordedQuery, recordedVariables] = JSON.parse(key) as [string, Record<string, unknown>]
    const sameQuery = recordedQuery === query
    if (!sameQuery && operationName(recordedQuery) !== name) {
      continue
    }
    const score = (sameQuery ? 100 : 0)
      + Object.entries(variables).filter(([k, v]) => JSON.stringify(recordedVariables[k]) === JSON.stringify(v)).length
    if (score > bestScore) {
      best = answer
      bestScore = score
    }
  }
  return best ?? { errors: [{ message: 'Not available in the demo', extensions: { code: 'DEMO_NOT_RECORDED' } }] }
}

/** Serves the REST calls of the portal (/api/...) from the recording. */
export function installDemoFetch(): void {
  const realFetch = window.fetch.bind(window)
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const path = url.startsWith('/') ? url : new URL(url, window.location.href).pathname
    if (!path.startsWith('/api/')) {
      return realFetch(input, init)
    }
    const method = (init?.method ?? 'GET').toUpperCase()
    if (method !== 'GET') {
      return new Response(JSON.stringify({ code: 'DEMO_READ_ONLY', message: 'Demo: changes are disabled' }),
        { status: 403, headers: { 'Content-Type': 'application/json' } })
    }
    const { rest } = await load()
    const hit = rest[path] ?? Object.entries(rest).find(([key]) => key.split('?')[0] === path.split('?')[0])?.[1]
    if (!hit) {
      // The live telemetry stream and unrecorded files: an empty answer, the page keeps its recorded data
      return new Response(path.includes('/stream') ? '' : '[]', { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    const bytes = Uint8Array.from(atob(hit.body), c => c.charCodeAt(0))
    return new Response(bytes, { status: hit.status, headers: { 'Content-Type': hit.type } })
  }
}
