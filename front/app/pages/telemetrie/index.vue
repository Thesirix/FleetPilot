<script setup lang="ts">
import { BatteryLow, Bell, CircleParking, Gauge, Navigation, Route, Timer, WifiOff } from 'lucide-vue-next'
import { ACTIVITIES, TQ, fetchTrack, type Activity, type LiveState, type TelemetrySummary, type TrackPoint } from '~/graphql/telemetry'

const { t, locale } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('telemetry.title')} · FleetPilot` })

const { states, connected, lastUpdate } = useLiveStream()

// Kilometres of the day and open alerts: from the gateway, refreshed every minute (the stream carries positions)
const { data, refresh } = useGraphqlQuery<{ telemetrySummary: TelemetrySummary }>(TQ.summary, {})
const summaryTimer = setInterval(refresh, 60_000)
onBeforeUnmount(() => clearInterval(summaryTimer))
const summary = computed(() => data.value?.telemetrySummary)

const counts = computed(() => {
  const result: Record<Activity, number> = { MOVING: 0, IDLING: 0, PARKED: 0, OFFLINE: 0 }
  for (const s of states.value) result[s.activity]++
  return result
})

const state = useUrlState({ q: '', activity: '', vehicle: '' })
const ORDER: Record<Activity, number> = { MOVING: 0, IDLING: 1, PARKED: 2, OFFLINE: 3 }
const visible = computed(() => {
  const q = state.q.trim().toUpperCase().replace(/\s/g, '')
  return states.value
    .filter(s => (!state.activity || s.activity === state.activity) && (!q || s.registration.replace(/-/g, '').includes(q.replace(/-/g, ''))))
    .sort((a, b) => ORDER[a.activity] - ORDER[b.activity] || b.openAlerts - a.openAlerts || a.registration.localeCompare(b.registration))
})

const selected = computed<LiveState | null>(() => states.value.find(s => s.vehicleId === state.vehicle) ?? null)
const track = shallowRef<TrackPoint[]>([])
const map = ref<{ focus: (id: string) => void } | null>(null)

async function loadTrack() {
  track.value = state.vehicle ? await fetchTrack(state.vehicle, 60) : []
}
watch(() => state.vehicle, loadTrack, { immediate: true })
const trackTimer = setInterval(() => { if (state.vehicle) loadTrack() }, 30_000)
onBeforeUnmount(() => clearInterval(trackTimer))

function select(vehicleId: string) {
  state.vehicle = vehicleId
  map.value?.focus(vehicleId)
}

// "updated 2 s ago", ticking every second
const now = ref(Date.now())
const clock = setInterval(() => { now.value = Date.now() }, 1000)
onBeforeUnmount(() => clearInterval(clock))
function since(iso: string) {
  const relative = new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' })
  const seconds = Math.round((now.value - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return t('telemetry.secondsAgo', { n: Math.max(0, seconds) })
  if (seconds < 3600) return relative.format(-Math.round(seconds / 60), 'minute')
  if (seconds < 86400) return relative.format(-Math.round(seconds / 3600), 'hour')
  return f.dateTime(iso)
}
const updated = computed(() => (lastUpdate.value ? since(lastUpdate.value.toISOString()) : null))

const DOTS: Record<Activity, string> = { MOVING: 'moving', IDLING: 'idling', PARKED: 'parked', OFFLINE: 'offline' }
</script>

<template>
  <div>
    <UiPageHeader :title="$t('telemetry.title')" :subtitle="$t('telemetry.subtitle')">
      <template #badges>
        <span class="live" :class="{ on: connected }" data-cy="live-indicator">
          <span class="pulse" />{{ connected ? $t('telemetry.live') : $t('telemetry.reconnecting') }}
        </span>
      </template>
      <template #meta>
        <p class="small muted">
          {{ $t('telemetry.connectedCount', { n: f.number(states.length) }) }}<template v-if="updated"> · {{ $t('telemetry.updated', { when: updated }) }}</template>
        </p>
      </template>
    </UiPageHeader>

    <div class="grid kpis">
      <UiKpi :label="$t('enum.Activity.MOVING')" :value="f.number(counts.MOVING)" tone="success" :icon="Navigation" />
      <UiKpi :label="$t('enum.Activity.IDLING')" :value="f.number(counts.IDLING)" :tone="counts.IDLING ? 'warning' : 'default'" :icon="Timer" />
      <UiKpi :label="$t('enum.Activity.PARKED')" :value="f.number(counts.PARKED)" :icon="CircleParking" />
      <UiKpi :label="$t('enum.Activity.OFFLINE')" :value="f.number(counts.OFFLINE)" :icon="WifiOff" :hint="$t('telemetry.offlineHint')" />
      <UiKpi :label="$t('telemetry.kmToday')" :value="summary ? f.km(Math.round(summary.kilometresToday)) : '-'" :icon="Route" />
      <UiKpi :label="$t('telemetry.openAlerts')" :value="summary ? f.number(summary.openAlerts) : '-'"
        :tone="summary?.openAlerts ? 'danger' : 'default'" :icon="Bell" to="/alertes" :hint="$t('telemetry.seeAlerts')" />
    </div>

    <div class="layout">
      <section class="card map-card">
        <ClientOnly>
          <FleetMap ref="map" :states="visible" :selected-id="state.vehicle || null" :track="track" height="640px"
            @select="select" />
        </ClientOnly>
        <div class="legend small">
          <span v-for="a in ACTIVITIES" :key="a" class="legend-item"><span class="dot" :class="DOTS[a]" />{{ $t(`enum.Activity.${a}`) }}</span>
          <span class="legend-item"><span class="dot alert" />{{ $t('telemetry.withAlert') }}</span>
        </div>
      </section>

      <aside class="card side">
        <div v-if="selected" class="detail" data-cy="vehicle-detail">
          <div class="row-between">
            <div>
              <p class="plate">{{ selected.registration }}</p>
              <p class="xs muted">{{ $t('telemetry.lastMessage', { when: since(selected.lastPositionAt) }) }}</p>
            </div>
            <button class="btn btn-sm btn-ghost" @click="state.vehicle = ''">{{ $t('app.close') }}</button>
          </div>
          <span class="badge" :class="DOTS[selected.activity]">{{ $t(`enum.Activity.${selected.activity}`) }}</span>
          <dl class="facts">
            <dt><Gauge :size="14" /> {{ $t('telemetry.speed') }}</dt><dd class="num">{{ selected.speedKmh }} km/h</dd>
            <dt><Route :size="14" /> {{ $t('telemetry.odometer') }}</dt><dd class="num">{{ selected.odometerKm ? f.km(selected.odometerKm) : '-' }}</dd>
            <template v-if="selected.batteryPercent !== null">
              <dt><BatteryLow :size="14" /> {{ $t('telemetry.battery') }}</dt>
              <dd class="num" :class="{ low: selected.batteryPercent < 15 }">{{ selected.batteryPercent }} %</dd>
            </template>
            <dt><Bell :size="14" /> {{ $t('telemetry.openAlerts') }}</dt><dd class="num">{{ selected.openAlerts }}</dd>
          </dl>
          <p class="xs muted">{{ track.length > 1 ? $t('telemetry.trackShown') : $t('telemetry.noTrack') }}</p>
          <div class="actions">
            <NuxtLink :to="`/vehicules/${selected.vehicleId}?tab=telemetry`" class="btn btn-sm">{{ $t('telemetry.openFile') }}</NuxtLink>
            <NuxtLink v-if="selected.openAlerts" :to="`/alertes?vehicleId=${selected.vehicleId}`" class="btn btn-sm">{{ $t('telemetry.seeAlerts') }}</NuxtLink>
          </div>
        </div>

        <div class="filters">
          <UiSearch v-model="state.q" :placeholder="$t('telemetry.search')" />
          <div class="chips" role="group" :aria-label="$t('telemetry.activity')">
            <button class="chip" :class="{ active: !state.activity }" @click="state.activity = ''">{{ $t('app.all') }}</button>
            <button v-for="a in ACTIVITIES" :key="a" class="chip" :class="{ active: state.activity === a }"
              :data-cy="`chip-${a}`" @click="state.activity = state.activity === a ? '' : a">
              <span class="dot" :class="DOTS[a]" />{{ counts[a] }}
            </button>
          </div>
        </div>
        <ul class="list" data-cy="vehicle-list">
          <li v-for="s in visible" :key="s.vehicleId">
            <button class="item" :class="{ active: s.vehicleId === state.vehicle }" @click="select(s.vehicleId)">
              <span class="dot" :class="[DOTS[s.activity], { alert: s.openAlerts }]" />
              <span class="reg">{{ s.registration }}</span>
              <span class="xs muted grow">{{ s.activity === 'MOVING' ? `${s.speedKmh} km/h` : $t(`enum.Activity.${s.activity}`) }}</span>
              <span v-if="s.openAlerts" class="badge danger plain xs">{{ s.openAlerts }}</span>
            </button>
          </li>
          <li v-if="!visible.length" class="empty small muted">{{ states.length ? $t('telemetry.noMatch') : $t('app.loading') }}</li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-bottom: 16px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.map-card {
  padding: 6px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 10px 8px 4px;
  color: var(--text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 50%;
  border: 1.5px solid #fff;
  box-shadow: 0 0 0 1px var(--border);
}

.dot.moving { background: #12b76a; }
.dot.idling { background: #f79009; }
.dot.parked { background: #475467; }
.dot.offline { background: #98a2b3; }
.dot.alert { box-shadow: 0 0 0 2px #d92d20; }
.legend .dot.alert { background: #fff; }

.badge.moving { background: var(--success-soft); color: var(--success); }
.badge.idling { background: var(--warning-soft); color: var(--warning); }
.badge.parked { background: var(--neutral-soft); color: var(--text-secondary); }
.badge.offline { background: var(--neutral-soft); color: var(--text-muted); }

.side {
  display: flex;
  flex-direction: column;
  height: 692px;
  overflow: hidden;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-muted);
}

.plate {
  font-size: var(--text-lg);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.detail .badge {
  align-self: flex-start;
}

.facts {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 6px 12px;
  font-size: var(--text-sm);
}

.facts dt {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}

.facts dd {
  text-align: right;
  font-weight: 500;
}

.facts dd.low {
  color: var(--danger);
}

.actions {
  display: flex;
  gap: 8px;
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 9px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface);
  color: var(--text-secondary);
  font-size: var(--text-xs);
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}

.chip.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary-text);
}

.list {
  flex: 1;
  overflow-y: auto;
  margin: 0;
  padding: 4px 6px;
  list-style: none;
}

.item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 34px;
  padding: 0 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.item:hover { background: var(--surface-hover); }
.item.active { background: var(--primary-soft); }

.reg {
  font-size: var(--text-sm);
  font-weight: 500;
}

.grow { flex: 1; }

.empty {
  padding: 16px 8px;
}

.live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--neutral-soft);
  color: var(--text-muted);
  font-size: var(--text-xs);
  font-weight: 500;
}

.live.on {
  background: var(--success-soft);
  color: var(--success);
}

.pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.live.on .pulse {
  animation: pulse 1.6s ease-out infinite;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba(6, 118, 71, 0.45); }
  100% { box-shadow: 0 0 0 7px rgba(6, 118, 71, 0); }
}

@media (max-width: 1280px) {
  .kpis { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 1100px) {
  .layout { grid-template-columns: minmax(0, 1fr); }
  .side { height: auto; max-height: 520px; }
}
</style>
