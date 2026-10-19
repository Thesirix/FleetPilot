import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('~/utils/auth', () => ({
  accessToken: vi.fn(async () => 'token-123'),
  login: vi.fn(async () => {}),
}))

const { graphql, GraphqlError } = await import('~/utils/graphql')
const auth = await import('~/utils/auth')

function answer(body: unknown, status = 200) {
  return vi.fn(async () => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
}

describe('graphql()', () => {
  beforeEach(() => localStorage.clear())
  afterEach(() => vi.unstubAllGlobals())

  it('sends the query with the access token', async () => {
    const fetchMock = answer({ data: { viewer: { username: 'claire.morel' } } })
    vi.stubGlobal('fetch', fetchMock)

    const result = await graphql<{ viewer: { username: string } }>('{ viewer { username } }', { a: 1 })

    expect(result.data.viewer.username).toBe('claire.morel')
    expect(result.stale).toBe(false)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/graphql')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-123')
    expect(JSON.parse(init.body as string)).toEqual({ query: '{ viewer { username } }', variables: { a: 1 } })
  })

  it('turns a service error into a GraphqlError with its code and field errors', async () => {
    vi.stubGlobal('fetch', answer({
      data: null,
      errors: [{ message: 'The request contains invalid values.', extensions: { code: 'VALIDATION_FAILED', status: 400,
        correlationId: 'abc-12345678', fieldErrors: [{ field: 'registration', message: 'must be valid' }] } }],
    }))

    const error = await graphql('mutation { x }').catch(e => e)

    expect(error).toBeInstanceOf(GraphqlError)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.status).toBe(400)
    expect(error.correlationId).toBe('abc-12345678')
    expect(error.fieldErrors).toEqual([{ field: 'registration', message: 'must be valid' }])
  })

  it('sends the user back to the login page when the session expired', async () => {
    vi.stubGlobal('fetch', answer({}, 401))

    await expect(graphql('{ viewer { username } }')).rejects.toMatchObject({ code: 'UNAUTHORIZED' })
    expect(auth.login).toHaveBeenCalled()
  })

  it('serves the last answer when the device is offline', async () => {
    vi.stubGlobal('fetch', answer({ data: { vehicles: { totalElements: 1500 } } }))
    await graphql('{ vehicles { totalElements } }', {}, { cacheKey: 'vehicles' })

    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    const offline = await graphql<{ vehicles: { totalElements: number } }>('{ vehicles { totalElements } }', {},
      { cacheKey: 'vehicles' })

    expect(offline.stale).toBe(true)
    expect(offline.data.vehicles.totalElements).toBe(1500)
    await expect(graphql('{ other }', {}, { cacheKey: 'never-loaded' })).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })
})
