<script setup lang="ts">
import { BadgeCheck, CircleSlash, Pencil } from 'lucide-vue-next'
import { QM, QQ, type Garage } from '~/graphql/quotes'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
const id = computed(() => route.params.id as string)
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ garage: Garage | null }>(QQ.garage,
  () => ({ id: id.value }), { cacheKey: () => `garage:${id.value}` })
const g = computed(() => data.value?.garage ?? null)
useHead({ title: () => (g.value ? `${g.value.name} · FleetPilot` : 'FleetPilot') })

const { run, running } = useMutation()
const approval = ref(false)
const rating = ref<number | null>(null)
function openApproval() {
  rating.value = g.value?.rating ?? null
  approval.value = true
}
async function save(approved: boolean) {
  const done = await run(QM.approveGarage, { id: id.value, approved, rating: rating.value ? Number(rating.value) : null },
    t('garage.approvalDone'))
  if (done) {
    approval.value = false
    refresh()
  }
}

const columns = computed(() => [
  { key: 'reference', label: t('quote.reference') },
  { key: 'vehicle', label: t('quote.vehicle') },
  { key: 'submittedAt', label: t('quote.submittedAt') },
  { key: 'amount', label: t('quote.amount'), align: 'right' as const },
  { key: 'overcharge', label: t('quote.overcharge'), align: 'right' as const },
  { key: 'status', label: t('quote.status') },
])
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !g" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="g">
      <UiPageHeader :title="g.name" :subtitle="`${f.label('GarageNetwork', g.network)} · ${g.city} · ${g.regionLabel}`" back="/garages">
        <template #badges>
          <span class="badge" :class="g.approved ? 'success' : ''">{{ g.approved ? $t('garage.approved') : $t('garage.notApproved') }}</span>
          <GarageRating :value="g.rating" />
        </template>
        <template v-if="session.isStaff" #actions>
          <NuxtLink :to="`/garages/${g.id}/modifier`" class="btn"><Pencil /> {{ $t('vehicle.edit') }}</NuxtLink>
          <button class="btn" :class="{ 'btn-primary': !g.approved }" @click="openApproval">
            <BadgeCheck v-if="!g.approved" /><CircleSlash v-else /> {{ g.approved ? $t('garage.withdraw') : $t('garage.approve') }}
          </button>
        </template>
      </UiPageHeader>

      <div class="grid grid-4 kpis">
        <UiKpi :label="$t('garage.quotes')" :value="f.number(g.stats.quotes)"
          :hint="`${$t('garage.acceptedQuotes')} ${g.stats.acceptedQuotes} · ${$t('garage.rejectedQuotes')} ${g.stats.rejectedQuotes}`" />
        <UiKpi :label="$t('garage.overchargeRate')" :value="`${f.number(g.stats.overchargeRate, 1)} %`"
          :tone="g.stats.overchargeRate >= 10 ? 'danger' : g.stats.overchargeRate >= 4 ? 'warning' : 'success'"
          :hint="`${$t('quote.overcharge')} : ${f.money(g.stats.overchargeDetected)}`" />
        <UiKpi :label="$t('garage.savings')" :value="f.money(g.stats.savings)" tone="success"
          :hint="`${$t('garage.amountQuoted')} : ${f.money(g.stats.amountQuoted)}`" />
        <UiKpi :label="$t('garage.decisionTime')"
          :value="g.stats.averageDecisionHours !== null ? $t('garage.hours', { value: f.number(g.stats.averageDecisionHours, 0) }) : '-'"
          :hint="`${$t('garage.disputed')} : ${g.stats.disputedInvoices}`" />
      </div>

      <div class="layout">
        <section class="card">
          <header class="card-header"><h2>{{ $t('garage.recentQuotes') }}</h2></header>
          <UiDataTable :columns="columns" :rows="g.quotes" clickable :empty-text="$t('quote.empty')"
            @row-click="row => navigateTo(`/devis/${row.id}`)">
            <template #cell-reference="{ row }"><span class="mono small">{{ row.reference }}</span></template>
            <template #cell-vehicle="{ row }"><span class="mono small">{{ row.registration }}</span></template>
            <template #cell-submittedAt="{ row }"><span class="nowrap">{{ f.date(row.submittedAt) }}</span></template>
            <template #cell-amount="{ row }"><span class="num">{{ f.money(row.amount, 2) }}</span></template>
            <template #cell-overcharge="{ row }">
              <span class="num" :class="{ over: row.overcharge > 0 }">{{ row.overcharge > 0 ? f.money(row.overcharge, 2) : '-' }}</span>
            </template>
            <template #cell-status="{ row }"><FleetBadge type="QuoteStatus" :value="row.status" /></template>
          </UiDataTable>
        </section>

        <section class="card">
          <header class="card-header"><h2>{{ $t('garage.identity') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('garage.address') }}</dt><dd>{{ g.address }}<br>{{ g.postalCode }} {{ g.city }}</dd>
              <dt>{{ $t('garage.phone') }}</dt><dd>{{ g.phone ?? '-' }}</dd>
              <dt>{{ $t('garage.email') }}</dt><dd><a :href="`mailto:${g.email}`">{{ g.email }}</a></dd>
              <dt>{{ $t('garage.siret') }}</dt><dd class="mono">{{ g.siret }}</dd>
              <dt>{{ $t('garage.hourlyRate') }}</dt><dd class="num">{{ f.money(g.hourlyRate, 2) }}</dd>
              <dt>{{ $t('garage.approved') }}</dt>
              <dd>{{ g.approvedSince ? $t('garage.approvedSince', { date: f.date(g.approvedSince) }) : $t('garage.notApproved') }}</dd>
            </dl>
          </div>
        </section>
      </div>

      <UiModal v-model:open="approval" :title="$t('garage.approvalTitle')">
        <div class="field">
          <label for="garage-rating">{{ $t('garage.ratingLabel') }}</label>
          <input id="garage-rating" v-model="rating" type="number" min="1" max="5" step="0.1" class="input">
        </div>
        <template #footer>
          <button class="btn" @click="approval = false">{{ $t('app.cancel') }}</button>
          <button v-if="g.approved" class="btn btn-danger" :disabled="running" @click="save(false)">{{ $t('garage.withdraw') }}</button>
          <button class="btn btn-primary" :disabled="running" @click="save(true)">{{ $t('garage.approve') }}</button>
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
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout,
  .kpis {
    grid-template-columns: minmax(0, 1fr);
  }
}

.over {
  color: var(--danger);
  font-weight: 600;
}
</style>
