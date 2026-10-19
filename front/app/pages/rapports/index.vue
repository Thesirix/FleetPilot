<script setup lang="ts">
import { CalendarClock, Download, FileSpreadsheet, FileText, Gauge, LoaderCircle, PiggyBank, ReceiptText, Route, ShieldAlert,
  Warehouse, Wrench } from 'lucide-vue-next'
import type { Component } from 'vue'
import { Q, type Company } from '~/graphql/fleet'
import { OM, OQ, downloadReport, type Indicators, type Report, type ReportTypeInfo } from '~/graphql/operations'
import { accessToken } from '~/utils/auth'

const { t } = useI18n()
const f = useFormat()
const toast = useToast()
const session = useSessionStore()
useHead({ title: () => `${t('reports.title')} · FleetPilot` })

const companyId = ref('')
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data, error, stale, refresh } = useGraphqlQuery<{ indicators: Indicators; reportTypes: ReportTypeInfo[] }>(
  OQ.reportsPage, () => compact({ companyId: companyId.value }), { cacheKey: () => `reports-page:${companyId.value}` })
const { data: list, pending: listPending, refresh: refreshList } = useGraphqlQuery<{
  reports: { totalElements: number; content: Report[] } }>(OQ.reports, {})
const i = computed(() => data.value?.indicators ?? null)

const ICONS: Record<string, Component> = { FLEET_INVENTORY: Warehouse, TCO: Gauge, QUOTES: ReceiptText,
  DEADLINES: CalendarClock, SAVINGS: PiggyBank }
const selected = ref('TCO')
const today = new Date().toISOString().slice(0, 10)
const period = reactive({ from: `${today.slice(0, 4)}-01-01`, to: today })
const selectedType = computed(() => data.value?.reportTypes.find(r => r.type === selected.value))

const { run, running } = useMutation()
async function generate() {
  const variables = compact({ type: selected.value, companyId: companyId.value,
    from: selectedType.value?.periodic ? period.from : '', to: selectedType.value?.periodic ? period.to : '' })
  const done = await run(OM.requestReport, variables, t('reports.requested'))
  if (done) refreshList()
}

// While a report is being built, the list refreshes itself
const building = computed(() => (list.value?.reports.content ?? []).some(r => r.status === 'PENDING' || r.status === 'RUNNING'))
let timer: ReturnType<typeof setInterval> | undefined
watch(building, (value) => {
  clearInterval(timer)
  if (value) timer = setInterval(refreshList, 1500)
}, { immediate: true })
onBeforeUnmount(() => clearInterval(timer))

async function download(report: Report) {
  try {
    await downloadReport(report, await accessToken())
  } catch {
    toast.error(t('reports.downloadFailed'))
  }
}

const costShares = computed(() => {
  if (!i.value || i.value.totalCost === 0) return []
  const total = i.value.totalCost
  return [
    { key: 'rent', label: t('reports.rent'), value: i.value.rent, color: 'var(--chart-1)' },
    { key: 'maintenance', label: t('reports.maintenance'), value: i.value.maintenance, color: 'var(--chart-2)' },
    { key: 'claims', label: t('reports.claims'), value: i.value.claims, color: 'var(--chart-4)' },
  ].map(s => ({ ...s, share: (s.value / total) * 100 }))
})

function size(bytes: number | null) {
  if (!bytes) return '-'
  return bytes < 1024 * 1024 ? `${f.number(bytes / 1024)} Ko` : `${f.number(bytes / 1024 / 1024, 1)} Mo`
}
const TONES: Record<string, string> = { PENDING: 'info', RUNNING: 'info', READY: 'success', FAILED: 'danger' }
</script>

