import { describe, expect, it } from 'vitest'
import {
  RejectedError, TransientError, flushOutbox, newId, newItem, type OutboxItem, type OutboxPhoto, type OutboxStore,
  type OutboxTransport,
} from '~/utils/outbox'

function memoryStore(): OutboxStore & { items: Map<string, OutboxItem> } {
  const items = new Map<string, OutboxItem>()
  return {
    items,
    all: async () => [...items.values()].map(item => structuredClone(item)),
    put: async (item) => { items.set(item.id, structuredClone(item)) },
    remove: async (id) => { items.delete(id) },
  }
}

/** A server that records what it receives and answers as scripted, call after call. */
function server(script: Array<'ok' | 'down' | 'refuse'> = []) {
  const records: string[] = []
  const photos: string[] = []
  let call = 0
  function answer(): void {
    const next = script[call++] ?? 'ok'
    if (next === 'down') throw new TransientError('network')
    if (next === 'refuse') throw new RejectedError('IMPLAUSIBLE_PRICE', 'price')
  }
  const transport: OutboxTransport = {
    async sendRecord(_mutation, variables) {
      answer()
      records.push(String(variables.id))
    },
    async sendPhoto(path) {
      answer()
      photos.push(path)
    },
  }
  return { transport, records, photos }
}

const photo = (id: string): OutboxPhoto => ({ id, bytes: new ArrayBuffer(8), contentType: 'image/jpeg' })

function fillUp(minute: number, photos: OutboxPhoto[] = []): OutboxItem {
  const id = newId()
  return newItem('fuel', 'Plein', 'mutation', { id }, {
    id, photos, photoPath: `/api/v1/me/incidents/${id}/photos`, now: new Date(Date.UTC(2026, 9, 15, 8, minute)),
  })
}

describe('outbox of the driver app', () => {
  it('sends the waiting items oldest first and empties itself', async () => {
    const store = memoryStore()
    const second = fillUp(5)
    const first = fillUp(1)
    await store.put(second)
    await store.put(first)
    const { transport, records } = server()

    const report = await flushOutbox(store, transport)

    expect(report).toEqual({ sent: 2, rejected: 0, interrupted: false })
    expect(records).toEqual([first.id, second.id])
    expect(store.items.size).toBe(0)
  })

  it('keeps everything when the network is down and stops at the first failure', async () => {
    const store = memoryStore()
    await store.put(fillUp(1))
    await store.put(fillUp(2))
    const { transport, records } = server(['down'])

    const report = await flushOutbox(store, transport)

    expect(report.interrupted).toBe(true)
    expect(records).toHaveLength(0)
    expect([...store.items.values()].map(i => i.attempts).sort()).toEqual([0, 1])
  })

  it('resumes a claim where it stopped: the record and the photos already sent are not sent again', async () => {
    const store = memoryStore()
    const claim = fillUp(1, [photo('p1'), photo('p2'), photo('p3')])
    await store.put(claim)
    // record ok, first photo ok, then the network drops
    const first = server(['ok', 'ok', 'down'])
    await flushOutbox(store, first.transport)
    const halfway = store.items.get(claim.id)!
    expect(halfway.recordSent).toBe(true)
    expect(halfway.photosSent).toEqual(['p1'])

    const second = server()
    const report = await flushOutbox(store, second.transport)

    expect(report.sent).toBe(1)
    expect(second.records).toHaveLength(0)
    expect(second.photos).toEqual([`/api/v1/me/incidents/${claim.id}/photos/p2`, `/api/v1/me/incidents/${claim.id}/photos/p3`])
    expect(store.items.size).toBe(0)
  })

  it('sets aside an item refused by the server and goes on with the next ones', async () => {
    const store = memoryStore()
    const refused = fillUp(1)
    const fine = fillUp(2)
    await store.put(refused)
    await store.put(fine)
    const { transport, records } = server(['refuse', 'ok'])

    const report = await flushOutbox(store, transport)

    expect(report).toEqual({ sent: 1, rejected: 1, interrupted: false })
    expect(records).toEqual([fine.id])
    expect(store.items.get(refused.id)?.error).toEqual({ code: 'IMPLAUSIBLE_PRICE', message: 'price' })

    // A refused item is not sent again by the next flushes: the driver corrects or removes it
    const again = server()
    await flushOutbox(store, again.transport)
    expect(again.records).toHaveLength(0)
  })

  it('generates version 4 UUIDs', () => {
    const ids = new Set(Array.from({ length: 50 }, () => newId()))
    expect(ids.size).toBe(50)
    for (const id of ids) {
      expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    }
  })
})
