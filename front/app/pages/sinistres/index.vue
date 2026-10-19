<script setup lang="ts">
import { Q, type Company, type Incident, type Page } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('incident.title')} · FleetPilot` })

const state = useUrlState({ open: 'true', type: '', status: '', companyId: '', sort: 'occurredAt,desc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const variables = computed(() => ({
  filter: compact({ type: state.type, status: state.status, companyId: state.companyId,
    open: state.open === '' ? undefined : state.open === 'true' }),
  page: state.page, size: 25, sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ incidents: Page<Incident> }>(Q.incidents, variables,
  { cacheKey: () => `incidents:${JSON.stringify(variables.value)}` })

const types = ['COLLISION', 'GLASS_BREAKAGE', 'MECHANICAL_FAILURE', 'VANDALISM', 'WEATHER', 'THEFT', 'FIRE']
const statuses = ['DECLARED', 'UNDER_REVIEW', 'IN_REPAIR', 'CLOSED', 'REJECTED']
const columns = computed(() => [
  { key: 'reference', label: t('incident.reference'), sort: 'reference' },
  { key: 'vehicle', label: t('vehicle.vehicle') },
  { key: 'type', label: t('incident.type'), sort: 'type' },
  { key: 'occurredAt', label: t('incident.occurredAt'), sort: 'occurredAt' },
  { key: 'driver', label: t('vehicle.driver') },
  { key: 'cost', label: t('incident.estimatedCost'), sort: 'estimatedCost', align: 'right' as const },
  { key: 'status', label: t('incident.status'), sort: 'status' },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('incident.title')" :subtitle="$t('incident.subtitle')" />
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <div class="filters">
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.open" class="select" :aria-label="$t('incident.status')">
          <option value="">{{ $t('app.all') }}</option>
          <option value="true">{{ $t('incident.openOnly') }}</option>
          <option value="false">{{ $t('incident.closedOnly') }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('incident.status')">
          <option value="">{{ $t('incident.status') }} : {{ $t('app.all') }}</option>
          <option v-for="s in statuses" :key="s" :value="s">{{ f.label('IncidentStatus', s) }}</option>
        </select>
        <select v-model="state.type" class="select" :aria-label="$t('incident.type')">
          <option value="">{{ $t('incident.type') }} : {{ $t('app.all') }}</option>
          <option v-for="k in types" :key="k" :value="k">{{ f.label('IncidentType', k) }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.incidents.content ?? []" :loading="pending"
        clickable :empty-text="$t('incident.empty')" @row-click="row => navigateTo(`/sinistres/${row.id}`)">
        <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
        <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
        <template #cell-type="{ row }">{{ f.label('IncidentType', row.type) }}</template>
        <template #cell-occurredAt="{ row }"><span class="nowrap">{{ f.date(row.occurredAt) }}</span></template>
        <template #cell-driver="{ row }"><span class="secondary">{{ row.driver?.fullName ?? '-' }}</span></template>
        <template #cell-cost="{ row }"><span class="num">{{ f.money(row.estimatedCost) }}</span></template>
        <template #cell-status="{ row }"><FleetBadge type="IncidentStatus" :value="row.status" /></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.incidents.totalPages"
        :total-elements="data.incidents.totalElements" />
    </section>
  </div>
</template>

<style scoped>
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
</style>
