import { accessToken } from '~/utils/auth'
import { GraphqlError, graphql } from '~/utils/graphql'
import {
  RejectedError, TransientError, flushOutbox, indexedDbStore, type OutboxItem, type OutboxPhoto, type OutboxTransport,
} from '~/utils/outbox'

/** Codes that mean "not now" rather than "not like this". */
const TRANSIENT_CODES = new Set(['NETWORK_ERROR', 'SERVICE_UNAVAILABLE', 'UNAUTHORIZED', 'INTERNAL_ERROR'])

const transport: OutboxTransport = {
  async sendRecord(mutation, variables) {
    // Never send the browser to the login page from a background flush: wait for the next one instead.
    if (!(await accessToken())) {
      throw new TransientError('Not signed in')
    }
    try {
      await graphql(mutation, variables)
    } catch (e) {
      if (e instanceof GraphqlError && !TRANSIENT_CODES.has(e.code) && (e.status === undefined || e.status < 500)) {
        throw new RejectedError(e.code, e.message)
      }
      throw new TransientError(String(e))
    }
  },

  async sendPhoto(path: string, photo: OutboxPhoto) {
    const token = await accessToken()
    if (!token) {
      throw new TransientError('Not signed in')
    }
    let response: Response
    try {
      response = await fetch(path, {
        method: 'PUT',
        headers: { 'Content-Type': photo.contentType, Authorization: `Bearer ${token}` },
        body: photo.bytes,
      })
    } catch (e) {
      throw new TransientError(String(e))
    }
    if (response.ok) {
      return
    }
    if (response.status >= 500 || [401, 408, 429].includes(response.status)) {
      throw new TransientError(`HTTP ${response.status}`)
    }
    const problem = await response.json().catch(() => ({})) as { code?: string; detail?: string }
    throw new RejectedError(problem.code ?? `HTTP_${response.status}`, problem.detail ?? `HTTP ${response.status}`)
  },
}

export type SubmitResult = { status: 'sent' } | { status: 'queued' } | { status: 'rejected'; code: string; message: string }

/**
 * IndexedDB stores structured clones, which Vue's reactive proxies (the photos of a form) cannot be: the item is
 * copied into plain objects first, the photos keeping their bytes.
 */
function plain(item: OutboxItem): OutboxItem {
  const raw = toRaw(item)
  return {
    ...raw,
    variables: JSON.parse(JSON.stringify(raw.variables)) as Record<string, unknown>,
    photos: raw.photos.map(photo => ({ id: photo.id, bytes: toRaw(photo).bytes, contentType: photo.contentType })),
    photosSent: [...raw.photosSent],
    error: raw.error ? { ...raw.error } : null,
  }
}

const items = ref<OutboxItem[]>([])
const flushing = ref(false)
let started = false

/**
 * The outbox of the driver app, shared by every page: the items waiting, a way to add one, and the automatic flush
 * when the app starts, when the network comes back and every minute while items are waiting.
 */
export function useOutbox() {
  const { t, te } = useI18n()
  const toast = useToast()

  async function reload() {
    try {
      items.value = (await indexedDbStore.all()).sort((a, b) => a.createdAt.localeCompare(b.createdAt))
    } catch {
      items.value = []
    }
  }

  /** Quiet when the driver just submitted: the page says itself whether it left or waits for the network. */
  async function flush(options: { quiet?: boolean } = {}): Promise<void> {
    if (flushing.value) {
      return
    }
    flushing.value = true
    try {
      const report = await flushOutbox(indexedDbStore, transport)
      if (report.sent > 0 && !options.quiet) {
        toast.success(t('driverApp.outbox.sent', report.sent))
      }
      if (report.rejected > 0 && !options.quiet) {
        toast.error(t('driverApp.outbox.rejected', report.rejected))
      }
    } finally {
      flushing.value = false
      await reload()
    }
  }

  /**
   * Writes the item to the phone, then tries to send it at once. Refused at once (an implausible price, a checklist
   * point without comment), it is taken back out of the outbox: the driver corrects the form and sends it again.
   */
  async function submit(item: OutboxItem): Promise<SubmitResult> {
    await indexedDbStore.put(plain(item))
    await reload()
    await flush({ quiet: true })
    const left = items.value.find(waiting => waiting.id === item.id)
    if (!left) {
      return { status: 'sent' }
    }
    if (left.error && !left.recordSent) {
      await discard(left.id)
      return { status: 'rejected', code: left.error.code, message: left.error.message }
    }
    return { status: 'queued' }
  }

  /**
   * Submits and tells the driver what happened: sent, kept on the phone until the network is back, or refused with
   * the reason. True unless refused, so the form can be closed.
   */
  async function send(item: OutboxItem, sentMessage: string): Promise<boolean> {
    let result: SubmitResult
    try {
      result = await submit(item)
    } catch (e) {
      // The phone refused to store it (storage full, private browsing): nothing was sent, the form stays filled
      toast.error(t('driverApp.outbox.storageFailed'))
      console.error(e)
      return false
    }
    if (result.status === 'sent') {
      toast.success(sentMessage)
      return true
    }
    if (result.status === 'queued') {
      toast.info(t('driverApp.outbox.queued'))
      return true
    }
    toast.error(te(`errors.${result.code}`) ? t(`errors.${result.code}`) : result.message)
    return false
  }

  async function discard(id: string) {
    await indexedDbStore.remove(id)
    await reload()
  }

  if (import.meta.client && !started) {
    started = true
    reload().then(() => flush())
    window.addEventListener('online', () => flush())
    setInterval(() => {
      if (items.value.some(item => item.error === null) && navigator.onLine) {
        flush()
      }
    }, 60_000)
  }

  const pending = computed(() => items.value.filter(item => item.error === null))
  const rejected = computed(() => items.value.filter(item => item.error !== null))

  return { items, pending, rejected, flushing, submit, send, flush, discard, reload }
}
