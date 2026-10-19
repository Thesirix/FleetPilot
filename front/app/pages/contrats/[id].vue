<script setup lang="ts">
import { CalendarClock, Gauge, Receipt, Wallet } from 'lucide-vue-next'
import { M, Q, type Assignment, type Contract, type MileageReading, type SiteRef } from '~/graphql/fleet'

interface ContractVehicle {
  id: string
  company: { id: string; name: string }
  registration: string
  make: string
  model: string
  energy: string
  status: string
  mileage: number
  mileageReadAt: string
  site: SiteRef | null
  currentAssignment: Assignment | null
  mileageHistory: MileageReading[]
}

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const id = computed(() => route.params.id as string)

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ contract: Contract | null }>(Q.contract,
  () => ({ id: id.value }), { cacheKey: () => `contract:${id.value}` })
const c = computed(() => data.value?.contract ?? null)
const vehicleQuery = useGraphqlQuery<{ vehicle: ContractVehicle | null }>(Q.contractVehicle,
  () => ({ id: c.value?.vehicle.id ?? '00000000-0000-0000-0000-000000000000' }))
const vehicle = computed(() => vehicleQuery.data.value?.vehicle ?? null)
useHead({ title: () => (c.value ? `${c.value.contractNumber} · FleetPilot` : 'FleetPilot') })

const DAY = 86_400_000
/** Share of the contract duration already elapsed, in percent. */
const elapsed = computed(() => {
  if (!c.value) return 0
  const start = Date.parse(c.value.startDate)
  const end = Date.parse(c.value.endDate)
  const now = c.value.endedOn ? Date.parse(c.value.endedOn) : Date.now()
  return Math.max(0, Math.min(100, ((now - start) / (end - start)) * 100))
})
const driven = computed(() => (c.value && vehicle.value ? Math.max(0, vehicle.value.mileage - c.value.startMileage) : 0))
const allowance = computed(() => (c.value ? c.value.contractualMileage - c.value.startMileage : 0))
const usedShare = computed(() => (allowance.value > 0 ? Math.min(140, (driven.value / allowance.value) * 100) : 0))
const projectedShare = computed(() => {
  const p = c.value?.projection
  if (!p || allowance.value <= 0) return 0
  return Math.min(140, ((p.projectedMileage - c.value!.startMileage) / allowance.value) * 100)
})
const monthsElapsed = computed(() => (c.value ? Math.round((c.value.durationMonths * elapsed.value) / 100) : 0))
const rentPaid = computed(() => (c.value ? c.value.monthlyRent * monthsElapsed.value : 0))

// The odometer since the start of the contract, one point a day
const points = computed(() => {
  if (!c.value || !vehicle.value) return []
  const start = c.value.startDate
  return dailyReadings(vehicle.value.mileageHistory.filter(r => r.readAt.slice(0, 10) >= start))
    .map(r => ({ x: f.date(r.readAt, 'short'), y: r.mileage }))
})

