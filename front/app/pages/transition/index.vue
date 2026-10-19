<script setup lang="ts">
import { CloudOff, Gauge, Leaf, MapPinOff, PiggyBank } from 'lucide-vue-next'
import { Q, type Company, type Page } from '~/graphql/fleet'
import { EQ, ELIGIBILITIES, type EnergyAssessment, type EnergySummary, type PlanQuarter, type QuotaYear } from '~/graphql/energy'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('transition.title')} · FleetPilot` })

const state = useUrlState({ company: '', status: '', category: '', q: '', sort: 'annualSaving', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data: overview, error, stale, refresh } = useGraphqlQuery<{ energySummary: EnergySummary;
  energyPlan: { quarters: PlanQuarter[]; quotas: QuotaYear[] } }>(EQ.overview,
() => ({ companyId: state.company || null }), { cacheKey: () => `energy:${state.company}` })
const variables = computed(() => ({
  filter: compact({ companyId: state.company, status: state.status, category: state.category, q: state.q }),
  sort: state.sort, page: state.page, size: 20,
}))
const { data, pending } = useGraphqlQuery<{ energyAssessments: Page<EnergyAssessment> }>(EQ.assessments, variables,
  { cacheKey: () => `energy-list:${JSON.stringify(variables.value)}` })

const s = computed(() => overview.value?.energySummary)
const plan = computed(() => overview.value?.energyPlan)

/** The fleet split by eligibility, as one bar: what can switch now, later, or must be confirmed. */
const SEGMENTS: { key: keyof EnergySummary; status: string; color: string }[] = [
  { key: 'zeroEmission', status: 'ZERO_EMISSION', color: '#0f766e' },
  { key: 'eligible', status: 'ELIGIBLE', color: '#14b8a6' },
  { key: 'eligibleWithCharging', status: 'ELIGIBLE_WITH_CHARGING', color: '#5eead4' },
  { key: 'toConfirm', status: 'TO_CONFIRM', color: '#98a2b3' },
  { key: 'notYet', status: 'NOT_YET', color: '#f79009' },
  { key: 'notApplicable', status: 'NOT_APPLICABLE', color: '#d0d5dd' },
]
const segments = computed(() => SEGMENTS.map(seg => ({ ...seg, count: (s.value?.[seg.key] as number) ?? 0 }))
  .map(seg => ({ ...seg, width: s.value?.vehicles ? (seg.count / s.value.vehicles) * 100 : 0 })))

const quarterBars = computed(() => (plan.value?.quarters ?? []).map(q => ({ x: q.quarter, y: q.vehicles })))
/** The saving and the CO2 avoided only mean something for a vehicle that can switch. */
const SWITCHABLE = ['ELIGIBLE', 'ELIGIBLE_WITH_CHARGING', 'TO_CONFIRM']
const TONES: Record<string, string> = { ZERO_EMISSION: 'success', ELIGIBLE: 'success', ELIGIBLE_WITH_CHARGING: 'info',
  TO_CONFIRM: 'plain', NOT_YET: 'warning', NOT_APPLICABLE: 'plain' }

function filterStatus(status: string) {
  state.status = state.status === status ? '' : status
  state.page = 0
}

const columns = computed(() => [
  { key: 'vehicle', label: t('transition.vehicle') },
  { key: 'usage', label: t('transition.usage') },
  { key: 'model', label: t('transition.proposal') },
  { key: 'status', label: t('transition.eligibility') },
  { key: 'saving', label: t('transition.saving'), align: 'right' as const, sort: 'annualSaving' },
  { key: 'co2', label: t('transition.co2'), align: 'right' as const },
  { key: 'renewal', label: t('transition.renewal'), sort: 'renewalDate' },
])
const sortModel = computed({
  get: () => (state.sort === 'renewalDate' ? 'renewalDate,asc' : 'annualSaving,desc'),
  set: (value: string | undefined) => { state.sort = value?.startsWith('renewalDate') ? 'renewalDate' : 'annualSaving' },
})

const selected = ref<EnergyAssessment | null>(null)
const detailOpen = computed({ get: () => selected.value !== null, set: (o: boolean) => { if (!o) selected.value = null } })
const co2Saved = (a: EnergyAssessment) => (a.co2CurrentKg ?? 0) - (a.co2EvKg ?? 0)
const tonnes = (kg: number) => f.number(kg / 1000, 1)
</script>

<template>
  <div>
    <UiPageHeader :title="$t('transition.title')" :subtitle="$t('transition.subtitle')">
      <template #actions>
        <select v-if="session.isStaff" v-model="state.company" class="select company" :aria-label="$t('vehicle.company')"
          @change="state.page = 0">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('transition.zeroShare')" :value="s ? `${f.number(s.zeroEmissionShare, 1)} %` : '-'" :icon="Leaf"
        tone="success" :hint="s ? $t('transition.potentialShare', { share: f.number(s.potentialShare, 1) }) : undefined" />
      <UiKpi :label="$t('transition.savingPotential')" :value="s ? f.money(s.annualSavingPotential) : '-'" :icon="PiggyBank"
        :hint="$t('transition.savingHint')" />
      <UiKpi :label="$t('transition.co2Avoidable')" :value="s ? `${f.number(s.co2AvoidableTonnes, 0)} t` : '-'" :icon="CloudOff"
        :hint="s ? $t('transition.co2Hint', { total: f.number(s.co2CurrentTonnes, 0) }) : undefined" />
      <UiKpi :label="$t('transition.zfe')" :value="s ? f.number(s.zfeExposed) : '-'" :icon="MapPinOff"
        :tone="s?.zfeBannedNow ? 'danger' : 'default'"
        :hint="s ? $t('transition.zfeHint', { n: f.number(s.zfeBannedNow) }) : undefined" />
    </div>

    <div class="top">
      <section class="card">
        <header class="card-header">
          <h2>{{ $t('transition.fleetEligibility') }}</h2>
          <span v-if="s?.computedAt" class="xs muted">{{ $t('transition.computedAt', { when: f.dateTime(s.computedAt) }) }}</span>
        </header>
        <div class="card-body">
          <div class="bar" role="img" :aria-label="$t('transition.fleetEligibility')">
            <span v-for="seg in segments" :key="seg.status" :style="{ width: `${seg.width}%`, background: seg.color }"
              :title="`${$t(`enum.Eligibility.${seg.status}`)} : ${seg.count}`" />
          </div>
          <ul class="legend" data-cy="eligibility">
            <li v-for="seg in segments" :key="seg.status">
              <button class="legend-item" :class="{ active: state.status === seg.status }" @click="filterStatus(seg.status)">
                <span class="dot" :style="{ background: seg.color }" />
                <span class="small grow">{{ $t(`enum.Eligibility.${seg.status}`) }}</span>
                <span class="num small strong">{{ f.number(seg.count) }}</span>
                <span class="num xs muted pct">{{ f.number(seg.width, 0) }} %</span>
              </button>
            </li>
          </ul>
          <p class="xs muted note">{{ $t('transition.method') }}</p>
        </div>
      </section>

      <section class="card">
        <header class="card-header">
          <h2>{{ $t('transition.quotas') }}</h2>
        </header>
        <div class="card-body stack-sm">
          <p class="xs muted">{{ $t('transition.quotasHint') }}</p>
          <div v-for="q in plan?.quotas ?? []" :key="q.year" class="quota" data-cy="quota">
            <div class="row-between">
              <span class="small strong">{{ q.year }}</span>
              <span class="badge" :class="q.met ? 'success' : 'warning'">
                {{ q.met ? $t('transition.met') : $t('transition.notMet') }}
              </span>
            </div>
            <div class="progress">
              <span class="fill" :class="{ met: q.met }" :style="{ width: `${Math.min(100, q.share)}%` }" />
              <span class="target" :style="{ left: `${q.objective}%` }" />
            </div>
            <p class="xs muted">
              {{ $t('transition.quotaLine', { low: q.lowEmissionRenewals, renewals: q.renewals, share: f.number(q.share, 0), objective: q.objective }) }}
            </p>
          </div>
        </div>
      </section>
    </div>

    <section class="card plan">
      <header class="card-header">
        <h2>{{ $t('transition.plan') }}</h2>
        <span class="xs muted">{{ $t('transition.planHint') }}</span>
      </header>
      <div class="card-body plan-body">
        <ChartBars :bars="quarterBars" :format="v => `${f.number(v)} véh.`" height="180px" />
        <div class="table-wrap">
        <table class="table compact">
          <thead>
            <tr>
              <th>{{ $t('transition.quarter') }}</th>
              <th class="right">{{ $t('transition.switched') }}</th>
              <th class="right">{{ $t('transition.saving') }}</th>
              <th class="right">{{ $t('transition.co2') }}</th>
              <th class="right">{{ $t('transition.shareAfter') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="q in plan?.quarters ?? []" :key="q.quarter">
              <td class="small">{{ q.quarter }}</td>
              <td class="num small right">{{ q.vehicles }}</td>
              <td class="num small right">{{ f.money(q.annualSaving) }}</td>
              <td class="num small right">{{ f.number(q.co2AvoidedTonnes, 1) }} t</td>
              <td class="num small right">{{ f.number(q.zeroEmissionShareAfter, 1) }} %</td>
            </tr>
          </tbody>
        </table>
        </div>
      </div>
    </section>

    <section class="card" data-cy="assessments">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('transition.search')" class="grow" />
        <select v-model="state.status" class="select" :aria-label="$t('transition.eligibility')" @change="state.page = 0">
          <option value="">{{ $t('transition.eligibility') }} : {{ $t('app.all') }}</option>
          <option v-for="e in ELIGIBILITIES" :key="e" :value="e">{{ $t(`enum.Eligibility.${e}`) }}</option>
        </select>
        <select v-model="state.category" class="select" :aria-label="$t('vehicle.category')" @change="state.page = 0">
          <option value="">{{ $t('vehicle.category') }} : {{ $t('app.all') }}</option>
          <option v-for="c in ['CAR', 'VAN', 'TRUCK', 'MACHINE']" :key="c" :value="c">{{ f.label('VehicleCategory', c) }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="sortModel" :columns="columns" :rows="data?.energyAssessments.content ?? []"
        row-key="vehicleId" :loading="pending && !data" clickable :empty-text="$t('transition.empty')"
        @row-click="row => selected = row">
        <template #cell-vehicle="{ row }">
          <div class="small strong">{{ row.registration }}</div>
          <div class="xs muted">{{ row.make }} {{ row.model }} · {{ f.label('Energy', row.energy) }}</div>
        </template>
        <template #cell-usage="{ row }">
          <template v-if="row.dataSource === 'TELEMETRY'">
            <div class="small num">{{ $t('transition.p95', { km: f.number(row.p95DailyKm) }) }}</div>
            <div class="xs muted num">{{ $t('transition.longest', { km: f.number(row.longestDayKm) }) }}</div>
          </template>
          <span v-else class="xs muted">{{ $t('transition.estimated', { km: f.number(row.annualKm) }) }}</span>
        </template>
        <template #cell-model="{ row }">
          <template v-if="row.evModel">
            <div class="small">{{ row.evModel.name }}</div>
            <div class="xs muted num">{{ $t('transition.range', { km: row.evModel.realisticRangeKm }) }}</div>
          </template>
          <span v-else class="xs muted">-</span>
        </template>
        <template #cell-status="{ row }">
          <span class="badge" :class="TONES[row.status]">{{ $t(`enum.Eligibility.${row.status}`) }}</span>
          <div v-if="row.zfeZone" class="xs zfe">{{ $t('transition.zfeShort') }}</div>
        </template>
        <template #cell-saving="{ row }">
          <span v-if="row.annualSaving !== null && SWITCHABLE.includes(row.status)" class="num small"
            :class="row.annualSaving >= 0 ? 'gain' : 'loss'">
            {{ row.annualSaving >= 0 ? '+' : '' }}{{ f.money(row.annualSaving) }}
          </span>
          <span v-else class="xs muted">-</span>
        </template>
        <template #cell-co2="{ row }">
          <span v-if="row.evModel && SWITCHABLE.includes(row.status)" class="num small">{{ tonnes(co2Saved(row)) }} t</span>
          <span v-else class="xs muted">-</span>
        </template>
        <template #cell-renewal="{ row }"><span class="small nowrap">{{ row.renewalDate ? f.date(row.renewalDate) : '-' }}</span></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.energyAssessments.totalPages"
        :total-elements="data.energyAssessments.totalElements" />
    </section>

    <UiModal v-model:open="detailOpen" :title="selected ? `${selected.registration} · ${selected.make} ${selected.model}` : ''" width="720px">
      <div v-if="selected" class="stack" data-cy="assessment">
        <div class="row-between">
          <span class="badge" :class="TONES[selected.status]">{{ $t(`enum.Eligibility.${selected.status}`) }}</span>
          <span class="xs muted">{{ selected.dataSource === 'TELEMETRY' ? $t('transition.measured', { days: selected.activeDays }) : $t('transition.fromOdometer') }}</span>
        </div>
        <p class="reason">{{ selected.reason }}</p>

        <div class="facts-grid">
          <div class="fact"><span class="xs muted">{{ $t('transition.annualKm') }}</span><strong class="num">{{ f.km(selected.annualKm) }}</strong></div>
          <div class="fact"><span class="xs muted">{{ $t('transition.p95Label') }}</span><strong class="num">{{ selected.p95DailyKm !== null ? f.km(Math.round(selected.p95DailyKm)) : '-' }}</strong></div>
          <div class="fact"><span class="xs muted">{{ $t('transition.required') }}</span><strong class="num">{{ selected.requiredRangeKm ? f.km(selected.requiredRangeKm) : '-' }}</strong></div>
          <div class="fact"><span class="xs muted">{{ $t('transition.beyond') }}</span><strong class="num">{{ selected.daysBeyondRange ?? '-' }}</strong></div>
        </div>

        <section v-if="selected.evModel" class="model">
          <div class="row-between">
            <div>
              <p class="xs muted">{{ $t('transition.proposal') }}</p>
              <p class="strong">{{ selected.evModel.name }}</p>
            </div>
            <span class="num small">{{ $t('transition.rent', { amount: f.money(selected.evModel.monthlyRent) }) }}</span>
          </div>
          <p class="xs muted">
            {{ $t('transition.modelLine', { battery: f.number(selected.evModel.batteryKwh), wltp: selected.evModel.wltpRangeKm, real: selected.evModel.realisticRangeKm, kwh: f.number(selected.evModel.consumptionKwh100km, 1), dc: selected.evModel.fastChargeKw }) }}
          </p>
        </section>

        <div v-if="selected.currentAnnualCost !== null" class="compare">
          <div>
            <p class="xs muted">{{ $t('transition.costToday') }}</p>
            <p class="num strong">{{ f.money(selected.currentAnnualCost) }}</p>
            <div class="cost-bar"><span :style="{ width: '100%' }" /></div>
          </div>
          <div>
            <p class="xs muted">{{ $t('transition.costEv') }}</p>
            <p class="num strong">{{ f.money(selected.evAnnualCost) }}</p>
            <div class="cost-bar ev"><span :style="{ width: `${Math.min(100, (selected.evAnnualCost! / selected.currentAnnualCost) * 100)}%` }" /></div>
          </div>
          <div>
            <p class="xs muted">{{ $t('transition.co2Year') }}</p>
            <p class="num strong">{{ tonnes(selected.co2CurrentKg ?? 0) }} t → {{ tonnes(selected.co2EvKg ?? 0) }} t</p>
          </div>
        </div>
        <p class="xs muted">{{ $t('transition.costNote') }}</p>

        <div class="row-between small">
          <span>{{ $t('transition.renewalOn', { date: selected.renewalDate ? f.date(selected.renewalDate, 'long') : '-' }) }}</span>
          <span v-if="selected.zfeZone" class="zfe">{{ $t('transition.zfeLine', { zone: selected.zfeZone, date: f.date(selected.zfeBannedFrom) }) }}</span>
        </div>
        <NuxtLink :to="`/vehicules/${selected.vehicleId}`" class="btn btn-sm self-start"><Gauge /> {{ $t('transition.openVehicle') }}</NuxtLink>
      </div>
    </UiModal>
  </div>
</template>

<style scoped>
.company {
  width: auto;
  min-width: 220px;
}

.kpis {
  margin-bottom: 16px;
}

.top {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.bar {
  display: flex;
  height: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--neutral-soft);
}

.bar span {
  height: 100%;
}

.legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px 16px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.legend-item:hover { background: var(--surface-hover); }

.legend-item.active {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.dot {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 3px;
}

.grow { flex: 1; }
.strong { font-weight: 600; }

.pct {
  width: 42px;
  text-align: right;
}

.note {
  margin-top: 12px;
}

.progress {
  position: relative;
  height: 8px;
  margin: 6px 0 4px;
  border-radius: 999px;
  background: var(--neutral-soft);
}

.progress .fill {
  position: absolute;
  inset: 0 auto 0 0;
  border-radius: 999px;
  background: #f79009;
}

.progress .fill.met {
  background: var(--primary);
}

.progress .target {
  position: absolute;
  top: -3px;
  width: 2px;
  height: 14px;
  background: var(--text);
}

.plan {
  margin-bottom: 16px;
}

.plan-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 20px;
  align-items: start;
}

.table.compact td,
.table.compact th {
  padding: 6px 10px;
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

.gain { color: var(--success); font-weight: 600; }
.loss { color: var(--warning); }

.zfe {
  color: var(--danger);
}

.reason {
  font-size: var(--text-md);
}

.facts-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.fact {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.model {
  padding: 12px 14px;
  border-radius: var(--radius);
  background: var(--primary-soft);
}

.compare {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.cost-bar {
  height: 6px;
  margin-top: 4px;
  border-radius: 999px;
  background: var(--neutral-soft);
}

.cost-bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--text-faint);
}

.cost-bar.ev span {
  background: var(--primary);
}

.self-start { align-self: flex-start; }

@media (max-width: 1100px) {
  .top, .plan-body { grid-template-columns: minmax(0, 1fr); }
  .facts-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