<template>
  <div>
    <UiPageHeader :title="$t('reports.title')" :subtitle="$t('reports.subtitle')">
      <template #actions>
        <select v-if="session.isStaff" v-model="companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('reports.monthlyCost')" :value="i ? f.money(i.monthlyCostPerVehicle) : '-'" :icon="Gauge"
        :hint="$t('reports.monthlyCostHint')" />
      <UiKpi :label="$t('reports.costPerKm')" :value="i?.costPerKm ? f.money(i.costPerKm, 3) : '-'" :icon="Route"
        :hint="$t('reports.costPerKmHint')" />
      <UiKpi :label="$t('reports.claimsRate')" :value="i ? f.number(i.claimsPer100Vehicles, 1) : '-'" :icon="ShieldAlert"
        :hint="$t('reports.claimsRateHint')" />
      <UiKpi :label="$t('reports.immobilisation')" :value="i ? `${f.number(i.immobilisationRate, 1)} %` : '-'" :icon="Wrench"
        :tone="i && i.immobilisationRate > 5 ? 'warning' : 'default'" :hint="$t('reports.immobilisationHint')" />
    </div>

    <section v-if="i" class="card costs">
      <div class="row-between">
        <div>
          <span class="muted small">{{ $t('reports.totalCost', { vehicles: f.number(i.vehicles) }) }}</span>
          <strong class="num total">{{ f.money(i.totalCost) }}</strong>
        </div>
        <span class="xs muted">{{ $t('reports.asOf', { date: f.date(i.asOf) }) }}</span>
      </div>
      <div class="stack-bar" role="img" :aria-label="$t('reports.breakdown')">
        <span v-for="s in costShares" :key="s.key" :style="{ width: `${s.share}%`, background: s.color }" />
      </div>
      <ul class="legend">
        <li v-for="s in costShares" :key="s.key">
          <span class="dot" :style="{ background: s.color }" />{{ s.label }}
          <strong class="num">{{ f.money(s.value) }}</strong><span class="muted num">{{ f.number(s.share, 0) }} %</span>
        </li>
      </ul>
    </section>

    <div class="layout">
      <section class="card">
        <header class="card-header"><h2>{{ $t('reports.new') }}</h2></header>
        <div class="card-body stack">
          <div class="types" role="radiogroup" :aria-label="$t('reports.type')">
            <button v-for="r in data?.reportTypes ?? []" :key="r.type" type="button" class="type" role="radio"
              :aria-checked="selected === r.type" :class="{ selected: selected === r.type }" @click="selected = r.type">
              <component :is="ICONS[r.type] ?? FileText" :size="18" />
              <span class="grow">
                <strong>{{ $t(`reports.types.${r.type}.title`) }}</strong>
                <span class="xs muted">{{ $t(`reports.types.${r.type}.text`) }}</span>
              </span>
              <span class="badge plain">{{ r.format }}</span>
            </button>
          </div>
          <div v-if="selectedType?.periodic" class="grid grid-2">
            <div class="field"><label for="from">{{ $t('reports.from') }}</label>
              <input id="from" v-model="period.from" type="date" class="input" required></div>
            <div class="field"><label for="to">{{ $t('reports.to') }}</label>
              <input id="to" v-model="period.to" type="date" class="input" required></div>
          </div>
          <p class="xs muted">{{ $t('reports.async') }}</p>
          <button class="btn btn-primary" :disabled="running || !selectedType" @click="generate">
            <FileSpreadsheet /> {{ $t('reports.generate') }}
          </button>
        </div>
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('reports.mine') }}</h2></header>
        <UiDataTable :rows="list?.reports.content ?? []" :loading="listPending && !list" :empty-text="$t('reports.empty')"
          :columns="[
            { key: 'title', label: $t('reports.report') },
            { key: 'requestedAt', label: $t('reports.requestedAt') },
            { key: 'status', label: $t('reports.status') },
            { key: 'rows', label: $t('reports.rows'), align: 'right' },
            { key: 'file', label: '' },
          ]">
          <template #cell-title="{ row }">
            <div class="small strong">{{ row.title }}</div>
            <div v-if="row.periodFrom" class="xs muted">{{ f.date(row.periodFrom) }} → {{ f.date(row.periodTo) }}</div>
          </template>
          <template #cell-requestedAt="{ row }"><span class="nowrap small">{{ f.dateTime(row.requestedAt) }}</span></template>
          <template #cell-status="{ row }">
            <span class="badge" :class="TONES[row.status]" :title="row.error ?? ''">
              <LoaderCircle v-if="row.status === 'PENDING' || row.status === 'RUNNING'" :size="12" class="spin" />
              {{ $t(`reports.statuses.${row.status}`) }}
            </span>
          </template>
          <template #cell-rows="{ row }"><span class="num small">{{ row.rowCount ?? '-' }}</span></template>
          <template #cell-file="{ row }">
            <button v-if="row.status === 'READY'" class="btn btn-sm" :aria-label="$t('reports.download')" @click="download(row)">
              <Download /> {{ size(row.sizeBytes) }}
            </button>
          </template>
        </UiDataTable>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.costs {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px 20px;
}

.costs .total {
  display: block;
  font-size: var(--text-xl);
  font-weight: 650;
}

.stack-bar {
  display: flex;
  height: 10px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--surface-hover);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.legend li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(0, 1.6fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout,
  .kpis {
    grid-template-columns: minmax(0, 1fr);
  }
}

.types {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.type {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--text-secondary);
  text-align: left;
  cursor: pointer;
}

.type:hover {
  border-color: var(--border-strong);
}

.type.selected {
  border-color: var(--primary);
  background: var(--primary-soft);
  color: var(--primary-text);
}

.type strong {
  display: block;
  color: var(--text);
  font-size: var(--text-sm);
}

.strong {
  font-weight: 560;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
