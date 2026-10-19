<script setup lang="ts">
import { FilePlus2, MessageSquareReply, ReceiptText, Timer } from 'lucide-vue-next'
import { QQ, type Garage, type QuoteSummary } from '~/graphql/quotes'

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('garageSpace.title')} · FleetPilot` })

interface Space {
  myGarage: Garage | null
  open: { totalElements: number; content: QuoteSummary[] }
  toInvoice: { totalElements: number; content: QuoteSummary[] }
  recent: { content: QuoteSummary[] }
}
const { data, pending, error, stale, refresh } = useGraphqlQuery<Space>(QQ.garageSpace, {}, { cacheKey: 'garage-space' })
const garage = computed(() => data.value?.myGarage ?? null)
const toAnswer = computed(() => (data.value?.open.content ?? []).filter(q => q.counterOffer !== null))
const waiting = computed(() => (data.value?.open.content ?? []).filter(q => q.counterOffer === null))

const columns = computed(() => [
  { key: 'reference', label: t('quote.reference') },
  { key: 'vehicle', label: t('quote.vehicle') },
  { key: 'submittedAt', label: t('quote.submittedAt') },
  { key: 'amount', label: t('quote.amount'), align: 'right' as const },
  { key: 'status', label: t('quote.status') },
])
</script>

<template>
  <div>
    <UiPageHeader :title="garage?.name ?? $t('garageSpace.title')" :subtitle="$t('garageSpace.subtitle')">
      <template v-if="garage" #badges>
        <span class="badge" :class="garage.approved ? 'success' : ''">{{ garage.approved ? $t('garage.approved') : $t('garage.notApproved') }}</span>
      </template>
      <template #actions>
        <NuxtLink to="/espace-garage/nouveau" class="btn btn-primary"><FilePlus2 /> {{ $t('garageSpace.newQuote') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div v-if="garage" class="grid grid-4 kpis">
      <UiKpi :label="$t('garageSpace.toAnswer')" :value="toAnswer.length" :tone="toAnswer.length ? 'warning' : 'default'" :icon="MessageSquareReply" />
      <UiKpi :label="$t('garageSpace.pending')" :value="waiting.length" :icon="Timer" />
      <UiKpi :label="$t('garageSpace.toInvoice')" :value="data?.toInvoice.totalElements ?? 0" :icon="ReceiptText" />
      <UiKpi :label="$t('garage.overchargeRate')" :value="`${f.number(garage.stats.overchargeRate, 1)} %`"
        :tone="garage.stats.overchargeRate >= 10 ? 'danger' : garage.stats.overchargeRate >= 4 ? 'warning' : 'success'"
        :hint="`${$t('garage.quotes')} : ${garage.stats.quotes} · ${$t('garageSpace.yourRate', { rate: f.money(garage.hourlyRate, 2) })}`" />
    </div>

    <div class="stack">
      <section v-if="toAnswer.length" class="card highlight">
        <header class="card-header"><h2>{{ $t('garageSpace.toAnswer') }}</h2></header>
        <UiDataTable :columns="columns" :rows="toAnswer" clickable @row-click="row => navigateTo(`/devis/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
          <template #cell-vehicle="{ row }"><span class="mono small">{{ row.registration }}</span></template>
          <template #cell-submittedAt="{ row }">{{ f.date(row.submittedAt) }}</template>
          <template #cell-amount="{ row }">
            <span class="num">{{ f.money(row.amount, 2) }}</span>
            <div class="xs counter">{{ $t('quote.counterOffer') }} {{ f.money(row.counterOffer, 2) }}</div>
          </template>
          <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('garageSpace.pending') }}</h2></header>
        <UiDataTable :columns="columns" :rows="waiting" :loading="pending" clickable :empty-text="$t('garageSpace.nothing')"
          @row-click="row => navigateTo(`/devis/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
          <template #cell-vehicle="{ row }"><span class="mono small">{{ row.registration }}</span></template>
          <template #cell-submittedAt="{ row }">{{ f.date(row.submittedAt) }}</template>
          <template #cell-amount="{ row }"><span class="num">{{ f.money(row.amount, 2) }}</span></template>
          <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('garageSpace.toInvoice') }}</h2></header>
        <UiDataTable :columns="columns" :rows="data?.toInvoice.content ?? []" :loading="pending" clickable
          :empty-text="$t('garageSpace.nothing')" @row-click="row => navigateTo(`/devis/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
          <template #cell-vehicle="{ row }"><span class="mono small">{{ row.registration }}</span></template>
          <template #cell-submittedAt="{ row }">{{ f.date(row.submittedAt) }}</template>
          <template #cell-amount="{ row }"><span class="num">{{ f.money(row.acceptedAmount, 2) }}</span></template>
          <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('garageSpace.recent') }}</h2></header>
        <UiDataTable :columns="columns" :rows="data?.recent.content ?? []" :loading="pending" clickable
          :empty-text="$t('garageSpace.nothing')" @row-click="row => navigateTo(`/devis/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
          <template #cell-vehicle="{ row }"><span class="mono small">{{ row.registration }}</span></template>
          <template #cell-submittedAt="{ row }">{{ f.date(row.submittedAt) }}</template>
          <template #cell-amount="{ row }"><span class="num">{{ f.money(row.acceptedAmount ?? row.amount, 2) }}</span></template>
          <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>
    </div>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.highlight {
  border-color: #fedf89;
}

.counter {
  color: var(--warning);
  font-weight: 560;
}

@media (max-width: 1100px) {
  .kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
