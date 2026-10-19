<script setup lang="ts">
import { Q, type Company, type Contract, type Page } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('contract.title')} · FleetPilot` })

const LESSORS = ['Ayvens', 'Arval', 'Leasys', 'Alphabet', 'Free2Move Lease', 'Athlon']
const in90Days = new Date(Date.now() + 90 * 86_400_000).toISOString().slice(0, 10)
const state = useUrlState({ lessor: '', status: 'ACTIVE', ending: '', companyId: '', sort: 'endDate,asc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const variables = computed(() => ({
  filter: compact({ lessor: state.lessor, status: state.status, companyId: state.companyId,
    endingBefore: state.ending === '90' ? in90Days : '' }),
  page: state.page, size: 25, sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ contracts: Page<Contract> }>(Q.contracts, variables,
  { cacheKey: () => `contracts:${JSON.stringify(variables.value)}` })

const columns = computed(() => [
  { key: 'vehicle', label: t('vehicle.vehicle') },
  { key: 'contract', label: t('contract.number'), sort: 'contractNumber' },
  { key: 'endDate', label: t('contract.end'), sort: 'endDate' },
  { key: 'mileage', label: t('contract.projected'), align: 'right' as const },
  { key: 'risk', label: t('contract.overrun') },
  { key: 'rent', label: t('contract.rent'), sort: 'monthlyRent', align: 'right' as const },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('contract.title')" :subtitle="$t('contract.subtitle')" />
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <div class="filters">
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.lessor" class="select" :aria-label="$t('contract.lessor')">
          <option value="">{{ $t('contract.lessor') }} : {{ $t('app.all') }}</option>
          <option v-for="l in LESSORS" :key="l" :value="l">{{ l }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('vehicle.status')">
          <option value="">{{ $t('vehicle.status') }} : {{ $t('app.all') }}</option>
          <option value="ACTIVE">{{ f.label('ContractStatus', 'ACTIVE') }}</option>
          <option value="ENDED">{{ f.label('ContractStatus', 'ENDED') }}</option>
        </select>
        <select v-model="state.ending" class="select" :aria-label="$t('contract.end')">
          <option value="">{{ $t('contract.end') }} : {{ $t('app.all') }}</option>
          <option value="90">{{ $t('contract.end90') }}</option>
        </select>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.contracts.content ?? []" :loading="pending"
        clickable :empty-text="$t('contract.empty')" @row-click="row => navigateTo(`/contrats/${row.id}`)">
        <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
        <template #cell-contract="{ row }">
          <span class="stacked"><span>{{ row.lessor }}</span><span class="mono xs muted">{{ row.contractNumber }}</span></span>
        </template>
        <template #cell-endDate="{ row }">
          <span class="stacked">
            <span class="nowrap">{{ f.date(row.endDate) }}</span>
            <span v-if="row.daysRemaining !== null" class="xs muted">{{ $t('contract.endingIn', { count: row.daysRemaining }, row.daysRemaining) }}</span>
            <span v-else class="xs muted">{{ f.label('ContractStatus', row.status) }}</span>
          </span>
        </template>
        <template #cell-mileage="{ row }">
          <span v-if="row.projection" class="num small">{{ f.number(row.projection.projectedMileage) }} / {{ f.km(row.contractualMileage) }}</span>
          <span v-else class="num small">{{ f.km(row.contractualMileage) }}</span>
        </template>
        <template #cell-risk="{ row }">
          <span v-if="row.projection" class="badge" :class="row.projection.overrunRisk ? 'warning' : 'success'">
            {{ row.projection.projectedOverrunKm > 0 ? '+' : '' }}{{ f.km(row.projection.projectedOverrunKm) }}
          </span>
          <span v-else class="muted">-</span>
        </template>
        <template #cell-rent="{ row }"><span class="num">{{ f.money(row.monthlyRent, 2) }}</span></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.contracts.totalPages"
        :total-elements="data.contracts.totalElements" />
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

.stacked {
  display: flex;
  flex-direction: column;
}
</style>
