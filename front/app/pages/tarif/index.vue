<script setup lang="ts">
import { Info, Pencil } from 'lucide-vue-next'
import { AQ, type AnalysisRules } from '~/graphql/admin'
import { QM, QQ, type LabourRate, type Tariff } from '~/graphql/quotes'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('tariff.title')} · FleetPilot` })

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ tariff: Tariff }>(QQ.tariff, {}, { cacheKey: 'tariff' })
const rulesQuery = useGraphqlQuery<{ analysisRules: AnalysisRules }>(AQ.rules, {}, { cacheKey: 'analysis-rules' })
/** The rules in plain words, with the thresholds set in the administration. */
const rulesText = computed(() => {
  const r = rulesQuery.data.value?.analysisRules
  if (!r) return ''
  const pct = (share: number) => f.number(share * 100, share * 100 % 1 === 0 ? 0 : 1)
  return t('tariff.rulesDynamic', { part: pct(r.partTolerance), time: pct(r.timeTolerance), rate: pct(r.rateTolerance),
    supplies: pct(r.suppliesShare), suppliesMin: f.money(r.suppliesMinimum), auto: f.money(r.autoApprovalLimit) })
})
const tab = ref('parts')
const search = ref('')
const tabs = computed(() => [
  { key: 'parts', label: t('tariff.parts'), count: data.value?.tariff.parts.length },
  { key: 'operations', label: t('tariff.operations'), count: data.value?.tariff.operations.length },
  { key: 'rates', label: t('tariff.rates'), count: data.value?.tariff.rates.length },
])

const matches = (code: string, label: string) => {
  const term = search.value.trim().toLowerCase()
  return !term || code.toLowerCase().includes(term) || label.toLowerCase().includes(term)
}
const parts = computed(() => (data.value?.tariff.parts ?? []).filter(p => matches(p.code, p.label)))
const operations = computed(() => (data.value?.tariff.operations ?? []).filter(o => matches(o.code, o.label)))

const { run, running } = useMutation()
const editing = ref<LabourRate | null>(null)
const rate = reactive({ hourlyRate: 0, truckHourlyRate: 0 })
function edit(r: LabourRate) {
  editing.value = r
  rate.hourlyRate = r.hourlyRate
  rate.truckHourlyRate = r.truckHourlyRate
}
async function saveRate() {
  if (!editing.value) return
  const done = await run(QM.updateLabourRate, { region: editing.value.region, category: editing.value.labourCategory,
    hourlyRate: Number(rate.hourlyRate), truckHourlyRate: Number(rate.truckHourlyRate) }, t('tariff.rateSaved'))
  if (done) {
    editing.value = null
    refresh()
  }
}
</script>

<template>
  <div>
    <UiPageHeader :title="$t('tariff.title')" :subtitle="$t('tariff.subtitle')" />
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="rules card">
      <Info :size="18" />
      <div>
        <strong>{{ $t('tariff.rules') }}.</strong> {{ rulesText }}
        <NuxtLink v-if="session.isStaff" to="/administration/parametres" class="rules-link">{{ $t('tariff.changeRules') }}</NuxtLink>
      </div>
    </div>

    <section class="card">
      <div class="toolbar">
        <UiTabs v-model="tab" :tabs="tabs" />
        <UiSearch v-if="tab !== 'rates'" v-model="search" :placeholder="$t('app.search')" class="search" />
      </div>

      <div v-if="pending && !data" class="card-body"><span class="skeleton" style="height: 240px" /></div>

      <div v-else-if="tab === 'parts'" class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('tariff.code') }}</th><th>{{ $t('tariff.label') }}</th><th>{{ $t('tariff.family') }}</th>
              <th class="right">{{ $t('tariff.car') }}</th><th class="right">{{ $t('tariff.van') }}</th><th class="right">{{ $t('tariff.truck') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in parts" :key="p.code">
              <td class="mono small">{{ p.code }}</td>
              <td>{{ p.label }}</td>
              <td class="secondary small">{{ f.label('PartFamily', p.family) }}</td>
              <td class="right num">{{ f.money(p.priceCar, 2) }}</td>
              <td class="right num">{{ f.money(p.priceVan, 2) }}</td>
              <td class="right num">{{ f.money(p.priceTruck, 2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else-if="tab === 'operations'" class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('tariff.code') }}</th><th>{{ $t('tariff.label') }}</th><th>{{ $t('tariff.category') }}</th>
              <th class="right">{{ $t('tariff.car') }}</th><th class="right">{{ $t('tariff.van') }}</th><th class="right">{{ $t('tariff.truck') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="o in operations" :key="o.code">
              <td class="mono small">{{ o.code }}</td>
              <td>{{ o.label }}</td>
              <td class="secondary small">{{ f.label('LabourCategory', o.labourCategory) }}</td>
              <td class="right num">{{ $t('garage.hours', { value: f.number(o.hoursCar, 2) }) }}</td>
              <td class="right num">{{ $t('garage.hours', { value: f.number(o.hoursVan, 2) }) }}</td>
              <td class="right num">{{ $t('garage.hours', { value: f.number(o.hoursTruck, 2) }) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('tariff.region') }}</th><th>{{ $t('tariff.category') }}</th>
              <th class="right">{{ $t('tariff.rate') }}</th><th class="right">{{ $t('tariff.truckRate') }}</th>
              <th v-if="session.isAdmin" />
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in data?.tariff.rates ?? []" :key="r.region + r.labourCategory">
              <td>{{ r.regionLabel }}</td>
              <td class="secondary">{{ f.label('LabourCategory', r.labourCategory) }}</td>
              <td class="right num">{{ f.money(r.hourlyRate, 2) }}</td>
              <td class="right num">{{ f.money(r.truckHourlyRate, 2) }}</td>
              <td v-if="session.isAdmin" class="right">
                <button class="btn btn-ghost btn-sm" :aria-label="$t('tariff.editRate')" @click="edit(r)"><Pencil /></button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <UiModal :open="!!editing" :title="editing ? `${editing.regionLabel} · ${f.label('LabourCategory', editing.labourCategory)}` : ''"
      @update:open="editing = null">
      <form id="rate-form" class="grid grid-2" @submit.prevent="saveRate">
        <div class="field">
          <label for="rate-car">{{ $t('tariff.rate') }} (€)</label>
          <input id="rate-car" v-model="rate.hourlyRate" type="number" min="1" step="0.01" class="input" required>
        </div>
        <div class="field">
          <label for="rate-truck">{{ $t('tariff.truckRate') }} (€)</label>
          <input id="rate-truck" v-model="rate.truckHourlyRate" type="number" min="1" step="0.01" class="input" required>
        </div>
      </form>
      <template #footer>
        <button class="btn" @click="editing = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" type="submit" form="rate-form" :disabled="running">{{ $t('app.save') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.rules {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 16px;
  color: var(--text-secondary);
  font-size: var(--text-sm);
  line-height: 1.55;
}

.rules svg {
  flex: none;
  margin-top: 2px;
  color: var(--primary);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-right: 14px;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
}

.search {
  width: 260px;
  margin: 8px 0;
}

.rules-link {
  margin-left: 6px;
  white-space: nowrap;
}
</style>
