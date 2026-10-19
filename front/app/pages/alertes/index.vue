<script setup lang="ts">
import { BatteryLow, Check, CheckCheck, Gauge, MapPin, Siren, Timer, TriangleAlert, Wrench } from 'lucide-vue-next'
import type { Component } from 'vue'
import type { Page } from '~/graphql/fleet'
import { ALERT_TYPES, TM, TQ, type Alert, type AlertType, type TelemetrySummary } from '~/graphql/telemetry'

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('alerts.title')} · FleetPilot` })

const state = useUrlState({ status: 'OPEN', type: '', severity: '', vehicleId: '', page: 0 })
const variables = computed(() => ({
  filter: compact({ status: state.status, type: state.type, severity: state.severity, vehicleId: state.vehicleId }),
  page: state.page, size: 25,
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ alerts: Page<Alert>; telemetrySummary: TelemetrySummary }>(
  TQ.alerts, variables, { cacheKey: () => `alerts:${JSON.stringify(variables.value)}` })
const byType = computed(() => {
  const counts = Object.fromEntries((data.value?.telemetrySummary.openAlertsByType ?? []).map(c => [c.type, c.count]))
  return ALERT_TYPES.map(type => ({ type, count: (counts[type] as number | undefined) ?? 0 }))
})

const ICONS: Record<AlertType, Component> = {
  ENGINE_FAULT: Siren, ENGINE_WARNING: TriangleAlert, SPEEDING: Gauge, LOW_BATTERY: BatteryLow, LONG_IDLE: Timer,
  SERVICE_DUE: Wrench,
}
const SEVERITY_TONES: Record<string, string> = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info' }
const STATUS_TONES: Record<string, string> = { OPEN: 'danger', ACKNOWLEDGED: 'warning', RESOLVED: 'success' }

const columns = computed(() => [
  { key: 'type', label: t('alerts.alert') },
  { key: 'registration', label: t('alerts.vehicle') },
  { key: 'raisedAt', label: t('alerts.raisedAt') },
  { key: 'occurrences', label: t('alerts.occurrences'), align: 'right' as const },
  { key: 'status', label: t('alerts.status') },
])

function filterType(type: AlertType) {
  state.type = state.type === type ? '' : type
  state.page = 0
}

const selected = ref<Alert | null>(null)
const comment = ref('')
const { run, running } = useMutation()
function open(alert: Alert) {
  selected.value = alert
  comment.value = ''
}
async function acknowledge() {
  const result = await run<{ acknowledgeAlert: Alert }>(TM.acknowledge, { id: selected.value!.id }, t('alerts.acknowledged'))
  if (result) {
    selected.value = result.acknowledgeAlert
    refresh()
  }
}
async function resolve() {
  const result = await run(TM.resolve, { id: selected.value!.id, comment: comment.value || null }, t('alerts.resolved'))
  if (result) {
    selected.value = null
    refresh()
  }
}
const modalOpen = computed({ get: () => selected.value !== null, set: (o: boolean) => { if (!o) selected.value = null } })
</script>

<template>
  <div>
    <UiPageHeader :title="$t('alerts.title')" :subtitle="$t('alerts.subtitle')">
      <template #actions>
        <NuxtLink to="/telemetrie" class="btn"><MapPin /> {{ $t('alerts.liveMap') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="types" data-cy="alert-types">
      <button v-for="c in byType" :key="c.type" class="type card" :class="{ active: state.type === c.type, critical: c.type === 'ENGINE_FAULT' && c.count }"
        @click="filterType(c.type)">
        <span class="type-icon"><component :is="ICONS[c.type]" :size="16" /></span>
        <span class="type-label small">{{ $t(`enum.AlertType.${c.type}`) }}</span>
        <span class="type-count num">{{ f.number(c.count) }}</span>
      </button>
    </div>

    <section class="card">
      <div class="filters">
        <select v-model="state.status" class="select" :aria-label="$t('alerts.status')" @change="state.page = 0">
          <option value="">{{ $t('alerts.status') }} : {{ $t('app.all') }}</option>
          <option v-for="s in ['OPEN', 'ACKNOWLEDGED', 'RESOLVED']" :key="s" :value="s">{{ $t(`enum.AlertStatus.${s}`) }}</option>
        </select>
        <select v-model="state.severity" class="select" :aria-label="$t('alerts.severity')" @change="state.page = 0">
          <option value="">{{ $t('alerts.severity') }} : {{ $t('app.all') }}</option>
          <option v-for="s in ['CRITICAL', 'WARNING', 'INFO']" :key="s" :value="s">{{ $t(`enum.AlertSeverity.${s}`) }}</option>
        </select>
        <select v-model="state.type" class="select" :aria-label="$t('alerts.alert')" @change="state.page = 0">
          <option value="">{{ $t('alerts.alert') }} : {{ $t('app.all') }}</option>
          <option v-for="type in ALERT_TYPES" :key="type" :value="type">{{ $t(`enum.AlertType.${type}`) }}</option>
        </select>
        <button v-if="state.vehicleId" class="btn btn-sm" @click="state.vehicleId = ''">{{ $t('alerts.allVehicles') }}</button>
      </div>
      <UiDataTable :columns="columns" :rows="data?.alerts.content ?? []" :loading="pending" clickable
        :empty-text="$t('alerts.empty')" @row-click="open">
        <template #cell-type="{ row }">
          <div class="alert-cell">
            <span class="sev" :class="SEVERITY_TONES[row.severity]"><component :is="ICONS[row.type as AlertType]" :size="15" /></span>
            <div>
              <div class="small strong">{{ $t(`enum.AlertType.${row.type}`) }}</div>
              <div class="xs muted">{{ row.message }}</div>
            </div>
          </div>
        </template>
        <template #cell-registration="{ row }">
          <NuxtLink :to="`/vehicules/${row.vehicleId}?tab=telemetry`" class="plate small" @click.stop>{{ row.registration ?? '-' }}</NuxtLink>
        </template>
        <template #cell-raisedAt="{ row }">
          <div class="small nowrap">{{ f.dateTime(row.raisedAt) }}</div>
          <div v-if="row.lastSeenAt !== row.raisedAt" class="xs muted nowrap">{{ $t('alerts.lastSeen', { when: f.dateTime(row.lastSeenAt) }) }}</div>
        </template>
        <template #cell-occurrences="{ row }"><span class="num small">{{ row.occurrences }}</span></template>
        <template #cell-status="{ row }">
          <span class="badge" :class="STATUS_TONES[row.status]">{{ $t(`enum.AlertStatus.${row.status}`) }}</span>
          <div v-if="row.handledBy" class="xs muted">{{ row.handledBy }}</div>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.alerts.totalPages"
        :total-elements="data.alerts.totalElements" />
    </section>

    <UiModal v-model:open="modalOpen" :title="selected ? $t(`enum.AlertType.${selected.type}`) : ''" width="560px">
      <div v-if="selected" class="stack-sm" data-cy="alert-detail">
        <p class="message">{{ selected.message }}</p>
        <dl class="facts small">
          <dt>{{ $t('alerts.vehicle') }}</dt><dd>{{ selected.registration }}</dd>
          <dt>{{ $t('alerts.severity') }}</dt>
          <dd><span class="badge" :class="SEVERITY_TONES[selected.severity]">{{ $t(`enum.AlertSeverity.${selected.severity}`) }}</span></dd>
          <dt>{{ $t('alerts.raisedAt') }}</dt><dd>{{ f.dateTime(selected.raisedAt) }}</dd>
          <dt>{{ $t('alerts.occurrences') }}</dt><dd class="num">{{ selected.occurrences }}</dd>
          <template v-if="selected.latitude !== null">
            <dt>{{ $t('alerts.position') }}</dt>
            <dd>
              <a :href="`https://www.openstreetmap.org/?mlat=${selected.latitude}&mlon=${selected.longitude}#map=15/${selected.latitude}/${selected.longitude}`"
                target="_blank" rel="noopener">{{ selected.latitude.toFixed(4) }}, {{ selected.longitude?.toFixed(4) }}</a>
            </dd>
          </template>
          <dt>{{ $t('alerts.status') }}</dt>
          <dd><span class="badge" :class="STATUS_TONES[selected.status]">{{ $t(`enum.AlertStatus.${selected.status}`) }}</span></dd>
          <template v-if="selected.handledBy">
            <dt>{{ $t('alerts.handledBy') }}</dt><dd>{{ selected.handledBy }}</dd>
          </template>
          <template v-if="selected.comment">
            <dt>{{ $t('alerts.comment') }}</dt><dd>{{ selected.comment }}</dd>
          </template>
        </dl>
        <label v-if="selected.status !== 'RESOLVED'" class="field">
          <span class="label small">{{ $t('alerts.whatWasDone') }}</span>
          <textarea v-model="comment" class="input" rows="3" maxlength="500" :placeholder="$t('alerts.commentPlaceholder')"
            data-cy="alert-comment" />
        </label>
      </div>
      <template v-if="selected && selected.status !== 'RESOLVED'" #footer>
        <button v-if="selected.status === 'OPEN'" class="btn" :disabled="running" data-cy="acknowledge" @click="acknowledge">
          <Check /> {{ $t('alerts.acknowledge') }}
        </button>
        <button class="btn btn-primary" :disabled="running" data-cy="resolve" @click="resolve">
          <CheckCheck /> {{ $t('alerts.resolve') }}
        </button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.types {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.type {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  gap: 6px 10px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--border);
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.type:hover {
  border-color: var(--border-strong);
}

.type.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--ring);
}

.type-icon {
  display: inline-flex;
  padding: 6px;
  border-radius: var(--radius-sm);
  background: var(--neutral-soft);
  color: var(--text-secondary);
}

.type.critical .type-icon {
  background: var(--danger-soft);
  color: var(--danger);
}

.type-label {
  color: var(--text-secondary);
}

.type-count {
  grid-column: 1 / -1;
  font-size: var(--text-2xl);
  font-weight: 600;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.filters .select {
  width: auto;
  max-width: 240px;
}

.alert-cell {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.sev {
  display: inline-flex;
  flex: none;
  padding: 6px;
  border-radius: var(--radius-sm);
}

.sev.danger { background: var(--danger-soft); color: var(--danger); }
.sev.warning { background: var(--warning-soft); color: var(--warning); }
.sev.info { background: var(--info-soft); color: var(--info); }

.strong {
  font-weight: 500;
}

.plate {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.message {
  font-size: var(--text-md);
  font-weight: 500;
}

.facts {
  display: grid;
  grid-template-columns: 140px 1fr;
  gap: 8px 12px;
}

.facts dt {
  color: var(--text-muted);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 6px;
}

@media (max-width: 1280px) {
  .types { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
