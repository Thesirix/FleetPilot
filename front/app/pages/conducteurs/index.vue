<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import { Q, type Company, type DriverSummary, type Page } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('driver.title')} · FleetPilot` })

const state = useUrlState({ q: '', active: 'true', assigned: '', companyId: '', sort: 'lastName,asc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const variables = computed(() => ({
  filter: compact({
    q: state.q, companyId: state.companyId,
    active: state.active === '' ? undefined : state.active === 'true',
    assigned: state.assigned === '' ? undefined : state.assigned === 'true',
  }),
  page: state.page,
  size: 25,
  sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ drivers: Page<DriverSummary> }>(Q.drivers, variables,
  { cacheKey: () => `drivers:${JSON.stringify(variables.value)}` })

const today = new Date().toISOString().slice(0, 10)
const columns = computed(() => [
  { key: 'name', label: t('driver.name'), sort: 'lastName' },
  { key: 'employeeNumber', label: t('driver.employeeNumber'), sort: 'employeeNumber' },
  { key: 'license', label: t('driver.license') },
  { key: 'vehicle', label: t('driver.vehicle') },
  { key: 'site', label: t('vehicle.site') },
  { key: 'active', label: t('vehicle.status'), sort: 'active' },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('driver.title')" :subtitle="$t('driver.subtitle')">
      <template #actions>
        <NuxtLink to="/conducteurs/nouveau" class="btn btn-primary"><Plus /> {{ $t('driver.new') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('driver.searchPlaceholder')" class="grow" />
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.active" class="select" :aria-label="$t('vehicle.status')">
          <option value="">{{ $t('vehicle.status') }} : {{ $t('app.all') }}</option>
          <option value="true">{{ $t('driver.active') }}</option>
          <option value="false">{{ $t('driver.inactive') }}</option>
        </select>
        <select v-model="state.assigned" class="select" :aria-label="$t('driver.vehicle')">
          <option value="">{{ $t('driver.vehicle') }} : {{ $t('app.all') }}</option>
          <option value="true">{{ $t('vehicle.withDriver') }}</option>
          <option value="false">{{ $t('driver.noVehicle') }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.drivers.content ?? []" :loading="pending" clickable
        :empty-text="$t('driver.empty')" @row-click="row => navigateTo(`/conducteurs/${row.id}`)">
        <template #cell-name="{ row }">
          <span class="person">
            <span class="name">{{ row.lastName }} {{ row.firstName }}</span>
            <span class="xs muted">{{ row.email }}</span>
          </span>
        </template>
        <template #cell-employeeNumber="{ row }"><span class="mono small">{{ row.employeeNumber }}</span></template>
        <template #cell-license="{ row }">
          <span class="row">
            <span v-for="c in row.licenseCategories" :key="c" class="badge plain">{{ c }}</span>
            <span v-if="row.licenseExpiresOn && row.licenseExpiresOn < today" class="badge danger">{{ $t('driver.licenseExpired') }}</span>
          </span>
        </template>
        <template #cell-vehicle="{ row }">
          <VehicleCell v-if="row.currentVehicle" :vehicle="row.currentVehicle" />
          <span v-else class="faint">{{ $t('driver.noVehicle') }}</span>
        </template>
        <template #cell-site="{ row }"><span class="secondary">{{ row.site?.name ?? '-' }}</span></template>
        <template #cell-active="{ row }">
          <span class="badge" :class="row.active ? 'success' : ''">{{ row.active ? $t('driver.active') : $t('driver.inactive') }}</span>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.drivers.totalPages"
        :total-elements="data.drivers.totalElements" />
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
  max-width: 220px;
}

.person {
  display: flex;
  flex-direction: column;
}

.name {
  font-weight: 550;
}

.faint {
  color: var(--text-faint);
}
</style>
