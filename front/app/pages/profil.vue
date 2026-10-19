<script setup lang="ts">
import { ExternalLink, KeyRound, Languages, ShieldCheck } from 'lucide-vue-next'
import { AQ, type AuditEntry } from '~/graphql/admin'
import { Q, type Company, type Page } from '~/graphql/fleet'
import { graphql } from '~/utils/graphql'

const { t, te, locale, locales, setLocale } = useI18n()
const f = useFormat()
const session = useSessionStore()
const config = useRuntimeConfig().public
useHead({ title: () => `${t('profile.title')} · FleetPilot` })

const v = computed(() => session.viewer)
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const company = computed(() => companies.value?.companies.find(c => c.id === v.value?.companyId)?.name ?? null)
// The last actions of the user, for the roles that see the journal
const lastActions = ref<AuditEntry[]>([])
const loadingActions = ref(false)
onMounted(async () => {
  if (!session.isManager) return
  loadingActions.value = true
  try {
    const result = await graphql<{ auditTrail: Page<AuditEntry> }>(AQ.audit,
      { filter: { actor: session.displayName }, page: 0, size: 8 })
    lastActions.value = result.data.auditTrail.content
  } finally {
    loadingActions.value = false
  }
})
const accountUrl = computed(() => `${config.oidcAuthority}/account`)
const passwordUrl = computed(() => `${config.oidcAuthority}/account/account-security/signing-in`)
const actionLabel = (action: string) => (te(`audit.actions.${action}`) ? t(`audit.actions.${action}`) : action)
const scope = computed(() => {
  if (session.isStaff) return t('profile.scopeStaff')
  if (session.isDriver) return t('profile.scopeDriver')
  if (session.isGarage) return t('profile.scopeGarage')
  if (company.value) return t('profile.scopeCompany', { company: company.value })
  return '-'
})
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('profile.title')" :subtitle="$t('profile.subtitle')" />
    <div v-if="v" class="grid grid-2">
      <section class="card">
        <header class="card-header">
          <div class="who">
            <span class="avatar">{{ session.initials }}</span>
            <div>
              <h2>{{ session.displayName }}</h2>
              <p class="xs muted">{{ v.email }}</p>
            </div>
          </div>
        </header>
        <div class="card-body">
          <dl class="dl">
            <dt>{{ $t('users.login') }}</dt><dd class="mono">{{ v.username }}</dd>
            <dt>{{ $t('users.role') }}</dt>
            <dd><span v-for="r in v.roles.filter(r => te(`roles.${r}`))" :key="r" class="badge plain">{{ $t(`roles.${r}`) }}</span></dd>
            <dt>{{ $t('profile.scope') }}</dt><dd>{{ scope }}</dd>
          </dl>
        </div>
      </section>

      <section class="card">
        <header class="card-header"><h2><ShieldCheck :size="16" /> {{ $t('profile.security') }}</h2></header>
        <div class="card-body stack">
          <p class="small muted">{{ $t('profile.securityText') }}</p>
          <div class="row wrap">
            <a :href="passwordUrl" class="btn" target="_blank" rel="noopener"><KeyRound /> {{ $t('profile.changePassword') }}</a>
            <a :href="accountUrl" class="btn btn-ghost" target="_blank" rel="noopener"><ExternalLink /> {{ $t('profile.account') }}</a>
          </div>
          <hr class="divider">
          <p class="field-label"><Languages :size="14" /> {{ $t('app.language') }}</p>
          <div class="row wrap">
            <button v-for="l in locales" :key="l.code" class="btn btn-sm" :class="{ 'btn-primary': locale === l.code }"
              :aria-pressed="locale === l.code" @click="setLocale(l.code)">{{ l.name }}</button>
          </div>
        </div>
      </section>

      <section v-if="session.isManager" class="card wide">
        <header class="card-header">
          <h2>{{ $t('profile.lastActions') }}</h2>
          <NuxtLink :to="`/administration/journal?actor=${encodeURIComponent(session.displayName)}&period=`" class="small">
            {{ $t('profile.seeAll') }}
          </NuxtLink>
        </header>
        <div class="card-body flush">
          <UiDataTable :rows="lastActions" :loading="loadingActions"
            :empty-text="$t('profile.noAction')" :columns="[
              { key: 'when', label: $t('audit.when') },
              { key: 'action', label: $t('audit.action') },
              { key: 'target', label: $t('audit.target') },
            ]">
            <template #cell-when="{ row }"><span class="secondary small">{{ f.dateTime(row.occurredAt) }}</span></template>
            <template #cell-action="{ row }"><span class="badge plain">{{ actionLabel(row.action) }}</span></template>
            <template #cell-target="{ row }">{{ row.targetLabel ?? row.targetId }}</template>
          </UiDataTable>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 1000px;
}

.who {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary-text);
  font-weight: 650;
}

.wide {
  grid-column: 1 / -1;
}

h2 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
</style>
