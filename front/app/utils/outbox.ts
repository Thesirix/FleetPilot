/**
 * Outbox of the driver app: what the driver enters (fill-up, inspection, claim with photos, mileage) is first written
 * to the phone, then sent. Without network it waits and leaves on its own when the connection is back.
 *
 * Every record carries an id generated on the phone and the API endpoints are idempotent on it: when an answer is
 * lost and the record is sent a second time, the server returns the one it already has. A record is sent before its
 * photos; each photo has its own id too, so a half-sent claim resumes where it stopped.
 */

export type OutboxKind = 'mileage' | 'fuel' | 'inspection' | 'incident'

/** The image is kept as bytes: Safari does not always accept a Blob in IndexedDB, every browser accepts bytes. */
export interface OutboxPhoto {
  id: string
  bytes: ArrayBuffer
  contentType: string
}

export interface OutboxItem {
  id: string
  kind: OutboxKind
  /** What the driver sees in the list of pending items: "Plein du 15/10, 42,17 L". */
  summary: string
  mutation: string
  variables: Record<string, unknown>
  photos: OutboxPhoto[]
  /** REST path of the photos of this record, without the photo id: /api/v1/me/incidents/{id}/photos */
  photoPath: string | null
  recordSent: boolean
  photosSent: string[]
  createdAt: string
  attempts: number
  /** Refused by the server for a business reason: it will not go through as it is, the driver has to act. */
  error: { code: string; message: string } | null
}

/** Where the items wait: IndexedDB in the browser, a map in the unit tests. */
export interface OutboxStore {
  all(): Promise<OutboxItem[]>
  put(item: OutboxItem): Promise<void>
  remove(id: string): Promise<void>
}

/** What the outbox needs to send: the GraphQL mutation of the record, then each photo. */
export interface OutboxTransport {
  sendRecord(mutation: string, variables: Record<string, unknown>): Promise<void>
  sendPhoto(path: string, photo: OutboxPhoto): Promise<void>
}

/** The network or the server is unavailable: try again later, the item is fine. */
export class TransientError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'TransientError'
  }
}

/** The server refused the item (validation, business rule): sending it again would give the same answer. */
export class RejectedError extends Error {
  constructor(readonly code: string, message: string) {
    super(message)
    this.name = 'RejectedError'
  }
}

export interface FlushReport {
  sent: number
  rejected: number
  /** True when the flush stopped because the network or the server was unavailable. */
  interrupted: boolean
}

export function newItem(kind: OutboxKind, summary: string, mutation: string, variables: Record<string, unknown>,
  options: { id?: string; photos?: OutboxPhoto[]; photoPath?: string | null; now?: Date } = {}): OutboxItem {
  return {
    id: options.id ?? newId(),
    kind,
    summary,
    mutation,
    variables,
    photos: options.photos ?? [],
    photoPath: options.photoPath ?? null,
    recordSent: false,
    photosSent: [],
    createdAt: (options.now ?? new Date()).toISOString(),
    attempts: 0,
    error: null,
  }
}

/**
 * Sends the waiting items, oldest first. An item refused by the server is kept with its error and skipped; a network
 * failure stops the flush, since the next items would fail the same way.
 */
export async function flushOutbox(store: OutboxStore, transport: OutboxTransport): Promise<FlushReport> {
  const report: FlushReport = { sent: 0, rejected: 0, interrupted: false }
  const items = (await store.all())
    .filter(item => item.error === null)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  for (const item of items) {
    try {
      await sendItem(store, transport, item)
      await store.remove(item.id)
      report.sent++
    } catch (e) {
      if (e instanceof RejectedError) {
        await store.put({ ...item, attempts: item.attempts + 1, error: { code: e.code, message: e.message } })
        report.rejected++
        continue
      }
      await store.put({ ...item, attempts: item.attempts + 1 })
      report.interrupted = true
      break
    }
  }
  return report
}

async function sendItem(store: OutboxStore, transport: OutboxTransport, item: OutboxItem): Promise<void> {
  if (!item.recordSent) {
    await transport.sendRecord(item.mutation, item.variables)
    item.recordSent = true
    await store.put(item)
  }
  for (const photo of item.photos) {
    if (item.photosSent.includes(photo.id) || !item.photoPath) {
      continue
    }
    await transport.sendPhoto(`${item.photoPath}/${photo.id}`, photo)
    item.photosSent = [...item.photosSent, photo.id]
    await store.put(item)
  }
}

/** A random UUID (version 4), also outside secure contexts where crypto.randomUUID is missing. */
export function newId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

// ---------------------------------------------------------------- IndexedDB store

const DB_NAME = 'fleetpilot-driver'
const STORE = 'outbox'

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = () => request.result.createObjectStore(STORE, { keyPath: 'id' })
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function run<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest): Promise<T> {
  return openDb().then(db => new Promise<T>((resolve, reject) => {
    const tx = db.transaction(STORE, mode)
    const request = action(tx.objectStore(STORE))
    tx.oncomplete = () => {
      db.close()
      resolve(request.result as T)
    }
    tx.onerror = () => {
      db.close()
      reject(tx.error)
    }
  }))
}

/** Items and their photos (as Blobs) survive a closed tab, a restarted phone and a lost network. */
export const indexedDbStore: OutboxStore = {
  all: () => run<OutboxItem[]>('readonly', store => store.getAll()),
  put: item => run<void>('readwrite', store => store.put(item)),
  remove: id => run<void>('readwrite', store => store.delete(id)),
}
