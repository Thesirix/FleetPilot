<script setup lang="ts">
import { AQ, type AuditEntry } from '~/graphql/admin'
import { Q, type Company, type Page } from '~/graphql/fleet'

const { t, te } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('audit.title')} · FleetPilot` })

const state = useUrlState({ actor: '', targetType: '', companyId: '', period: '30', page: 0 })
const since = computed(() => (state.period === '' ? undefined
  : new Date(Date.now() - Number(state.period) * 86_400_000).toISOString()))
const variables = computed(() => ({
  filter: compact({ actor: state.actor, targetType: state.targetType, companyId: state.companyId, since: since.value }),
  page: state.page, size: 30,
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ auditTrail: Page<AuditEntry> }>(AQ.audit, variables)
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const companyName = (id: string | null) => companies.value?.companies.find(c => c.id === id)?.name ?? ''

const TYPES = ['VEHICLE', 'DRIVER', 'CONTRACT', 'DEADLINE', 'INCIDENT', 'COMPANY', 'SITE', 'COST_CENTER', 'USER']

/** Where the journal line leads: the record itself when the portal has a page for it. */
function link(e: AuditEntry): string | null {
  if (!e.targetId) return null
  switch (e.targetType) {
    case 'VEHICLE': return `/vehicules/${e.targetId}`
    case 'DRIVER': return `/conducteurs/${e.targetId}`
    case 'CONTRACT': return `/contrats/${e.targetId}`
    case 'INCIDENT': return `/sinistres/${e.targetId}`
    case 'COMPANY': return session.isStaff ? `/administration/entreprises/${e.targetId}` : null
    case 'USER': return session.isStaff ? `/administration/utilisateurs/${e.targetId}` : null
    default: return e.companyId && session.isStaff ? `/administration/entreprises/${e.companyId}` : null
  }
}
const actionLabel = (action: string) => (te(`audit.actions.${action}`) ? t(`audit.actions.${action}`) : action)
const roleLabel = (role: string | null) => (role && te(`roles.${role}`) ? t(`roles.${role}`) : role === 'service' ? t('audit.service') : '')

/** Turns the codes of a journal line (statuses, types, dates) into the words of the portal. */
const ENUMS = ['VehicleStatus', 'DeadlineType', 'IncidentType', 'IncidentStatus', 'Responsibility', 'MileageSource',
  'AssignmentKind', 'ContractStatus']
function details(text: string | null): string {
  if (!text) return ''
  return text
    .replace(/\b(\d{4}-\d{2}-\d{2})\b/g, (_, d: string) => f.date(d))
    .replace(/\b([A-Z][A-Z_]{2,})\b/g, (code: string) => {
      const type = ENUMS.find(e => te(`enum.${e}.${code}`))
      if (type) return f.label(type, code)
      const role = code.toLowerCase().replace(/_/g, '-')
      return te(`roles.${role}`) ? t(`roles.${role}`) : code
    })
    .replace(/(\d+) km\b/g, (_, n: string) => f.km(Number(n)))
    .replace(/(\d+(?:\.\d+)?) EUR\b/g, (_, n: string) => f.money(Number(n)))
    .replace(/ > /g, ' → ')
    .replace(/^inactive$/, t('audit.inactive'))
    .replace(/(\d+) accounts disabled/, (_, n: string) => t('audit.accountsDisabled', { count: Number(n) }))
}

const columns = computed(() => [
  { key: 'when', label: t('audit.when') },
  { key: 'actor', label: t('audit.actor') },
  { key: 'action', label: t('audit.action') },
  { key: 'target', label: t('audit.target') },
  { key: 'details', label: t('audit.details') },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('audit.title')" :subtitle="$t('audit.subtitle')" />
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.actor" :placeholder="$t('audit.searchActor')" class="grow" />
        <select v-model="state.targetType" class="select" :aria-label="$t('audit.target')">
          <option value="">{{ $t('audit.target') }} : {{ $t('app.all') }}</option>
          <option v-for="type in TYPES" :key="type" :value="type">{{ $t(`audit.types.${type}`) }}</option>
        </select>
        <select v-if="session.isStaff" v-model="state.companyId" class="select" :aria-label="$t('users.company')">
          <option value="">{{ $t('users.company') }} : {{ $t('app.all') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.period" class="select" :aria-label="$t('audit.period')">
          <option value="1">{{ $t('audit.periods.day') }}</option>
          <option value="7">{{ $t('audit.periods.week') }}</option>
          <option value="30">{{ $t('audit.periods.month') }}</option>
          <option value="">{{ $t('audit.periods.all') }}</option>
        </select>
      </div>
      <UiDataTable :columns="columns" :rows="data?.auditTrail.content ?? []" :loading="pending" :empty-text="$t('audit.empty')">
        <template #cell-when="{ row }"><span class="secondary small nowrap">{{ f.dateTime(row.occurredAt) }}</span></template>
        <template #cell-actor="{ row }">
          <div class="strong">{{ row.actor }}</div>
          <div class="xs muted">{{ roleLabel(row.actorRole) }}</div>
        </template>
        <template #cell-action="{ row }"><span class="badge plain">{{ actionLabel(row.action) }}</span></template>
        <template #cell-target="{ row }">
          <NuxtLink v-if="link(row)" :to="link(row)!" class="mono-ish">{{ row.targetLabel ?? row.targetId }}</NuxtLink>
          <span v-else>{{ row.targetLabel ?? row.targetId }}</span>
          <div v-if="row.companyId && session.isStaff" class="xs muted">{{ companyName(row.companyId) }}</div>
        </template>
        <template #cell-details="{ row }"><span class="secondary small">{{ details(row.details) }}</span></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.auditTrail.totalPages"
        :total-elements="data.auditTrail.totalElements" />
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

.strong {
  font-weight: 540;
}

.nowrap {
  white-space: nowrap;
}
</style>
