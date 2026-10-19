import { accessToken, login } from './auth'

/** A field refused by the service, as sent in `extensions.fieldErrors`. */
export interface FieldError {
  field: string
  /** English text of the service, for API users. */
  message: string
  /** The rule that failed (NotBlank, Size, Pattern, Email, DecimalMin...), translated by the portal. */
  constraint?: string | null
  params?: Record<string, string | number | boolean>
}

/** An error of the GraphQL gateway, with the stable code of the service (VEHICLE_NOT_FOUND, LICENSE_NOT_VALID...). */
export class GraphqlError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly fieldErrors: FieldError[] = [],
    readonly correlationId?: string,
    readonly status?: number,
  ) {
    super(message)
    this.name = 'GraphqlError'
  }
}

export interface GraphqlResult<T> {
  data: T
  /** True when the network was unavailable and the data comes from the last successful answer. */
  stale: boolean
}

interface RawError {
  message: string
  extensions?: { code?: string; classification?: string; fieldErrors?: FieldError[]; correlationId?: string; status?: number }
}

const CACHE_PREFIX = 'fleetpilot:gql:'

/**
 * Runs a GraphQL operation with the user's access token. Queries given a cache key are kept in local storage, so
 * a page already visited still shows its last data when the device is offline (PWA).
 */
export async function graphql<T>(query: string, variables: Record<string, unknown> = {},
  options: { cacheKey?: string } = {}): Promise<GraphqlResult<T>> {
  const token = await accessToken()
  if (!token) {
    await login()
    throw new GraphqlError('Not signed in', 'UNAUTHORIZED')
  }
  let response: Response
  try {
    response = await fetch(useRuntimeConfig().public.graphqlUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ query, variables }),
    })
  } catch (networkError) {
    const cached = options.cacheKey ? readCache<T>(options.cacheKey) : null
    if (cached) {
      return { data: cached, stale: true }
    }
    throw new GraphqlError('Network unavailable', 'NETWORK_ERROR')
  }
  if (response.status === 401) {
    await login()
    throw new GraphqlError('Session expired', 'UNAUTHORIZED', [], undefined, 401)
  }
  const body = (await response.json()) as { data?: T; errors?: RawError[] }
  if (body.errors?.length) {
    const first = body.errors[0]!
    const ext = first.extensions ?? {}
    throw new GraphqlError(first.message, ext.code ?? ext.classification ?? 'ERROR', ext.fieldErrors ?? [],
      ext.correlationId, ext.status)
  }
  if (options.cacheKey && body.data) {
    writeCache(options.cacheKey, body.data)
  }
  return { data: body.data as T, stale: false }
}

function readCache<T>(key: string): T | null {
  try {
    const value = localStorage.getItem(CACHE_PREFIX + key)
    return value ? (JSON.parse(value) as T) : null
  } catch {
    return null
  }
}

function writeCache(key: string, data: unknown): void {
  try {
    localStorage.setItem(CACHE_PREFIX + key, JSON.stringify(data))
  } catch {
    // Storage full or disabled: the page simply will not be available offline.
  }
}
