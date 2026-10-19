<script setup lang="ts">
import { CircleAlert, Droplets, Euro, Gauge } from 'lucide-vue-next'
import type { Page } from '~/graphql/fleet'
import { FUEL_ANOMALIES, IQ, type FuelSummary, type FuelTransaction } from '~/graphql/integration'

const { t, locale } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('fuel.title')} · FleetPilot` })

const state = useUrlState({ q: '', anomaly: '', only: '', page: 0 })
const variables = computed(() => ({
  filter: compact({ q: state.q, anomaly: state.anomaly, onlyAnomalies: state.only === '1' || !!state.anomaly || undefined }),
  page: state.page, size: 25,
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ fuelTransactions: Page<FuelTransaction>; fuelSummary: FuelSummary }>(
  IQ.fuel, variables, { cacheKey: () => `fuel:${JSON.stringify(variables.value)}` })
const s = computed(() => data.value?.fuelSummary)
const byType = computed(() => {
  const counts = Object.fromEntries((s.value?.anomaliesByType ?? []).map(c => [c.key, c.count]))
  return FUEL_ANOMALIES.map(code => ({ code, count: (counts[code] as number | undefined) ?? 0 }))
})
const months = computed(() => (s.value?.months ?? []).map(m => ({
  x: new Intl.DateTimeFormat(locale.value === 'fr' ? 'fr-FR' : 'en-GB', { month: 'short', year: '2-digit' })
    .format(new Date(`${m.month}-15T12:00:00`)),
  y: Math.round(m.amount),
})))

function filterAnomaly(code: string) {
  state.anomaly = state.anomaly === code ? '' : code
  state.page = 0
}

const columns = computed(() => [
  { key: 'occurredAt', label: t('fuel.date') },
  { key: 'registration', label: t('fuel.vehicle') },
  { key: 'station', label: t('fuel.station') },
  { key: 'product', label: t('fuel.product') },
  { key: 'litres', label: t('fuel.litres'), align: 'right' as const },
  { key: 'amount', label: t('fuel.amount'), align: 'right' as const },
  { key: 'odometer', label: t('fuel.odometer'), align: 'right' as const },
  { key: 'anomalies', label: t('fuel.anomalies') },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('fuel.title')"
      :subtitle="s ? $t('fuel.subtitle', { from: f.date(s.from), to: f.date(s.to) }) : $t('fuel.subtitleShort')" />
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('fuel.spending')" :value="s ? f.money(s.amount) : '-'" :icon="Euro"
        :hint="s ? $t('fuel.transactions', { n: f.number(s.transactions) }) : undefined" />
      <UiKpi :label="$t('fuel.volume')" :value="s ? `${f.number(s.litres)} L` : '-'" :icon="Droplets" />
      <UiKpi :label="$t('fuel.averagePrice')" :value="s ? `${f.number(s.averagePrice, 3)} €/L` : '-'" :icon="Gauge"
        :hint="$t('fuel.averageHint')" />
      <UiKpi :label="$t('fuel.withAnomaly')" :value="s ? f.number(s.withAnomaly) : '-'" :icon="CircleAlert"
        :tone="s?.withAnomaly ? 'danger' : 'default'"
        :hint="s ? $t('fuel.amountToCheck', { amount: f.money(s.amountWithAnomaly) }) : undefined" />
    </div>

    <div class="top">
      <section class="card">
        <header class="card-header"><h2>{{ $t('fuel.monthly') }}</h2></header>
        <div class="card-body"><ChartBars :bars="months" :format="v => f.money(v)" height="200px" /></div>
      </section>
      <section class="card">
        <header class="card-header"><h2>{{ $t('fuel.anomaliesTitle') }}</h2></header>
        <ul class="anomalies" data-cy="fuel-anomalies">
          <li v-for="a in byType" :key="a.code">
            <button class="anomaly" :class="{ active: state.anomaly === a.code }" :disabled="!a.count" @click="filterAnomaly(a.code)">
              <span class="small">{{ $t(`enum.FuelAnomaly.${a.code}`) }}</span>
              <span class="xs muted grow">{{ $t(`fuel.anomalyHint.${a.code}`) }}</span>
              <span class="num count" :class="{ zero: !a.count }">{{ a.count }}</span>
            </button>
          </li>
        </ul>
      </section>
    </div>

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('fuel.search')" class="grow" />
        <select v-model="state.anomaly" class="select" :aria-label="$t('fuel.anomalies')" @change="state.page = 0">
          <option value="">{{ $t('fuel.anomalies') }} : {{ $t('app.all') }}</option>
          <option v-for="code in FUEL_ANOMALIES" :key="code" :value="code">{{ $t(`enum.FuelAnomaly.${code}`) }}</option>
        </select>
        <label class="check small"><input v-model="state.only" type="checkbox" true-value="1" false-value="" data-cy="only-anomalies"> {{ $t('fuel.onlyAnomalies') }}</label>
      </div>
      <UiDataTable :columns="columns" :rows="data?.fuelTransactions.content ?? []" :loading="pending && !data"
        :empty-text="$t('fuel.empty')">
        <template #cell-occurredAt="{ row }"><span class="small nowrap">{{ f.dateTime(row.occurredAt) }}</span></template>
        <template #cell-registration="{ row }">
          <NuxtLink v-if="row.vehicleId" :to="`/vehicules/${row.vehicleId}`" class="plate small">{{ row.registration }}</NuxtLink>
          <span v-else class="plate small unknown">{{ row.registration }}</span>
          <div class="xs muted">{{ row.provider }}</div>
        </template>
        <template #cell-station="{ row }">
          <div class="small">{{ row.station }}</div>
          <div class="xs muted">{{ row.city }}</div>
        </template>
        <template #cell-product="{ row }"><span class="small">{{ $t(`fuel.products.${row.product}`) }}</span></template>
        <template #cell-litres="{ row }"><span class="num small">{{ f.number(row.litres, 2) }} L</span></template>
        <template #cell-amount="{ row }"><span class="num small">{{ f.money(row.amount, 2) }}</span></template>
        <template #cell-odometer="{ row }"><span class="num small">{{ row.odometer ? f.km(row.odometer) : '-' }}</span></template>
        <template #cell-anomalies="{ row }">
          <span v-for="code in row.anomalies" :key="code" class="badge danger plain tag">{{ $t(`enum.FuelAnomaly.${code}`) }}</span>
          <span v-if="!row.anomalies.length" class="xs muted">-</span>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.fuelTransactions.totalPages"
        :total-elements="data.fuelTransactions.totalElements" />
    </section>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.top {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.anomalies {
  margin: 0;
  padding: 6px;
  list-style: none;
}

.anomaly {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.anomaly:hover:not(:disabled) { background: var(--surface-hover); }
.anomaly:disabled { cursor: default; }

.anomaly.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.grow { flex: 1; }

.count {
  min-width: 28px;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: var(--text-xs);
  font-weight: 600;
  text-align: center;
}

.count.zero {
  background: var(--neutral-soft);
  color: var(--text-muted);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.filters .select {
  width: auto;
  max-width: 240px;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
}

.plate {
  font-weight: 500;
}

.plate.unknown {
  color: var(--danger);
}

.tag {
  margin: 0 4px 2px 0;
}

@media (max-width: 1100px) {
  .top { grid-template-columns: minmax(0, 1fr); }
}
</style>
