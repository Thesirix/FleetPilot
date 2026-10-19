<script setup lang="ts">
import { AlertTriangle, FileSearch, PiggyBank, Scale } from 'lucide-vue-next'
import { Q, type Company, type Page } from '~/graphql/fleet'
import { QQ, type QuoteSummary, type SavingsSummary } from '~/graphql/quotes'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('quote.title')} · FleetPilot` })

const state = useUrlState({ q: '', status: '', open: '', withFindings: '', companyId: '', sort: 'submittedAt,desc', page: 0 })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data: savings } = useGraphqlQuery<{ savings: SavingsSummary }>(QQ.savings,
  () => compact({ companyId: state.companyId }), { cacheKey: () => `savings:${state.companyId}` })

const variables = computed(() => ({
  filter: compact({
    q: state.q, status: state.status, companyId: state.companyId,
    open: state.open === '' ? undefined : state.open === 'true',
    withFindings: state.withFindings === '' ? undefined : state.withFindings === 'true',
  }),
  page: state.page,
  size: 25,
  sort: [state.sort],
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ quotes: Page<QuoteSummary> }>(QQ.quotes, variables,
  { cacheKey: () => `quotes:${JSON.stringify(variables.value)}` })

const columns = computed(() => [
  { key: 'reference', label: t('quote.reference'), sort: 'reference' },
  { key: 'vehicle', label: t('quote.vehicle') },
  { key: 'garage', label: t('quote.garage') },
  { key: 'submittedAt', label: t('quote.submittedAt'), sort: 'submittedAt' },
  { key: 'amount', label: t('quote.amount'), sort: 'amount', align: 'right' as const },
  { key: 'overcharge', label: t('quote.overcharge'), sort: 'overcharge', align: 'right' as const },
  { key: 'findings', label: t('quote.findings'), sort: 'findingCount', align: 'right' as const },
  { key: 'status', label: t('quote.status'), sort: 'status' },
])
const statuses = ['RECEIVED', 'IN_REVIEW', 'TO_NEGOTIATE', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'INVOICED']
const s = computed(() => savings.value?.savings)

function show(status: string) {
  state.status = status
  state.open = ''
}
</script>

<template>
  <div>
    <UiPageHeader :title="$t('quote.title')" :subtitle="$t('quote.subtitle')" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('quote.kpi.toNegotiate')" :value="s ? f.number(s.toNegotiate) : '-'" tone="danger" :icon="Scale"
        :hint="s ? $t('quote.kpi.pending') + ' : ' + f.money(s.pendingOvercharge) : ''" to="/devis?status=TO_NEGOTIATE"
        @click="show('TO_NEGOTIATE')" />
      <UiKpi :label="$t('quote.kpi.toReview')" :value="s ? f.number(s.toReview) : '-'" tone="warning" :icon="FileSearch"
        to="/devis?status=IN_REVIEW" @click="show('IN_REVIEW')" />
      <UiKpi :label="$t('quote.kpi.received')" :value="s ? f.number(s.received) : '-'" :icon="AlertTriangle"
        to="/devis?status=RECEIVED" @click="show('RECEIVED')" />
      <UiKpi :label="$t('savings.total')" :value="s ? f.money(s.savings) : '-'" tone="success" :icon="PiggyBank"
        :hint="s ? $t('savings.rate', { rate: f.number(s.savingsRate, 1) }) : ''" to="/economies" />
    </div>

    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('quote.searchPlaceholder')" class="grow" />
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('quote.status')">
          <option value="">{{ $t('quote.status') }} : {{ $t('app.all') }}</option>
          <option v-for="st in statuses" :key="st" :value="st">{{ f.label('QuoteStatus', st) }}</option>
        </select>
        <select v-model="state.open" class="select" :aria-label="$t('quote.openOnly')">
          <option value="">{{ $t('quote.decision') }} : {{ $t('app.all') }}</option>
          <option value="true">{{ $t('quote.openOnly') }}</option>
          <option value="false">{{ $t('quote.decidedOnly') }}</option>
        </select>
        <label class="checkbox">
          <input type="checkbox" :checked="state.withFindings === 'true'"
            @change="state.withFindings = ($event.target as HTMLInputElement).checked ? 'true' : ''">
          {{ $t('quote.withFindings') }}
        </label>
      </div>
      <UiDataTable v-model:sort="state.sort" :columns="columns" :rows="data?.quotes.content ?? []" :loading="pending"
        clickable :empty-text="$t('quote.empty')" @row-click="row => navigateTo(`/devis/${row.id}`)">
        <template #cell-reference="{ row }">
          <span class="mono small">{{ row.reference }}</span>
          <div v-if="row.autoApproved" class="xs muted">{{ $t('quote.autoApproved') }}</div>
        </template>
        <template #cell-vehicle="{ row }">
          <VehicleCell v-if="row.vehicle" :vehicle="{ registration: row.vehicle.registration, make: row.vehicle.make ?? '',
            model: row.vehicle.model ?? '' }" />
          <span v-else class="mono small">{{ row.registration }}</span>
        </template>
        <template #cell-garage="{ row }">
          <div class="small">{{ row.garage.name }}</div>
          <div class="xs muted">{{ row.garage.city }}</div>
        </template>
        <template #cell-submittedAt="{ row }"><span class="nowrap">{{ f.date(row.submittedAt) }}</span></template>
        <template #cell-amount="{ row }"><span class="num">{{ f.money(row.amount, 2) }}</span></template>
        <template #cell-overcharge="{ row }">
          <span class="num" :class="{ over: row.overcharge > 0 }">{{ row.overcharge > 0 ? f.money(row.overcharge, 2) : '-' }}</span>
        </template>
        <template #cell-findings="{ row }">
          <span v-if="row.findingCount" class="badge danger plain num">{{ row.findingCount }}</span>
          <span v-else class="muted">-</span>
        </template>
        <template #cell-status="{ row }">
          <FleetBadge type="QuoteStatus" :value="row.status" />
          <div v-if="row.counterOffer" class="xs muted nowrap">{{ $t('quote.counterOffer') }} {{ f.money(row.counterOffer) }}</div>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.quotes.totalPages"
        :total-elements="data.quotes.totalElements" />
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
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
}

.filters .select {
  width: auto;
  max-width: 240px;
}

.over {
  color: var(--danger);
  font-weight: 600;
}

@media (max-width: 1100px) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