// ---------------------------------------------------------------- end of contract
const ending = ref(false)
const { run, running } = useMutation()
async function endContract() {
  const done = await run(M.endContract, { id: id.value }, t('contract.ended'))
  if (done) {
    ending.value = false
    await refresh()
  }
}
const daysText = computed(() => {
  const days = c.value?.daysRemaining
  if (days == null) return '-'
  return days >= 0 ? t('contract.daysLeft', { count: days }) : t('contract.daysOver', { count: -days })
})
const monthsLeft = computed(() => (c.value ? Math.max(0, Math.ceil((Date.parse(c.value.endDate) - Date.now()) / (30.44 * DAY))) : 0))
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !c" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />

    <template v-if="c">
      <UiPageHeader :title="c.contractNumber" :subtitle="`${c.lessor} · ${c.vehicle.registration} ${c.vehicle.make} ${c.vehicle.model}`"
        back="/contrats">
        <template #badges>
          <FleetBadge type="ContractStatus" :value="c.status" />
          <span v-if="c.projection && c.status === 'ACTIVE'" class="badge" :class="c.projection.overrunRisk ? 'warning' : 'success'">
            {{ c.projection.overrunRisk ? $t('contract.atRisk') : $t('contract.onTrack') }}
          </span>
        </template>
        <template #actions>
          <button v-if="c.status === 'ACTIVE'" class="btn" @click="ending = true">{{ $t('contract.endContract') }}</button>
        </template>
      </UiPageHeader>

      <div class="grid grid-4 kpis">
        <UiKpi :label="$t('contract.rent')" :value="f.money(c.monthlyRent, 2)" :hint="$t('contract.perMonthExclVat')" :icon="Wallet" />
        <UiKpi :label="$t('contract.totalRent')" :value="f.money(c.totalRent)"
          :hint="$t('contract.rentPaidHint', { paid: f.money(rentPaid), months: monthsElapsed })" :icon="Receipt" />
        <UiKpi :label="$t('contract.projected')" :value="c.projection ? f.km(c.projection.projectedMileage) : '-'"
          :tone="c.projection?.overrunRisk ? 'warning' : 'default'"
          :hint="$t('contract.ofContractual', { km: f.km(c.contractualMileage) })" :icon="Gauge" />
        <UiKpi :label="$t('contract.end')" :value="f.date(c.endedOn ?? c.endDate)" :hint="c.status === 'ACTIVE' ? daysText : $t('contract.ended')"
          :icon="CalendarClock" />
      </div>

      <div class="layout">
        <div class="stack">
          <section class="card">
            <header class="card-header"><h2>{{ $t('contract.progress') }}</h2></header>
            <div class="card-body stack">
              <div>
                <div class="row-between small">
                  <span class="muted">{{ $t('contract.timeElapsed') }}</span>
                  <span class="num">{{ $t('contract.monthsOf', { done: monthsElapsed, total: c.durationMonths }) }}</span>
                </div>
                <div class="gauge"><span :style="{ width: `${elapsed}%` }" /></div>
              </div>
              <div>
                <div class="row-between small">
                  <span class="muted">{{ $t('contract.kmDriven') }}</span>
                  <span class="num"><strong>{{ f.km(driven) }}</strong> / {{ f.km(allowance) }}</span>
                </div>
                <div class="gauge wide">
                  <span :style="{ width: `${usedShare / 1.4}%` }" />
                  <i v-if="c.projection" class="projected" :class="{ over: c.projection.overrunRisk }"
                    :style="{ left: `${projectedShare / 1.4}%` }" :title="$t('contract.projected')" />
                  <i class="limit" />
                </div>
                <p v-if="c.projection" class="xs muted">
                  {{ $t('contract.dailyAverage') }} : {{ f.km(c.projection.dailyAverageKm) }} ·
                  {{ $t('contract.overrun') }} : {{ c.projection.projectedOverrunKm > 0 ? '+' : '' }}{{ f.km(c.projection.projectedOverrunKm) }}
                  <template v-if="c.status === 'ACTIVE' && monthsLeft > 0">
                    · {{ $t('contract.remainingPerMonth', { km: f.km(Math.max(0, (c.contractualMileage - (vehicle?.mileage ?? c.startMileage)) / monthsLeft)) }) }}
                  </template>
                </p>
              </div>
            </div>
          </section>

          <section class="card">
            <header class="card-header">
              <h2>{{ $t('contract.odometer') }}</h2>
              <span class="xs muted">{{ $t('contract.sinceStart', { date: f.date(c.startDate) }) }}</span>
            </header>
            <div class="card-body">
              <ChartLine v-if="points.length > 1" :points="points" unit="km" height="220px" />
              <p v-else class="muted small">{{ $t('contract.noReading') }}</p>
            </div>
          </section>
        </div>

        <div class="stack">
          <section class="card">
            <header class="card-header"><h2>{{ $t('contract.terms') }}</h2></header>
            <div class="card-body">
              <dl class="dl">
                <dt>{{ $t('contract.lessor') }}</dt><dd>{{ c.lessor }}</dd>
                <dt>{{ $t('contract.number') }}</dt><dd class="mono">{{ c.contractNumber }}</dd>
                <dt>{{ $t('contract.duration') }}</dt>
                <dd>{{ f.date(c.startDate) }} → {{ f.date(c.endDate) }} ({{ $t('contract.months', { count: c.durationMonths }) }})</dd>
                <dt>{{ $t('contract.contractualMileage') }}</dt><dd>{{ f.km(c.contractualMileage) }}</dd>
                <dt>{{ $t('contract.startMileage') }}</dt><dd>{{ f.km(c.startMileage) }}</dd>
                <dt>{{ $t('contract.services') }}</dt>
                <dd>
                  <span v-if="c.maintenanceIncluded" class="badge plain">{{ $t('contract.maintenance') }}</span>
                  <span v-if="c.tyresIncluded" class="badge plain">{{ $t('contract.tyres') }}</span>
                  <span v-if="c.replacementVehicle" class="badge plain">{{ $t('contract.replacement') }}</span>
                  <span v-if="!c.maintenanceIncluded && !c.tyresIncluded && !c.replacementVehicle" class="muted">-</span>
                </dd>
                <template v-if="c.endedOn"><dt>{{ $t('contract.endedOn') }}</dt><dd>{{ f.date(c.endedOn) }}</dd></template>
              </dl>
            </div>
          </section>

          <section class="card">
            <header class="card-header"><h2>{{ $t('vehicle.vehicle') }}</h2></header>
            <div class="card-body">
              <dl v-if="vehicle" class="dl">
                <dt>{{ $t('vehicle.vehicle') }}</dt>
                <dd><NuxtLink :to="`/vehicules/${vehicle.id}`"><VehicleCell :vehicle="vehicle" /></NuxtLink></dd>
                <dt>{{ $t('vehicle.company') }}</dt><dd>{{ vehicle.company.name }}</dd>
                <dt>{{ $t('vehicle.site') }}</dt><dd>{{ vehicle.site?.name ?? '-' }}</dd>
                <dt>{{ $t('vehicle.driver') }}</dt>
                <dd>
                  <NuxtLink v-if="vehicle.currentAssignment" :to="`/conducteurs/${vehicle.currentAssignment.driver.id}`">
                    {{ vehicle.currentAssignment.driver.fullName }}
                  </NuxtLink>
                  <span v-else class="muted">{{ $t('vehicle.noDriver') }}</span>
                </dd>
                <dt>{{ $t('vehicle.mileage') }}</dt>
                <dd>{{ f.km(vehicle.mileage) }} <span class="xs muted">({{ f.date(vehicle.mileageReadAt) }})</span></dd>
              </dl>
            </div>
          </section>
        </div>
      </div>

      <UiModal v-model:open="ending" :title="$t('contract.endContract')" width="460px">
        <p class="small">{{ $t('contract.endConfirm', { number: c.contractNumber, registration: c.vehicle.registration }) }}</p>
        <template #footer>
          <button class="btn" @click="ending = false">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" :disabled="running" @click="endContract">{{ $t('contract.endContract') }}</button>
        </template>
      </UiModal>
    </template>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.gauge {
  position: relative;
  height: 8px;
  margin: 8px 0 6px;
  border-radius: 4px;
  background: var(--neutral-soft);
}

.gauge span {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--primary);
}

.gauge .limit {
  position: absolute;
  top: -3px;
  left: calc(100% / 1.4);
  width: 2px;
  height: 14px;
  background: var(--text-secondary);
}

.gauge .projected {
  position: absolute;
  top: -3px;
  width: 14px;
  height: 14px;
  margin-left: -7px;
  border: 2px solid var(--surface);
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 1px var(--border);
}

.gauge .projected.over {
  background: var(--warning);
}
</style>
