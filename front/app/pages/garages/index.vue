<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import type { Page } from '~/graphql/fleet'
import { QQ, type GarageSummary } from '~/graphql/quotes'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('garage.title')} · FleetPilot` })

const state = useUrlState({ q: '', region: '', network: '', approved: '', sort: 'name,asc', page: 0 })
const variables = computed(() => ({
  filter: compact({ q: state.q, region: state.region, network: state.network,
    approved: state.approved === '' ? undefined : state.approved === 'true' }),
  page: state.page, size: 25, sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ garages: Page<GarageSummary> }>(QQ.garages, variables,
  { cacheKey: () => `garages:${JSON.stringify(variables.value)}` })

const columns = computed(() => [
  { key: 'name', label: t('garage.name'), sort: 'name' },
  { key: 'network', label: t('garage.network'), sort: 'network' },
  { key: 'city', label: t('garage.city'), sort: 'city' },
  { key: 'region', label: t('garage.region'), sort: 'region' },
  { key: 'hourlyRate', label: t('garage.hourlyRate'), sort: 'hourlyRate', align: 'right' as const },
  { key: 'rating', label: t('garage.rating'), sort: 'rating', align: 'right' as const },
  { key: 'approved', label: t('garage.approved'), sort: 'approved' },
])
const regions = ['PAC', 'IDF', 'ARA', 'HDF', 'NAQ', 'OCC', 'PDL', 'GES']
const networks = ['DEALER', 'INDEPENDENT', 'FAST_FIT', 'BODYWORK', 'TRUCK']
</script>

<template>
  <div>
    <UiPageHeader :title="$t('garage.title')" :subtitle="$t('garage.subtitle')">
      <template v-if="session.isStaff" #actions>
        <NuxtLink to="/garages/nouveau" class="btn btn-primary"><Plus /> {{ $t('garage.newTitle') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('garage.searchPlaceholder')" class="grow" />
        <select v-model="state.region" class="select" :aria-label="$t('garage.region')">
          <option value="">{{ $t('garage.region') }} : {{ $t('app.all') }}</option>
          <option v-for="r in regions" :key="r" :value="r">{{ f.label('Region', r) }}</option>
        </select>
        <select v-model="state.network" class="select" :aria-label="$t('garage.network')">
          <option value="">{{ $t('garage.network') }} : {{ $t('app.all') }}</option>
          <option v-for="n in networks" :key="n" :value="n">{{ f.label('GarageNetwork', n) }}</option>
        </select>
        <select v-model="state.approved" class="select" :aria-label="$t('garage.approved')">
          <option value="">{{ $t('app.all') }}</option>
          <option value="true">{{ $t('garage.approvedOnly') }}</option>
          <option value="false">{{ $t('garage.notApprovedOnly') }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.garages.content ?? []" :loading="pending"
        clickable :empty-text="$t('garage.empty')" @row-click="row => navigateTo(`/garages/${row.id}`)">
        <template #cell-name="{ row }">
          <div class="name">{{ row.name }}</div>
          <div class="xs muted mono">{{ row.code }}</div>
        </template>
        <template #cell-network="{ row }"><FleetBadge type="GarageNetwork" :value="row.network" /></template>
        <template #cell-city="{ row }"><span class="secondary">{{ row.city }}</span> <span class="xs muted">{{ row.postalCode }}</span></template>
        <template #cell-region="{ row }"><span class="secondary small">{{ f.label('Region', row.region) }}</span></template>
        <template #cell-hourlyRate="{ row }"><span class="num">{{ f.money(row.hourlyRate, 2) }}</span></template>
        <template #cell-rating="{ row }"><GarageRating :value="row.rating" /></template>
        <template #cell-approved="{ row }">
          <span class="badge" :class="row.approved ? 'success' : ''">{{ row.approved ? $t('garage.approved') : $t('garage.notApproved') }}</span>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.garages.totalPages"
        :total-elements="data.garages.totalElements" />
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
  max-width: 260px;
}

.name {
  font-weight: 540;
}
</style>
