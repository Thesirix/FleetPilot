<script setup lang="ts">
import { CalendarClock, Car, FileWarning, PiggyBank, Radio, ShieldAlert } from 'lucide-vue-next'
import { Q, type Company, type Contract, type Deadline, type FleetSummary, type Page } from '~/graphql/fleet'
import { QQ, type SavingsSummary } from '~/graphql/quotes'
import { TQ, type TelemetrySummary } from '~/graphql/telemetry'

const { t, locale } = useI18n()
const f = useFormat()
const session = useSessionStore()
const firstName = computed(() => session.displayName.split(/[\s.@]+/)[0] ?? '')
useHead({ title: () => `${t('dashboard.title')} · FleetPilot` })

const companyId = useState<string>('dashboard-company', () => '')
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data, pending, error, stale, refresh } = useGraphqlQuery<{
  fleetSummary: FleetSummary
  overdue: Page<Deadline>
  ending: { content: Contract[] }
}>(Q.dashboard, () => ({ companyId: companyId.value || null }), {
  cacheKey: () => `dashboard:${companyId.value}`,
})

const s = computed(() => data.value?.fleetSummary)
const { data: savingsData } = useGraphqlQuery<{ savings: SavingsSummary }>(QQ.savings,
  () => ({ companyId: companyId.value || null }), { cacheKey: () => `savings:${companyId.value}` })
const savings = computed(() => savingsData.value?.savings)
const savingsBars = computed(() => (savings.value?.months ?? []).slice(-6).map(m => ({
  x: new Intl.DateTimeFormat(locale.value === 'fr' ? 'fr-FR' : 'en-GB', { month: 'short' }).format(new Date(`${m.month}-15T12:00:00`)),
  y: Number(m.savings),
})))

const { data: telemetryData } = useGraphqlQuery<{ telemetrySummary: TelemetrySummary }>(TQ.summary,
  () => ({ companyId: companyId.value || null }), { cacheKey: () => `telemetry:${companyId.value}` })
const telemetry = computed(() => telemetryData.value?.telemetrySummary)
const criticalAlerts = computed(() => telemetry.value?.openAlertsByType.find(c => c.type === 'ENGINE_FAULT')?.count ?? 0)

const ENERGY_COLORS: Record<string, string> = {
  ELECTRIC: '#0f766e', HYBRID: '#14b8a6', PLUG_IN_HYBRID: '#5eead4', HYDROGEN: '#0d9488',
  PETROL: '#98a2b3', DIESEL: '#475467', LPG: '#d0d5dd', CNG: '#b7c0cc',
}
const STATUS_COLORS: Record<string, string> = {
  ACTIVE: '#0f766e', IN_REPAIR: '#f79009', IMMOBILIZED: '#d92d20', AWAITING_DELIVERY: '#2e90fa', DISPOSED: '#d0d5dd',
}

const energyItems = computed(() => (s.value?.byEnergy ?? []).map(c => ({
  label: f.label('Energy', c.key), value: c.count, color: ENERGY_COLORS[c.key] ?? '#98a2b3',
})))
const statusItems = computed(() => (s.value?.byStatus ?? []).filter(c => c.key !== 'DISPOSED').map(c => ({
  label: f.label('VehicleStatus', c.key), value: c.count, color: STATUS_COLORS[c.key] ?? '#98a2b3',
})))
const electricShare = computed(() => {
  const electric = (s.value?.byEnergy ?? []).filter(c => c.key === 'ELECTRIC' || c.key === 'HYDROGEN')
    .reduce((sum, c) => sum + c.count, 0)
  return f.percent(electric, s.value?.inFleet ?? 0)
})
const critAir = computed(() => {
  const counts = new Map((s.value?.byCritAir ?? []).map(c => [Number(c.key), c.count]))
  const max = Math.max(1, ...counts.values())
  return [0, 1, 2, 3, 4, 5].map(level => ({ level, count: counts.get(level) ?? 0, width: ((counts.get(level) ?? 0) / max) * 100 }))
})
</script>

