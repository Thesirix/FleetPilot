<script setup lang="ts">
import { Building2, Car, Plus, Users } from 'lucide-vue-next'
import { AQ, type AdminCompany } from '~/graphql/admin'

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('companies.title')} · FleetPilot` })

const state = useUrlState({ q: '', status: 'active' })
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ adminCompanies: AdminCompany[] }>(AQ.companies, {},
  { cacheKey: 'admin-companies' })

const rows = computed(() => (data.value?.adminCompanies ?? [])
  .filter(c => state.status === '' || (state.status === 'active') === c.company.active)
  .filter((c) => {
    const q = state.q.trim().toLowerCase()
    return !q || `${c.company.name} ${c.company.legalName} ${c.company.siren} ${c.company.city}`.toLowerCase().includes(q)
  })
  .map(c => ({ ...c, id: c.company.id })))

const totals = computed(() => (data.value?.adminCompanies ?? []).filter(c => c.company.active).reduce((sum, c) => ({
  companies: sum.companies + 1, vehicles: sum.vehicles + c.activeVehicles, drivers: sum.drivers + c.drivers,
  sites: sum.sites + c.sites,
}), { companies: 0, vehicles: 0, drivers: 0, sites: 0 }))

const columns = computed(() => [
  { key: 'name', label: t('companies.name') },
  { key: 'segment', label: t('companies.segment') },
  { key: 'city', label: t('companies.city') },
  { key: 'vehicles', label: t('companies.vehicles'), align: 'right' as const },
  { key: 'drivers', label: t('companies.drivers'), align: 'right' as const },
  { key: 'sites', label: t('companies.sites'), align: 'right' as const },
  { key: 'contracts', label: t('companies.contracts'), align: 'right' as const },
  { key: 'status', label: t('companies.status') },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('companies.title')" :subtitle="$t('companies.subtitle')">
      <template #actions>
        <NuxtLink to="/administration/entreprises/nouvelle" class="btn btn-primary"><Plus /> {{ $t('companies.new') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('companies.activeCompanies')" :value="f.number(totals.companies)" :icon="Building2" />
      <UiKpi :label="$t('companies.vehiclesManaged')" :value="f.number(totals.vehicles)" :icon="Car" />
      <UiKpi :label="$t('companies.activeDrivers')" :value="f.number(totals.drivers)" :icon="Users" />
      <UiKpi :label="$t('companies.sitesTotal')" :value="f.number(totals.sites)" />
    </div>

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('companies.searchPlaceholder')" class="grow" />
        <select v-model="state.status" class="select" :aria-label="$t('companies.status')">
          <option value="active">{{ $t('companies.activeOnly') }}</option>
          <option value="inactive">{{ $t('companies.inactiveOnly') }}</option>
          <option value="">{{ $t('app.all') }}</option>
        </select>
      </div>
      <UiDataTable :columns="columns" :rows="rows" :loading="pending" clickable :empty-text="$t('companies.empty')"
        @row-click="row => navigateTo(`/administration/entreprises/${row.id}`)">
        <template #cell-name="{ row }">
          <div class="name">{{ row.company.name }}</div>
          <div class="xs muted">{{ row.company.legalName }} · <span class="mono">{{ row.company.siren }}</span></div>
        </template>
        <template #cell-segment="{ row }"><span class="badge plain">{{ f.label('CompanySegment', row.company.segment) }}</span></template>
        <template #cell-city="{ row }"><span class="secondary">{{ row.company.city }}</span></template>
        <template #cell-vehicles="{ row }"><span class="num">{{ f.number(row.activeVehicles) }}</span></template>
        <template #cell-drivers="{ row }"><span class="num">{{ f.number(row.drivers) }}</span></template>
        <template #cell-sites="{ row }"><span class="num">{{ f.number(row.sites) }}</span></template>
        <template #cell-contracts="{ row }"><span class="num">{{ f.number(row.activeContracts) }}</span></template>
        <template #cell-status="{ row }">
          <span class="badge" :class="row.company.active ? 'success' : ''">
            {{ row.company.active ? $t('companies.active') : $t('companies.inactive') }}
          </span>
        </template>
      </UiDataTable>
    </section>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
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
}

.name {
  font-weight: 540;
}
</style>
