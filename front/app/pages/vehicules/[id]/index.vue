<script setup lang="ts">
import { M, Q, type Deadline, type Vehicle } from '~/graphql/fleet'
import { QQ, type QuoteSummary } from '~/graphql/quotes'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const id = computed(() => route.params.id as string)

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ vehicle: Vehicle | null }>(Q.vehicle,
  () => ({ id: id.value }), { cacheKey: () => `vehicle:${id.value}` })
const v = computed(() => data.value?.vehicle ?? null)
// Garage quotes come from the quote service: the gateway joins both services for this screen
const { data: quoteData } = useGraphqlQuery<{ vehicle: { quotes: QuoteSummary[] } | null }>(QQ.vehicleQuotes,
  () => ({ id: id.value }), { cacheKey: () => `vehicle-quotes:${id.value}` })
const quotes = computed(() => quoteData.value?.vehicle?.quotes ?? [])
useHead({ title: () => (v.value ? `${v.value.registration} · FleetPilot` : 'FleetPilot') })

const tab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'overview')
const tabs = computed(() => [
  { key: 'overview', label: t('vehicle.overview') },
  { key: 'deadlines', label: t('vehicle.deadlines'), count: v.value?.openDeadlines.length },
  { key: 'history', label: t('vehicle.history'), count: v.value?.assignments?.length },
  { key: 'incidents', label: t('vehicle.incidents'), count: v.value?.incidents?.length },
  { key: 'quotes', label: t('quote.vehicleTab'), count: quotes.value.length },
  { key: 'telemetry', label: t('telemetry.vehicleTab') },
  { key: 'fuel', label: t('driverRecords.fuel') },
  { key: 'inspections', label: t('driverRecords.inspections') },
])

const mileagePoints = computed(() => dailyReadings(v.value?.mileageHistory ?? [])
  .map(r => ({ x: f.date(r.readAt, 'short'), y: r.mileage })))

const contract = computed(() => v.value?.activeContract ?? null)
const contractOpen = ref(false)
const projectionRatio = computed(() => {
  const c = contract.value
  if (!c?.projection) return 0
  return Math.min(140, (c.projection.projectedMileage / c.contractualMileage) * 100)
})