<template>
  <div>
    <section class="hero">
      <svg class="hero-map" viewBox="0 0 900 220" preserveAspectRatio="xMaxYMid slice" aria-hidden="true">
        <g class="hero-streets">
          <path d="M380 40 C 520 20, 640 90, 900 50" />
          <path d="M360 150 C 520 120, 700 200, 900 160" />
          <path d="M560 -10 C 540 80, 600 140, 570 230" />
          <path d="M760 -10 C 790 90, 730 150, 780 230" />
        </g>
        <path id="hero-trip" class="hero-trip" d="M470 190 C 540 150, 560 90, 650 100 S 760 160, 850 60" />
        <circle class="hero-stop" cx="470" cy="190" r="5" />
        <circle class="hero-stop" cx="850" cy="60" r="5" />
        <circle class="hero-pulse" cx="850" cy="60" r="5" />
        <g class="hero-vehicle">
          <circle r="9" class="hero-vehicle-halo" />
          <circle r="5" />
          <animateMotion dur="7s" repeatCount="indefinite" rotate="auto">
            <mpath href="#hero-trip" />
          </animateMotion>
        </g>
      </svg>
      <div class="hero-text">
        <p class="hero-hello">{{ $t('dashboard.hello', { name: firstName }) }}</p>
        <h1>{{ $t('dashboard.title') }}</h1>
        <p v-if="s" class="hero-sub">{{ $t('dashboard.subtitle', { date: f.date(s.asOf, 'long') }) }}</p>
        <ul v-if="s" class="hero-chips">
          <li><span class="hero-dot" />{{ $t('dashboard.chipVehicles', { value: f.number(s.inFleet) }) }}</li>
          <li><span class="hero-dot" />{{ $t('dashboard.electricShare', { value: electricShare }) }}</li>
          <li v-if="telemetry"><span class="hero-dot live" />{{ $t('dashboard.chipMoving', { value: f.number(telemetry.moving) }) }}</li>
        </ul>
      </div>
      <select v-if="session.isStaff" v-model="companyId" class="select company-select" :aria-label="$t('vehicle.company')">
        <option value="">{{ $t('dashboard.allCompanies') }}</option>
        <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
    </section>

    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="stack">
      <div class="grid grid-4">
        <UiKpi :label="$t('dashboard.inFleet')" :value="s ? f.number(s.inFleet) : '…'" :icon="Car" to="/vehicules"
          :hint="s ? $t('dashboard.assigned', { value: f.number(s.assignedVehicles) }) : undefined" />
        <UiKpi :label="$t('dashboard.overdue')" :value="s ? f.number(s.overdueDeadlines) : '…'" :icon="CalendarClock"
          :tone="s && s.overdueDeadlines > 0 ? 'danger' : 'default'" to="/echeances"
          :hint="s ? $t('dashboard.dueSoon', { value: f.number(s.dueSoonDeadlines) }) : undefined" />
        <UiKpi :label="$t('dashboard.overrun')" :value="s ? f.number(s.contractsOverrunRisk) : '…'" :icon="FileWarning"
          :tone="s && s.contractsOverrunRisk > 0 ? 'warning' : 'default'" :hint="$t('dashboard.overrunHint')" to="/contrats" />
        <UiKpi :label="$t('dashboard.openIncidents')" :value="s ? f.number(s.openIncidents) : '…'" :icon="ShieldAlert"
          :hint="s ? $t('dashboard.incidentsYear', { value: f.number(s.incidentsLast12Months) }) : undefined" to="/sinistres" />
      </div>

      <div class="grid grid-3">
        <section class="card">
          <header class="card-header">
            <h2>{{ $t('dashboard.energyMix') }}</h2>
            <span class="badge primary plain">{{ $t('dashboard.electricShare', { value: electricShare }) }}</span>
          </header>
          <div class="card-body">
            <ChartDonut v-if="s" :items="energyItems" :center-value="f.number(s.inFleet)" :center-label="$t('nav.vehicles')" />
            <span v-else class="skeleton" style="height: 150px" />
          </div>
        </section>

        <section class="card">
          <header class="card-header"><h2>{{ $t('dashboard.byStatus') }}</h2></header>
          <div class="card-body">
            <ChartDonut v-if="s" :items="statusItems" />
            <span v-else class="skeleton" style="height: 150px" />
          </div>
        </section>

        <section class="card">
          <header class="card-header"><h2>{{ $t('dashboard.critAir') }}</h2></header>
          <div class="card-body stack-sm">
            <div v-for="row in critAir" :key="row.level" class="critair-row">
              <CritAirBadge :value="row.level" />
              <div class="bar"><span :style="{ width: `${row.width}%` }" :class="{ warn: row.level >= 3 }" /></div>
              <span class="num small secondary count">{{ f.number(row.count) }}</span>
            </div>
            <p class="xs muted">{{ $t('dashboard.critAirHint') }}</p>
          </div>
        </section>
      </div>

      <div class="grid grid-4">
        <div class="mini card"><span class="muted small">{{ $t('dashboard.rent') }}</span>
          <strong class="num">{{ s ? f.money(s.monthlyRentTotal) : '…' }}</strong></div>
        <div class="mini card"><span class="muted small">{{ $t('dashboard.averageAge') }}</span>
          <strong class="num">{{ s ? $t('dashboard.months', { value: f.number(s.averageAgeMonths) }) : '…' }}</strong></div>
        <div class="mini card"><span class="muted small">{{ $t('dashboard.averageCo2') }}</span>
          <strong class="num">{{ s ? `${f.number(s.averageCo2GramsPerKm)} g/km` : '…' }}</strong></div>
        <div class="mini card"><span class="muted small">{{ $t('dashboard.drivers') }}</span>
          <strong class="num">{{ s ? f.number(s.activeDrivers) : '…' }}</strong></div>
      </div>

      <section class="card savings">
        <div class="savings-main">
          <span class="savings-icon"><PiggyBank :size="20" /></span>
          <div>
            <span class="muted small">{{ $t('savings.dashboardCard') }}</span>
            <strong class="num savings-value">{{ savings ? f.money(savings.savings) : '…' }}</strong>
            <span class="xs muted">{{ savings ? $t('savings.rate', { rate: f.number(savings.savingsRate, 1) }) : '' }}</span>
          </div>
        </div>
        <div class="savings-stat">
          <span class="muted small">{{ $t('savings.pending') }}</span>
          <strong class="num">{{ savings ? f.money(savings.pendingOvercharge) : '…' }}</strong>
          <NuxtLink to="/devis?open=true" class="xs">{{ savings ? $t('savings.dashboardHint', { count: savings.openQuotes }) : '' }}</NuxtLink>
        </div>
        <div class="savings-stat">
          <span class="muted small">{{ $t('savings.autoApproval') }}</span>
          <strong class="num">{{ savings ? `${f.number(savings.autoApprovalRate, 1)} %` : '…' }}</strong>
          <span class="xs muted">{{ $t('savings.autoApprovalHint') }}</span>
        </div>
        <div class="savings-chart">
          <ChartBars v-if="savings" :bars="savingsBars" :format="v => f.money(v)" height="84px" compact />
        </div>
      </section>

      <section class="card connected" data-cy="dashboard-connected">
        <div class="savings-main">
          <span class="savings-icon"><Radio :size="20" /></span>
          <div>
            <span class="muted small">{{ $t('dashboard.connected') }}</span>
            <strong class="num savings-value">{{ telemetry ? f.number(telemetry.connected) : '…' }}</strong>
            <NuxtLink to="/telemetrie" class="xs">{{ $t('dashboard.connectedHint') }}</NuxtLink>
          </div>
        </div>
        <div class="savings-stat">
          <span class="muted small">{{ $t('enum.Activity.MOVING') }}</span>
          <strong class="num">{{ telemetry ? f.number(telemetry.moving) : '…' }}</strong>
          <span class="xs muted">{{ telemetry ? $t('dashboard.kmToday', { km: f.number(Math.round(telemetry.kilometresToday)) }) : '' }}</span>
        </div>
        <div class="savings-stat">
          <span class="muted small">{{ $t('telemetry.openAlerts') }}</span>
          <strong class="num">{{ telemetry ? f.number(telemetry.openAlerts) : '…' }}</strong>
          <NuxtLink to="/alertes" class="xs">{{ $t('telemetry.seeAlerts') }}</NuxtLink>
        </div>
        <div class="savings-stat">
          <span class="muted small">{{ $t('enum.AlertType.ENGINE_FAULT') }}</span>
          <strong class="num" :class="{ critical: criticalAlerts }">{{ telemetry ? f.number(criticalAlerts) : '…' }}</strong>
          <NuxtLink v-if="criticalAlerts" to="/alertes?type=ENGINE_FAULT" class="xs">{{ $t('dashboard.toHandle') }}</NuxtLink>
          <span v-else class="xs muted">{{ $t('dashboard.noCritical') }}</span>
        </div>
      </section>

      <div class="grid grid-2">
        <section class="card">
          <header class="card-header">
            <h2>{{ $t('dashboard.urgentDeadlines') }}</h2>
            <NuxtLink to="/echeances?urgency=OVERDUE" class="small">{{ $t('app.seeAll') }}</NuxtLink>
          </header>
          <div class="card-body flush">
            <UiDataTable :loading="pending" :skeleton-rows="5" clickable :empty-text="$t('deadline.empty')"
              :columns="[
                { key: 'vehicle', label: $t('vehicle.vehicle') },
                { key: 'type', label: $t('deadline.type') },
                { key: 'remaining', label: $t('deadline.remaining') },
              ]" :rows="data?.overdue.content ?? []" @row-click="row => navigateTo(`/vehicules/${row.vehicle.id}`)">
              <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
              <template #cell-type="{ row }">{{ f.label('DeadlineType', row.type) }}</template>
              <template #cell-remaining="{ row }">
                <DeadlineRemaining :days-remaining="row.daysRemaining" :kilometres-remaining="row.kilometresRemaining" />
              </template>
            </UiDataTable>
          </div>
        </section>

        <section class="card">
          <header class="card-header">
            <h2>{{ $t('dashboard.endingContracts') }}</h2>
            <NuxtLink to="/contrats" class="small">{{ $t('app.seeAll') }}</NuxtLink>
          </header>
          <div class="card-body flush">
            <UiDataTable :loading="pending" :skeleton-rows="5" clickable :empty-text="$t('contract.empty')"
              :columns="[
                { key: 'vehicle', label: $t('vehicle.vehicle') },
                { key: 'lessor', label: $t('contract.lessor') },
                { key: 'end', label: $t('contract.end') },
              ]" :rows="data?.ending.content ?? []" @row-click="row => navigateTo(`/contrats/${row.id}`)">
              <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
              <template #cell-end="{ row }">
                <span class="nowrap">{{ f.date(row.endDate) }}</span>
                <span class="muted xs"> · {{ $t('contract.endingIn', { count: row.daysRemaining }, row.daysRemaining ?? 0) }}</span>
              </template>
            </UiDataTable>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Welcome banner: the brand gradient with a vehicle driving its route, as on the sign-in page */
.hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 24px 28px;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: linear-gradient(120deg, #0b4f4a 0%, #0f766e 55%, #14a394 100%);
  color: #fff;
  isolation: isolate;
  animation: rise 0.45s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.hero::before {
  content: "";
  position: absolute;
  inset: -40%;
  z-index: -1;
  background:
    radial-gradient(320px 200px at 15% 20%, rgba(255, 255, 255, 0.14), transparent 70%),
    radial-gradient(380px 260px at 80% 90%, rgba(94, 234, 212, 0.3), transparent 70%);
  animation: hero-drift 16s ease-in-out infinite alternate;
}

.hero-map {
  position: absolute;
  inset: 0;
  z-index: -1;
  width: 100%;
  height: 100%;
}

.hero-streets path {
  fill: none;
  stroke: rgba(255, 255, 255, 0.08);
  stroke-width: 12;
  stroke-linecap: round;
}

.hero-trip {
  fill: none;
  stroke: rgba(255, 255, 255, 0.6);
  stroke-width: 2;
  stroke-dasharray: 2 8;
  stroke-linecap: round;
  animation: hero-dash 1.5s linear infinite;
}

.hero-stop { fill: #fff; }

.hero-pulse {
  fill: none;
  stroke: #fff;
  stroke-width: 2;
  transform-box: fill-box;
  transform-origin: center;
  animation: hero-pulse 2.4s ease-out infinite;
}

.hero-vehicle circle { fill: #5eead4; }
.hero-vehicle .hero-vehicle-halo { fill: rgba(94, 234, 212, 0.3); }

.hero-text {
  min-width: 0;
}

.hero-hello {
  margin: 0 0 2px;
  color: rgba(255, 255, 255, 0.78);
  font-size: var(--text-sm);
  font-weight: 500;
}

.hero h1 {
  margin: 0;
  color: #fff;
  font-size: 26px;
  letter-spacing: -0.02em;
}

.hero-sub {
  margin: 4px 0 14px;
  color: rgba(255, 255, 255, 0.82);
  font-size: var(--text-sm);
}

.hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.hero-chips li {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 11px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(6px);
  font-size: var(--text-xs);
  font-weight: 500;
}

.hero-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #5eead4;
}

.hero-dot.live {
  box-shadow: 0 0 0 0 rgba(94, 234, 212, 0.7);
  animation: hero-live 1.8s ease-out infinite;
}

.hero .company-select {
  border-color: transparent;
  box-shadow: var(--shadow);
}

@keyframes hero-drift {
  from { transform: translate3d(-3%, -2%, 0); }
  to { transform: translate3d(3%, 3%, 0); }
}

@keyframes hero-dash {
  to { stroke-dashoffset: -20; }
}

@keyframes hero-pulse {
  from { opacity: 0.9; transform: scale(0.8); }
  to { opacity: 0; transform: scale(3); }
}

@keyframes hero-live {
  to { box-shadow: 0 0 0 7px rgba(94, 234, 212, 0); }
}

@media (max-width: 720px) {
  .hero {
    flex-direction: column;
    align-items: stretch;
    padding: 20px;
  }

  .hero .company-select {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-vehicle { display: none; }
}

.connected .critical {
  color: var(--danger);
}

.connected .savings-icon {
  background: var(--primary-soft);
  color: var(--primary);
}

.connected .savings-value {
  color: var(--text);
}

.savings,
.connected {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 20px;
  align-items: center;
  padding: 16px 20px;
}

.savings-main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.savings-main > div,
.savings-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.savings-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: var(--radius);
  background: var(--success-soft);
  color: var(--success);
}

.savings-value {
  color: var(--success);
  font-size: var(--text-2xl);
  font-weight: 650;
}

.savings-stat strong {
  font-size: var(--text-lg);
}

@media (max-width: 1100px) {
  .savings,
  .connected {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.company-select {
  width: auto;
  min-width: 240px;
}

.critair-row {
  display: grid;
  grid-template-columns: 22px 1fr 56px;
  align-items: center;
  gap: 10px;
}

.bar {
  height: 8px;
  border-radius: 4px;
  background: var(--neutral-soft);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--chart-4);
}

.bar span.warn {
  background: var(--critair-3);
}

.count {
  text-align: right;
}

.mini {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 16px;
}

.mini strong {
  font-size: var(--text-lg);
  font-weight: 600;
}
</style>
