<script setup lang="ts">
import { FileUp, Plus } from 'lucide-vue-next'
import { Q, type Company, type Page, type VehicleSummary } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('vehicle.title')} · FleetPilot` })

const state = useUrlState({ q: '', status: '', category: '', energy: '', assigned: '', companyId: '', sort: 'registration,asc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })

const variables = computed(() => ({
  filter: compact({
    q: state.q, status: state.status, category: state.category, energy: state.energy, companyId: state.companyId,
    assigned: state.assigned === '' ? undefined : state.assigned === 'true',
  }),
  page: state.page,
  size: 25,
  sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ vehicles: Page<VehicleSummary> }>(Q.vehicles, variables,
  { cacheKey: () => `vehicles:${JSON.stringify(variables.value)}` })

const columns = computed(() => [
  { key: 'registration', label: t('vehicle.vehicle'), sort: 'registration' },
  { key: 'category', label: t('vehicle.category'), sort: 'category' },
  { key: 'energy', label: t('vehicle.energy'), sort: 'energy' },
  { key: 'critAir', label: t('vehicle.critAir'), sort: 'critAir' },
  { key: 'mileage', label: t('vehicle.mileage'), sort: 'mileage', align: 'right' as const },
  { key: 'driver', label: t('vehicle.driver') },
  { key: 'site', label: t('vehicle.site') },
  { key: 'status', label: t('vehicle.status'), sort: 'status' },
])
const statuses = ['ACTIVE', 'IN_REPAIR', 'IMMOBILIZED', 'AWAITING_DELIVERY', 'DISPOSED']
const categories = ['CAR', 'VAN', 'TRUCK', 'MACHINE']
const energies = ['ELECTRIC', 'HYBRID', 'PLUG_IN_HYBRID', 'PETROL', 'DIESEL', 'LPG', 'CNG', 'HYDROGEN']
</script>

<template>
  <div>
    <UiPageHeader :title="$t('vehicle.title')" :subtitle="$t('vehicle.subtitle')">
      <template #actions>
        <NuxtLink to="/vehicules/import" class="btn"><FileUp /> {{ $t('vehicle.import') }}</NuxtLink>
        <NuxtLink to="/vehicules/nouveau" class="btn btn-primary"><Plus /> {{ $t('vehicle.new') }}</NuxtLink>
      </template>
    </UiPageHeader>

    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('vehicle.searchPlaceholder')" class="grow" />
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('vehicle.status')">
          <option value="">{{ $t('vehicle.status') }} : {{ $t('app.all') }}</option>
          <option v-for="s in statuses" :key="s" :value="s">{{ f.label('VehicleStatus', s) }}</option>
        </select>
        <select v-model="state.category" class="select" :aria-label="$t('vehicle.category')">
          <option value="">{{ $t('vehicle.category') }} : {{ $t('app.all') }}</option>
          <option v-for="c in categories" :key="c" :value="c">{{ f.label('VehicleCategoryLong', c) }}</option>
        </select>
        <select v-model="state.energy" class="select" :aria-label="$t('vehicle.energy')">
          <option value="">{{ $t('vehicle.energy') }} : {{ $t('app.all') }}</option>
          <option v-for="e in energies" :key="e" :value="e">{{ f.label('Energy', e) }}</option>
        </select>
        <select v-model="state.assigned" class="select" :aria-label="$t('vehicle.driver')">
          <option value="">{{ $t('vehicle.driver') }} : {{ $t('app.all') }}</option>
          <option value="true">{{ $t('vehicle.withDriver') }}</option>
          <option value="false">{{ $t('vehicle.noDriver') }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.vehicles.content ?? []" :loading="pending"
        clickable :empty-text="$t('vehicle.empty')" @row-click="row => navigateTo(`/vehicules/${row.id}`)">
        <template #cell-registration="{ row }"><VehicleCell :vehicle="row" /></template>
        <template #cell-category="{ row }">{{ f.label('VehicleCategory', row.category) }}</template>
        <template #cell-energy="{ row }"><FleetBadge type="Energy" :value="row.energy" /></template>
        <template #cell-critAir="{ row }"><CritAirBadge :value="row.critAir" /></template>
        <template #cell-mileage="{ row }"><span class="num">{{ f.km(row.mileage) }}</span></template>
        <template #cell-driver="{ row }">
          <span v-if="row.currentDriver">{{ row.currentDriver.fullName }}</span>
          <span v-else class="faint">{{ $t('vehicle.noDriver') }}</span>
        </template>
        <template #cell-site="{ row }"><span class="secondary">{{ row.site?.name ?? '-' }}</span></template>
        <template #cell-status="{ row }"><FleetBadge type="VehicleStatus" :value="row.status" /></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.vehicles.totalPages"
        :total-elements="data.vehicles.totalElements" />
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

.faint {
  color: var(--text-faint);
}
</style>
