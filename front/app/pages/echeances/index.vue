<script setup lang="ts">
import { M, Q, type Company, type Deadline, type Page } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('deadline.title')} · FleetPilot` })

const state = useUrlState({ urgency: 'OVERDUE', type: '', companyId: '', sort: 'dueDate,asc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const variables = computed(() => ({
  filter: compact({ urgency: state.urgency, type: state.type, companyId: state.companyId }),
  page: state.page, size: 25, sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ deadlines: Page<Deadline> }>(Q.deadlines, variables,
  { cacheKey: () => `deadlines:${JSON.stringify(variables.value)}` })

// Tab counters, refreshed after each completion
const counts = useGraphqlQuery<{ overdue: Page<Deadline>; soon: Page<Deadline>; upcoming: Page<Deadline> }>(`
  query($c: ID) {
    overdue: deadlines(filter: { companyId: $c, urgency: OVERDUE }, size: 1) { totalElements }
    soon: deadlines(filter: { companyId: $c, urgency: DUE_SOON }, size: 1) { totalElements }
    upcoming: deadlines(filter: { companyId: $c, urgency: UPCOMING }, size: 1) { totalElements }
  }`, () => ({ c: state.companyId || null }))
const tabs = computed(() => [
  { key: 'OVERDUE', label: f.label('Urgency', 'OVERDUE'), count: counts.data.value?.overdue.totalElements },
  { key: 'DUE_SOON', label: f.label('Urgency', 'DUE_SOON'), count: counts.data.value?.soon.totalElements },
  { key: 'UPCOMING', label: f.label('Urgency', 'UPCOMING'), count: counts.data.value?.upcoming.totalElements },
])
const urgencyTab = computed({ get: () => state.urgency, set: (v: string) => { state.urgency = v } })

const columns = computed(() => [
  { key: 'vehicle', label: t('vehicle.vehicle') },
  { key: 'type', label: t('deadline.type'), sort: 'type' },
  { key: 'dueDate', label: t('deadline.dueDate'), sort: 'dueDate' },
  { key: 'dueMileage', label: t('deadline.dueMileage'), sort: 'dueMileage', align: 'right' as const },
  { key: 'remaining', label: t('deadline.remaining') },
  { key: 'driver', label: t('vehicle.driver') },
  { key: 'action', label: '' },
])

const completing = ref<Deadline | null>(null)
const mileage = ref<number | null>(null)
const { run, running } = useMutation()
const dialogOpen = computed({ get: () => completing.value !== null, set: (o: boolean) => { if (!o) completing.value = null } })
async function complete() {
  const result = await run<{ completeDeadline: { next: { dueDate: string | null } | null } }>(M.completeDeadline,
    { id: completing.value!.id, mileage: mileage.value ? Number(mileage.value) : null })
  if (result) {
    const next = result.completeDeadline.next
    useToast().success(next?.dueDate ? t('deadline.nextScheduled', { date: f.date(next.dueDate) }) : t('deadline.completed'))
    completing.value = null
    refresh()
    counts.refresh()
  }
}
</script>

<template>
  <div>
    <UiPageHeader :title="$t('deadline.title')" :subtitle="$t('deadline.subtitle')">
      <template #actions>
        <select v-if="session.isStaff" v-model="state.companyId" class="select company" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.type" class="select company" :aria-label="$t('deadline.type')">
          <option value="">{{ $t('deadline.type') }} : {{ $t('app.all') }}</option>
          <option v-for="k in ['TECHNICAL_INSPECTION', 'SERVICE', 'TYRES', 'TACHOGRAPH', 'CONTRACT_END']" :key="k" :value="k">
            {{ f.label('DeadlineType', k) }}
          </option>
        </select>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <UiTabs v-model="urgencyTab" :tabs="tabs" class="tabs" />
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.deadlines.content ?? []" :loading="pending"
        :empty-text="$t('deadline.empty')">
        <template #cell-vehicle="{ row }">
          <NuxtLink :to="`/vehicules/${row.vehicle.id}`"><VehicleCell :vehicle="row.vehicle" /></NuxtLink>
        </template>
        <template #cell-type="{ row }">{{ f.label('DeadlineType', row.type) }}</template>
        <template #cell-dueDate="{ row }"><span class="nowrap">{{ f.date(row.dueDate) }}</span></template>
        <template #cell-dueMileage="{ row }"><span class="num">{{ row.dueMileage ? f.km(row.dueMileage) : '-' }}</span></template>
        <template #cell-remaining="{ row }">
          <DeadlineRemaining :days-remaining="row.daysRemaining" :kilometres-remaining="row.kilometresRemaining" />
        </template>
        <template #cell-driver="{ row }">
          <span class="secondary small">{{ row.vehicleSummary?.currentDriver?.fullName ?? '-' }}</span>
        </template>
        <template #cell-action="{ row }">
          <button v-if="row.type !== 'CONTRACT_END'" class="btn btn-sm" @click="completing = row; mileage = null">
            {{ $t('deadline.complete') }}
          </button>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.deadlines.totalPages"
        :total-elements="data.deadlines.totalElements" />
    </section>

    <UiModal v-if="completing" v-model:open="dialogOpen" :title="`${f.label('DeadlineType', completing.type)} · ${completing.vehicle.registration}`" width="440px">
      <div class="field">
        <label for="mileage">{{ $t('deadline.mileageAtCompletion') }}</label>
        <input id="mileage" v-model="mileage" type="number" min="0" class="input">
      </div>
      <template #footer>
        <button class="btn" @click="completing = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running" @click="complete">{{ $t('deadline.complete') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.company {
  width: auto;
  min-width: 200px;
}

.tabs {
  padding: 0 10px;
}
</style>
