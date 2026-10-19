<script setup lang="ts">
import { TQ, fetchTrack, type TrackPoint, type VehicleTelemetry } from '~/graphql/telemetry'

/** Telemetry tab of a vehicle file: where it is, how it was used over 30 days, its trips, trouble codes and alerts. */
const props = defineProps<{ vehicleId: string }>()
const { t } = useI18n()
const f = useFormat()

const { data, pending, error, refresh } = useGraphqlQuery<{ vehicleTelemetry: VehicleTelemetry | null }>(TQ.vehicle,
  () => ({ vehicleId: props.vehicleId }))
const telemetry = computed(() => data.value?.vehicleTelemetry ?? null)
const track = shallowRef<TrackPoint[]>([])
watch(() => props.vehicleId, async (id) => { track.value = await fetchTrack(id, 120) }, { immediate: true })

const usage = computed(() => telemetry.value?.usage ?? [])
const totalKm = computed(() => usage.value.reduce((sum, d) => sum + d.distanceKm, 0))
const longest = computed(() => Math.max(0, ...usage.value.map(d => d.longestTripKm)))
const bars = computed(() => {
  const byDay = new Map(usage.value.map(d => [d.day, d.distanceKm]))
  const days: { x: string; y: number }[] = []
  for (let i = 29; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86_400_000)
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    days.push({ x: f.date(day, 'short'), y: Math.round(byDay.get(day) ?? 0) })
  }
  return days
})
const states = computed(() => (telemetry.value?.state ? [telemetry.value.state] : []))

const SEVERITY_TONES: Record<string, string> = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info', HIGH: 'danger',
  MEDIUM: 'warning', LOW: 'info' }
const STATUS_TONES: Record<string, string> = { OPEN: 'danger', ACKNOWLEDGED: 'warning', RESOLVED: 'success' }
const duration = (minutes: number) => (minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${String(minutes % 60).padStart(2, '0')}`)
const minutesBetween = (a: string, b: string) => Math.max(1, Math.round((new Date(b).getTime() - new Date(a).getTime()) / 60000))
</script>

<template>
  <div>
    <UiQueryState :error="error" @retry="refresh" />
    <div v-if="pending && !data" class="stack">
      <span class="skeleton" style="height: 96px" />
      <span class="skeleton" style="height: 320px" />
    </div>
    <UiEmpty v-else-if="!telemetry" :title="$t('telemetry.notConnected')" :text="$t('telemetry.notConnectedText')" />

    <div v-else class="stack" data-cy="vehicle-telemetry">
      <div class="grid grid-4">
        <UiKpi :label="$t('telemetry.now')" :value="telemetry.state ? $t(`enum.Activity.${telemetry.state.activity}`) : '-'"
          :hint="telemetry.state ? `${telemetry.state.speedKmh} km/h · ${f.dateTime(telemetry.state.lastPositionAt)}` : undefined" />
        <UiKpi :label="$t('telemetry.km30')" :value="f.km(Math.round(totalKm))" :hint="$t('telemetry.perActiveDay', { km: f.number(usage.length ? totalKm / usage.length : 0) })" />
        <UiKpi :label="$t('telemetry.activeDays')" :value="`${usage.length} / 30`" />
        <UiKpi :label="$t('telemetry.longestTrip')" :value="f.km(Math.round(longest))"
          :tone="longest > 250 ? 'warning' : 'default'" :hint="longest > 250 ? $t('telemetry.longTripHint') : undefined" />
      </div>

      <div class="two">
        <section class="card">
          <header class="card-header"><h2>{{ $t('telemetry.dailyDistance') }}</h2></header>
          <div class="card-body"><ChartBars :bars="bars" :format="v => `${v} km`" height="260px" /></div>
        </section>
        <section class="card map">
          <ClientOnly>
            <FleetMap :states="states" :selected-id="vehicleId" :track="track" height="306px" />
          </ClientOnly>
        </section>
      </div>

      <div class="two">
        <section class="card">
          <header class="card-header"><h2>{{ $t('telemetry.lastTrips') }}</h2></header>
          <UiDataTable :rows="telemetry.trips" :empty-text="$t('telemetry.noTrip')" :columns="[
            { key: 'startedAt', label: $t('telemetry.departure') },
            { key: 'duration', label: $t('telemetry.duration') },
            { key: 'distanceKm', label: $t('telemetry.distance'), align: 'right' },
            { key: 'speeds', label: $t('telemetry.speeds'), align: 'right' },
          ]">
            <template #cell-startedAt="{ row }"><span class="small nowrap">{{ f.dateTime(row.startedAt) }}</span></template>
            <template #cell-duration="{ row }">
              <span class="small">{{ duration(minutesBetween(row.startedAt, row.endedAt)) }}</span>
              <span v-if="row.idleMinutes" class="xs muted"> · {{ $t('telemetry.idle', { n: row.idleMinutes }) }}</span>
            </template>
            <template #cell-distanceKm="{ row }"><span class="num small">{{ f.number(row.distanceKm, 1) }} km</span></template>
            <template #cell-speeds="{ row }"><span class="num small">{{ row.averageSpeed }} / {{ row.maxSpeed }} km/h</span></template>
          </UiDataTable>
        </section>

        <div class="stack">
          <section class="card">
            <header class="card-header"><h2>{{ $t('telemetry.alertsOfVehicle') }}</h2></header>
            <ul v-if="telemetry.alerts.length" class="rows">
              <li v-for="a in telemetry.alerts" :key="a.id" class="row-between">
                <div>
                  <div class="small">{{ $t(`enum.AlertType.${a.type}`) }} <span class="xs muted">· {{ f.dateTime(a.raisedAt) }}</span></div>
                  <div class="xs muted">{{ a.message }}</div>
                </div>
                <span class="badge" :class="STATUS_TONES[a.status]">{{ $t(`enum.AlertStatus.${a.status}`) }}</span>
              </li>
            </ul>
            <p v-else class="card-body small muted">{{ $t('telemetry.noAlert') }}</p>
          </section>
          <section class="card">
            <header class="card-header"><h2>{{ $t('telemetry.faults') }}</h2></header>
            <ul v-if="telemetry.faults.length" class="rows">
              <li v-for="d in telemetry.faults" :key="d.id" class="row-between">
                <div>
                  <div class="small"><span class="mono">{{ d.code }}</span> {{ d.description }}</div>
                  <div class="xs muted">{{ f.dateTime(d.at) }}</div>
                </div>
                <span class="badge" :class="SEVERITY_TONES[d.severity]">{{ $t(`telemetry.faultSeverity.${d.severity}`) }}</span>
              </li>
            </ul>
            <p v-else class="card-body small muted">{{ $t('telemetry.noFault') }}</p>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.two {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.map {
  padding: 6px;
}

.rows {
  margin: 0;
  padding: 0;
  list-style: none;
}

.rows li {
  padding: 10px 18px;
  border-top: 1px solid var(--border);
}

.rows li:first-child {
  border-top: 0;
}

@media (max-width: 1100px) {
  .two { grid-template-columns: minmax(0, 1fr); }
}
</style>
