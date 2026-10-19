import type { LiveState } from '~/graphql/telemetry'

/**
 * The live positions of the connected vehicles, pushed by the telemetry service every two seconds (Server-Sent
 * Events). EventSource cannot send the access token, so the stream is read with fetch; it reconnects by itself after
 * a network cut or an expired token, waiting a little longer each time.
 */
export function useLiveStream(companyId: MaybeRefOrGetter<string | null | undefined> = null) {
  const states = shallowRef<LiveState[]>([])
  const connected = ref(false)
  const lastUpdate = ref<Date | null>(null)
  let controller: AbortController | null = null
  let retry: ReturnType<typeof setTimeout> | null = null
  let attempts = 0
  let stopped = false

  async function connect() {
    controller?.abort()
    controller = new AbortController()
    const company = toValue(companyId)
    try {
      const response = await fetch(`/api/v1/telemetry/stream${company ? `?companyId=${company}` : ''}`, {
        headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'text/event-stream' },
        signal: controller.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(`stream ${response.status}`)
      }
      connected.value = true
      attempts = 0
      await read(response.body)
    } catch (e) {
      if ((e as Error).name === 'AbortError') return
    }
    connected.value = false
    if (!stopped) {
      retry = setTimeout(connect, Math.min(30_000, 1_000 * 2 ** attempts++))
    }
  }

  async function read(body: ReadableStream<Uint8Array>) {
    const reader = body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { value, done } = await reader.read()
      if (done) return
      buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n')
      let end = buffer.indexOf('\n\n')
      while (end >= 0) {
        handle(buffer.slice(0, end))
        buffer = buffer.slice(end + 2)
        end = buffer.indexOf('\n\n')
      }
    }
  }

  function handle(event: string) {
    const lines = event.split('\n')
    const name = lines.find(l => l.startsWith('event:'))?.slice(6).trim()
    const data = lines.filter(l => l.startsWith('data:')).map(l => l.slice(5)).join('\n')
    if (name === 'states' && data) {
      states.value = JSON.parse(data) as LiveState[]
      lastUpdate.value = new Date()
    }
  }

  function stop() {
    stopped = true
    if (retry) clearTimeout(retry)
    controller?.abort()
    connected.value = false
  }

  onMounted(connect)
  onBeforeUnmount(stop)
  watch(() => toValue(companyId), () => { if (!stopped) connect() })

  return { states, connected, lastUpdate }
}
