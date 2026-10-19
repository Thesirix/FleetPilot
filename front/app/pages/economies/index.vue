<script setup lang="ts">
import { BadgeCheck, FileWarning, PiggyBank, Scale } from 'lucide-vue-next'
import { Q, type Company } from '~/graphql/fleet'
import { QQ, type SavingsSummary } from '~/graphql/quotes'

const { t, locale } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('savings.title')} · FleetPilot` })

const state = useUrlState({ companyId: '' })
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ savings: SavingsSummary }>(QQ.savings,
  () => compact({ companyId: state.companyId }), { cacheKey: () => `savings:${state.companyId}` })
const s = computed(() => data.value?.savings ?? null)

const months = computed(() => (s.value?.months ?? []).map(m => ({
  x: new Intl.DateTimeFormat(locale.value === 'fr' ? 'fr-FR' : 'en-GB', { month: 'short', year: '2-digit' })
    .format(new Date(`${m.month}-15T12:00:00`)),
  y: Number(m.savings),
})))
const maxFinding = computed(() => Math.max(1, ...(s.value?.findings ?? []).map(x => x.overcharge)))
</script>

<template>
  <div>
    <UiPageHeader :title="$t('savings.title')" :subtitle="$t('savings.subtitle')">
      <template #actions>
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('vehicle.company')">
          <option value="">{{ $t('dashboard.allCompanies') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </template>
    </UiPageHeader>

    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-4 kpis">
      <UiKpi :label="$t('savings.total')" :value="s ? f.money(s.savings) : '-'" tone="success" :icon="PiggyBank"
        :hint="s ? $t('savings.rate', { rate: f.number(s.savingsRate, 1) }) : ''" />
      <UiKpi :label="$t('savings.pending')" :value="s ? f.money(s.pendingOvercharge) : '-'" tone="warning" :icon="Scale"
        :hint="s ? $t('savings.pendingHint', { count: s.openQuotes }) : ''" to="/devis?open=true" />
      <UiKpi :label="$t('savings.autoApproval')" :value="s ? `${f.number(s.autoApprovalRate, 1)} %` : '-'" :icon="BadgeCheck"
        :hint="$t('savings.autoApprovalHint')" />
      <UiKpi :label="$t('savings.disputed')" :value="s ? f.number(s.disputedInvoices) : '-'" tone="danger" :icon="FileWarning"
        :hint="s ? `${$t('savings.quoted')} : ${f.money(s.amountQuoted)}` : ''" />
    </div>

    <div class="layout">
      <section class="card">
        <header class="card-header"><h2>{{ $t('savings.byMonth') }}</h2></header>
        <div class="card-body">
          <ChartBars v-if="s" :bars="months" :format="v => f.money(v)" height="400px" />
          <span v-else-if="pending" class="skeleton" style="height: 400px" />
        </div>
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('savings.findings') }}</h2></header>
        <div class="card-body">
          <ul class="findings">
            <li v-for="item in s?.findings ?? []" :key="item.finding">
              <div class="row-between">
                <FleetBadge type="Finding" :value="item.finding" />
                <span class="num strong">{{ f.money(item.overcharge) }}</span>
              </div>
              <div class="bar"><span :style="{ width: `${(item.overcharge / maxFinding) * 100}%` }" /></div>
              <span class="xs muted">{{ $t('savings.findingLines', item.lines) }}</span>
            </li>
          </ul>
        </div>
      </section>
    </div>

    <section class="card">
      <header class="card-header"><h2>{{ $t('savings.garagesToWatch') }}</h2></header>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>{{ $t('garage.name') }}</th>
              <th>{{ $t('garage.city') }}</th>
              <th class="right">{{ $t('savings.quotes') }}</th>
              <th class="right">{{ $t('savings.overchargeRate') }}</th>
              <th class="right">{{ $t('garage.savings') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="g in s?.garagesToWatch ?? []" :key="g.garageId" class="clickable" @click="navigateTo(`/garages/${g.garageId}`)">
              <td class="strong">{{ g.name }}</td>
              <td class="secondary">{{ g.city }}</td>
              <td class="right num">{{ g.quotes }}</td>
              <td class="right num"><span class="badge plain" :class="g.overchargeRate >= 10 ? 'danger' : 'warning'">{{ f.number(g.overchargeRate, 1) }} %</span></td>
              <td class="right num">{{ f.money(g.savings) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

@media (max-width: 1100px) {
  .layout,
  .kpis {
    grid-template-columns: minmax(0, 1fr);
  }
}

.findings {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bar {
  height: 6px;
  margin: 6px 0 4px;
  border-radius: 3px;
  background: var(--surface-hover);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: var(--danger);
  opacity: 0.75;
}

.strong {
  font-weight: 600;
}
</style>