// Completing a deadline from the file
const completing = ref<Deadline | null>(null)
const completionMileage = ref(0)
const { run, running } = useMutation()
async function complete() {
  const result = await run<{ completeDeadline: { next: { dueDate: string | null } | null } }>(M.completeDeadline,
    { id: completing.value!.id, mileage: Number(completionMileage.value) })
  if (result) {
    const next = result.completeDeadline.next
    useToast().success(next?.dueDate ? t('deadline.nextScheduled', { date: f.date(next.dueDate) }) : t('deadline.completed'))
    completing.value = null
    refresh()
  }
}
function openCompletion(deadline: Deadline) {
  completionMileage.value = v.value?.mileage ?? 0
  completing.value = deadline
}
const completionOpen = computed({ get: () => completing.value !== null, set: (o: boolean) => { if (!o) completing.value = null } })
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !v" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />

    <template v-if="v">
      <UiPageHeader :title="v.registration" :subtitle="`${v.make} ${v.model}${v.trimLevel ? ' ' + v.trimLevel : ''}`" back="/vehicules">
        <template #badges>
          <FleetBadge type="VehicleStatus" :value="v.status" />
          <FleetBadge type="Energy" :value="v.energy" />
          <CritAirBadge :value="v.critAir" />
        </template>
        <template #meta>
          <p class="small muted meta">{{ v.company.name }}<template v-if="v.site"> · {{ v.site.name }}</template></p>
        </template>
        <template #actions><VehicleActions :vehicle="v" @changed="refresh" /></template>
      </UiPageHeader>

      <UiTabs v-model="tab" :tabs="tabs" class="tabs" />

      <div v-if="tab === 'overview'" class="overview">
        <div class="stack">
          <section class="card">
            <header class="card-header"><h2>{{ $t('vehicle.currentDriver') }}</h2></header>
            <div class="card-body">
              <div v-if="v.currentAssignment" class="row-between">
                <div>
                  <NuxtLink :to="`/conducteurs/${v.currentAssignment.driver.id}`" class="strong">
                    {{ v.currentAssignment.driver.fullName }}
                  </NuxtLink>
                  <p class="small muted">{{ v.currentAssignment.driver.employeeNumber }} ·
                    {{ f.label('AssignmentKind', v.currentAssignment.kind) }} ·
                    {{ $t('vehicle.since', { date: f.date(v.currentAssignment.startDate) }) }}</p>
                </div>
                <span class="num secondary small">{{ f.km(v.currentAssignment.drivenKilometres) }}</span>
              </div>
              <p v-else class="muted small">{{ $t('vehicle.noDriver') }}</p>
            </div>
          </section>

          <section class="card">
            <header class="card-header">
              <h2>{{ $t('vehicle.mileageHistory') }}</h2>
              <span class="small muted">{{ $t('vehicle.mileageReadAt', { date: f.date(v.mileageReadAt) }) }}</span>
            </header>
            <div class="card-body">
              <p class="big num">{{ f.km(v.mileage) }}</p>
              <ChartLine v-if="mileagePoints.length > 1" :points="mileagePoints" unit="km" height="200px" />
            </div>
          </section>

          <section class="card">
            <header class="card-header">
              <h2>{{ $t('vehicle.contract') }}</h2>
              <span v-if="contract?.projection" class="badge" :class="contract.projection.overrunRisk ? 'warning' : 'success'">
                {{ contract.projection.overrunRisk ? $t('contract.atRisk') : $t('contract.onTrack') }}
              </span>
            </header>
            <div class="card-body">
              <div v-if="!contract" class="row-between">
                <p class="muted small">{{ $t('vehicle.noContract') }}</p>
                <button v-if="v.status !== 'DISPOSED' && v.acquisition === 'LEASE'" class="btn btn-sm" @click="contractOpen = true">
                  {{ $t('vehicle.addContract') }}
                </button>
              </div>
              <div v-else class="stack">
                <dl class="dl">
                  <dt>{{ $t('contract.lessor') }}</dt>
                  <dd>{{ contract.lessor }} · <NuxtLink :to="`/contrats/${contract.id}`" class="mono">{{ contract.contractNumber }}</NuxtLink></dd>
                  <dt>{{ $t('contract.duration') }}</dt>
                  <dd>{{ f.date(contract.startDate) }} → {{ f.date(contract.endDate) }} ({{ $t('contract.months', { count: contract.durationMonths }) }})</dd>
                  <dt>{{ $t('contract.rent') }}</dt><dd>{{ $t('app.perMonth', { value: f.money(contract.monthlyRent, 2) }) }}</dd>
                  <dt>{{ $t('contract.services') }}</dt>
                  <dd>
                    <span v-if="contract.maintenanceIncluded" class="badge plain">{{ $t('contract.maintenance') }}</span>
                    <span v-if="contract.tyresIncluded" class="badge plain">{{ $t('contract.tyres') }}</span>
                    <span v-if="contract.replacementVehicle" class="badge plain">{{ $t('contract.replacement') }}</span>
                  </dd>
                </dl>
                <div v-if="contract.projection" class="projection">
                  <div class="row-between small">
                    <span class="muted">{{ $t('contract.projected') }}</span>
                    <span class="num"><strong>{{ f.km(contract.projection.projectedMileage) }}</strong> / {{ f.km(contract.contractualMileage) }}</span>
                  </div>
                  <div class="gauge"><span :style="{ width: `${projectionRatio / 1.4}%` }"
                    :class="{ over: contract.projection.overrunRisk }" /><i class="limit" /></div>
                  <p class="xs muted">{{ $t('contract.dailyAverage') }} : {{ f.km(contract.projection.dailyAverageKm) }} ·
                    {{ $t('contract.overrun') }} : {{ contract.projection.projectedOverrunKm > 0 ? '+' : '' }}{{ f.km(contract.projection.projectedOverrunKm) }}</p>
                </div>
              </div>
            </div>
          </section>
          <ContractDialog v-if="contractOpen" v-model:open="contractOpen" :vehicle-id="v.id" :registration="v.registration"
            @created="refresh" />
        </div>

        <div class="stack">
          <section class="card">
            <header class="card-header"><h2>{{ $t('vehicle.identity') }}</h2></header>
            <div class="card-body">
              <dl class="dl">
                <dt>{{ $t('vehicle.registration') }}</dt><dd class="mono">{{ v.registration }}</dd>
                <dt>{{ $t('vehicle.vin') }}</dt><dd class="mono">{{ v.vin }}</dd>
                <dt>{{ $t('vehicle.firstRegistration') }}</dt><dd>{{ f.date(v.firstRegistrationDate) }}</dd>
                <dt>{{ $t('vehicle.acquisition') }}</dt><dd>{{ f.label('Acquisition', v.acquisition) }}</dd>
              </dl>
            </div>
          </section>
          <section class="card">
            <header class="card-header"><h2>{{ $t('vehicle.technical') }}</h2></header>
            <div class="card-body">
              <dl class="dl">
                <dt>{{ $t('vehicle.category') }}</dt><dd>{{ f.label('VehicleCategoryLong', v.category) }}</dd>
                <dt>{{ $t('vehicle.energy') }}</dt><dd>{{ f.label('Energy', v.energy) }}</dd>
                <dt>{{ $t('vehicle.co2') }}</dt><dd>{{ v.co2GramsPerKm }} g/km</dd>
                <dt>{{ $t('vehicle.critAir') }}</dt><dd><CritAirBadge :value="v.critAir" /></dd>
                <dt>{{ $t('vehicle.fiscalPower') }}</dt><dd>{{ v.fiscalPower ? `${v.fiscalPower} CV` : '-' }}</dd>
              </dl>
            </div>
          </section>
          <section class="card">
            <header class="card-header"><h2>{{ $t('vehicle.fleet') }}</h2></header>
            <div class="card-body">
              <dl class="dl">
                <dt>{{ $t('vehicle.company') }}</dt><dd>{{ v.company.name }}</dd>
                <dt>{{ $t('vehicle.site') }}</dt><dd>{{ v.site ? `${v.site.name}` : '-' }}</dd>
                <dt>{{ $t('vehicle.costCenter') }}</dt><dd>{{ v.costCenter ? `${v.costCenter.code} · ${v.costCenter.label}` : '-' }}</dd>
              </dl>
            </div>
          </section>
        </div>
      </div>

      <section v-else-if="tab === 'deadlines'" class="card">
        <UiDataTable :rows="v.openDeadlines" :empty-text="$t('vehicle.noDeadline')" :columns="[
          { key: 'type', label: $t('deadline.type') },
          { key: 'dueDate', label: $t('deadline.dueDate') },
          { key: 'dueMileage', label: $t('deadline.dueMileage'), align: 'right' },
          { key: 'remaining', label: $t('deadline.remaining') },
          { key: 'urgency', label: $t('deadline.urgency') },
          { key: 'action', label: '' },
        ]">
          <template #cell-type="{ row }">{{ f.label('DeadlineType', row.type) }}</template>
          <template #cell-dueDate="{ row }">{{ f.date(row.dueDate) }}</template>
          <template #cell-dueMileage="{ row }"><span class="num">{{ row.dueMileage ? f.km(row.dueMileage) : '-' }}</span></template>
          <template #cell-remaining="{ row }">
            <DeadlineRemaining :days-remaining="row.daysRemaining" :kilometres-remaining="row.kilometresRemaining" />
          </template>
          <template #cell-urgency="{ row }"><FleetBadge type="Urgency" :value="row.urgency" /></template>
          <template #cell-action="{ row }">
            <button v-if="row.type !== 'CONTRACT_END'" class="btn btn-sm" @click="openCompletion(row)">{{ $t('deadline.complete') }}</button>
          </template>
        </UiDataTable>
      </section>

      <section v-else-if="tab === 'history'" class="card">
        <UiDataTable :rows="v.assignments ?? []" :empty-text="$t('vehicle.noDriver')" :columns="[
          { key: 'driver', label: $t('vehicle.driver') },
          { key: 'kind', label: $t('vehicle.assignmentKind') },
          { key: 'period', label: $t('contract.duration') },
          { key: 'km', label: $t('driver.drivenKm'), align: 'right' },
        ]">
          <template #cell-driver="{ row }"><NuxtLink :to="`/conducteurs/${row.driver.id}`">{{ row.driver.fullName }}</NuxtLink></template>
          <template #cell-kind="{ row }">{{ f.label('AssignmentKind', row.kind) }}</template>
          <template #cell-period="{ row }">{{ f.date(row.startDate) }} → {{ row.endDate ? f.date(row.endDate) : '…' }}</template>
          <template #cell-km="{ row }"><span class="num">{{ f.km(row.drivenKilometres) }}</span></template>
        </UiDataTable>
      </section>

      <section v-else-if="tab === 'quotes'" class="card">
        <UiDataTable :rows="quotes" clickable :empty-text="$t('quote.vehicleEmpty')" :columns="[
          { key: 'reference', label: $t('quote.reference') },
          { key: 'garage', label: $t('quote.garage') },
          { key: 'submittedAt', label: $t('quote.submittedAt') },
          { key: 'amount', label: $t('quote.amount'), align: 'right' },
          { key: 'overcharge', label: $t('quote.overcharge'), align: 'right' },
          { key: 'status', label: $t('quote.status') },
        ]" @row-click="row => navigateTo(`/devis/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono">{{ row.reference }}</span></template>
          <template #cell-garage="{ row }">{{ row.garage.name }} <span class="muted xs">{{ row.garage.city }}</span></template>
          <template #cell-submittedAt="{ row }">{{ f.date(row.submittedAt) }}</template>
          <template #cell-amount="{ row }"><span class="num">{{ f.money(row.acceptedAmount ?? row.amount, 2) }}</span></template>
          <template #cell-overcharge="{ row }">
            <span class="num" :class="{ over: row.overcharge > 0 }">{{ row.overcharge > 0 ? f.money(row.overcharge, 2) : '-' }}</span>
          </template>
          <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>

      <VehicleTelemetryTab v-else-if="tab === 'telemetry'" :vehicle-id="v.id" />

      <VehicleDriverRecords v-else-if="tab === 'fuel' || tab === 'inspections'" :vehicle-id="v.id" :mode="tab === 'fuel' ? 'fuel' : 'inspections'" />

      <section v-else class="card">
        <UiDataTable :rows="v.incidents ?? []" clickable :empty-text="$t('incident.empty')" :columns="[
          { key: 'reference', label: $t('incident.reference') },
          { key: 'type', label: $t('incident.type') },
          { key: 'occurredAt', label: $t('incident.occurredAt') },
          { key: 'driver', label: $t('vehicle.driver') },
          { key: 'cost', label: $t('incident.estimatedCost'), align: 'right' },
          { key: 'status', label: $t('incident.status') },
        ]" @row-click="row => navigateTo(`/sinistres/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono">{{ row.reference }}</span></template>
          <template #cell-type="{ row }">{{ f.label('IncidentType', row.type) }}</template>
          <template #cell-occurredAt="{ row }">{{ f.date(row.occurredAt) }}</template>
          <template #cell-driver="{ row }">{{ row.driver?.fullName ?? '-' }}</template>
          <template #cell-cost="{ row }"><span class="num">{{ f.money(row.estimatedCost) }}</span></template>
          <template #cell-status="{ row }"><FleetBadge type="IncidentStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>

      <UiModal v-if="completing" v-model:open="completionOpen" :title="f.label('DeadlineType', completing.type)" width="420px">
        <div class="field">
          <label for="cm">{{ $t('deadline.mileageAtCompletion') }}</label>
          <input id="cm" v-model="completionMileage" type="number" min="0" class="input">
        </div>
        <template #footer>
          <button class="btn" @click="completing = null">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" :disabled="running" @click="complete">{{ $t('deadline.complete') }}</button>
        </template>
      </UiModal>
    </template>

    <div v-else-if="pending" class="stack">
      <span class="skeleton" style="height: 56px; width: 40%" />
      <span class="skeleton" style="height: 320px" />
    </div>
  </div>
</template>

<style scoped>
.over {
  color: var(--danger);
  font-weight: 600;
}

.meta {
  margin-top: 2px;
}

.tabs {
  margin-bottom: 18px;
}

.overview {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1000px) {
  .overview {
    grid-template-columns: minmax(0, 1fr);
  }
}

.strong {
  font-weight: 600;
}

.big {
  margin-bottom: 8px;
  font-size: var(--text-2xl);
  font-weight: 650;
  letter-spacing: -0.02em;
}

.projection {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: var(--radius);
  background: var(--surface-muted);
}

.gauge {
  position: relative;
  height: 8px;
  border-radius: 4px;
  background: var(--neutral-soft);
}

.gauge span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--primary);
}

.gauge span.over {
  background: var(--warning);
}

.limit {
  position: absolute;
  top: -3px;
  left: calc(100% / 1.4);
  width: 2px;
  height: 14px;
  background: var(--text-secondary);
}

.dl .badge {
  margin-right: 4px;
}
</style>
