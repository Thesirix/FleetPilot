import { DQ, type DriverNotification } from '~/graphql/driver'
import { graphql } from '~/utils/graphql'

const SEEN_KEY = 'fleetpilot:driver:seen-notifications'
const ANNOUNCED_KEY = 'fleetpilot:driver:announced-notifications'
const POLL_MS = 120_000

const list = ref<DriverNotification[]>([])
const seen = ref<string[]>([])
const permission = ref<NotificationPermission | 'unsupported'>('default')
let started = false

function readIds(key: string): string[] {
  try {
    return JSON.parse(localStorage.getItem(key) ?? '[]') as string[]
  } catch {
    return []
  }
}

function writeIds(key: string, ids: string[]) {
  try {
    localStorage.setItem(key, JSON.stringify(ids.slice(-200)))
  } catch {
    // Private mode: the badge simply counts again after a reload
  }
}

/**
 * What the driver should know (deadlines, alerts of the box of their vehicle, progress of their claims), refreshed
 * every two minutes while the app is open. A notification id changes when the situation gets worse, so an overdue
 * deadline is announced again even if it was seen when it was only due soon. New ones are shown as system
 * notifications once the driver has allowed them.
 */
export function useDriverNotifications() {
  const { t } = useI18n()
  const f = useFormat()

  const unread = computed(() => list.value.filter(n => !seen.value.includes(n.id)).length)

  function title(n: DriverNotification): string {
    switch (n.kind) {
      case 'DEADLINE': return f.label('DeadlineType', n.code)
      case 'VEHICLE_ALERT': return f.label('AlertType', n.code)
      default: return t('driverApp.notifications.incident', { reference: n.detail ?? '' })
    }
  }

  function text(n: DriverNotification): string {
    switch (n.kind) {
      case 'DEADLINE': {
        const due = n.dueDate ? f.date(n.dueDate) : f.km(n.dueMileage ?? 0)
        return n.detail === 'OVERDUE' ? t('driverApp.notifications.overdue', { due }) : t('driverApp.notifications.dueSoon', { due })
      }
      case 'VEHICLE_ALERT': return n.detail ?? ''
      default: return t('driverApp.notifications.incidentStatus', { status: f.label('IncidentStatus', n.code) })
    }
  }

  async function refresh() {
    try {
      const { data } = await graphql<{ myNotifications: DriverNotification[] }>(DQ.myNotifications, {},
        { cacheKey: 'my-notifications' })
      list.value = data.myNotifications
      // Each notification pops up on the phone once, even if the driver has not opened the list since
      const announced = readIds(ANNOUNCED_KEY)
      const fresh = list.value.filter(n => !seen.value.includes(n.id) && !announced.includes(n.id))
      if (fresh.length && await announce(fresh)) {
        writeIds(ANNOUNCED_KEY, [...announced, ...fresh.map(n => n.id)])
      }
    } catch {
      // Offline: the last list stays
    }
  }

  /** True when the phone showed them; the most serious three only, the list says the rest. */
  async function announce(fresh: DriverNotification[]): Promise<boolean> {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted' || !('serviceWorker' in navigator)) {
      return false
    }
    const registration = await navigator.serviceWorker.getRegistration()
    if (!registration) {
      return false
    }
    for (const n of fresh.slice(0, 3)) {
      await registration.showNotification(title(n), {
        body: text(n),
        tag: n.id,
        icon: '/pwa-192x192.png',
        badge: '/pwa-64x64.png',
        data: { url: '/conducteur/notifications' },
      })
    }
    return true
  }

  function markAllSeen() {
    seen.value = Array.from(new Set([...seen.value, ...list.value.map(n => n.id)]))
    writeIds(SEEN_KEY, seen.value)
  }

  async function enableSystemNotifications() {
    if (typeof Notification === 'undefined') {
      permission.value = 'unsupported'
      return
    }
    permission.value = await Notification.requestPermission()
    if (permission.value === 'granted') {
      await refresh()
    }
  }

  if (import.meta.client && !started) {
    started = true
    seen.value = readIds(SEEN_KEY)
    permission.value = typeof Notification === 'undefined' ? 'unsupported' : Notification.permission
    refresh()
    setInterval(() => {
      if (document.visibilityState === 'visible') {
        refresh()
      }
    }, POLL_MS)
  }

  return { list, unread, refresh, markAllSeen, title, text, permission, enableSystemNotifications }
}
