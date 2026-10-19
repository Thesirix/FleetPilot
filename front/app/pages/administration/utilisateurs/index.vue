<script setup lang="ts">
import { Plus } from 'lucide-vue-next'
import { ALL_ROLES, AQ, type User } from '~/graphql/admin'
import { Q, type Company, type Page } from '~/graphql/fleet'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('users.title')} · FleetPilot` })

const state = useUrlState({ q: '', role: '', companyId: '', enabled: '', page: 0 })
const variables = computed(() => ({
  filter: compact({ q: state.q, role: state.role, companyId: state.companyId,
    enabled: state.enabled === '' ? undefined : state.enabled === 'true' }),
  page: state.page, size: 25,
}))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ users: Page<User> }>(AQ.users, variables)
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })

const columns = computed(() => [
  { key: 'name', label: t('users.name') },
  { key: 'role', label: t('users.role') },
  { key: 'attachment', label: t('users.attachment') },
  { key: 'source', label: t('users.source') },
  { key: 'createdAt', label: t('users.createdAt') },
  { key: 'enabled', label: t('users.status') },
])
</script>

<template>
  <div>
    <UiPageHeader :title="$t('users.title')" :subtitle="$t('users.subtitle')">
      <template v-if="session.isAdmin" #actions>
        <NuxtLink to="/administration/utilisateurs/nouveau" class="btn btn-primary"><Plus /> {{ $t('users.new') }}</NuxtLink>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <p v-if="!session.isAdmin" class="banner small">{{ $t('users.readOnly') }}</p>

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('users.searchPlaceholder')" class="grow" />
        <select v-model="state.role" class="select" :aria-label="$t('users.role')">
          <option value="">{{ $t('users.role') }} : {{ $t('app.all') }}</option>
          <option v-for="r in ALL_ROLES" :key="r" :value="r">{{ $t(`roles.${r}`) }}</option>
        </select>
        <select v-model="state.companyId" class="select" :aria-label="$t('users.company')">
          <option value="">{{ $t('users.company') }} : {{ $t('app.all') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <select v-model="state.enabled" class="select" :aria-label="$t('users.status')">
          <option value="">{{ $t('users.status') }} : {{ $t('app.all') }}</option>
          <option value="true">{{ $t('users.enabled') }}</option>
          <option value="false">{{ $t('users.disabled') }}</option>
        </select>
      </div>
      <UiDataTable :columns="columns" :rows="data?.users.content ?? []" :loading="pending" clickable
        :empty-text="$t('users.empty')" @row-click="row => navigateTo(`/administration/utilisateurs/${row.id}`)">
        <template #cell-name="{ row }">
          <div class="name">{{ row.fullName }}</div>
          <div class="xs muted">{{ row.email ?? row.username }}</div>
        </template>
        <template #cell-role="{ row }">
          <span class="badge" :class="row.role?.startsWith('platform-') || row.role === 'fleet-manager' ? 'info' : 'plain'">{{ row.role ? $t(`roles.${row.role}`) : '-' }}</span>
        </template>
        <template #cell-attachment="{ row }">
          <span class="secondary small">
            <template v-if="row.role === 'driver'">{{ row.driverName ?? '-' }} · {{ row.companyName }}</template>
            <template v-else-if="row.companyName">{{ row.companyName }}</template>
            <template v-else-if="row.garage">{{ row.garage.name }}</template>
            <template v-else-if="row.lessor">{{ row.lessor }}</template>
            <template v-else-if="row.role?.startsWith('platform-') || row.role === 'fleet-manager'">FleetPilot</template>
            <template v-else>-</template>
          </span>
        </template>
        <template #cell-source="{ row }">
          <span class="xs muted">{{ row.federated ? $t('users.fromLdap') : $t('users.fromFleetPilot') }}</span>
        </template>
        <template #cell-createdAt="{ row }"><span class="secondary small">{{ f.date(row.createdAt) }}</span></template>
        <template #cell-enabled="{ row }">
          <span class="badge" :class="row.enabled ? 'success' : 'danger'">{{ row.enabled ? $t('users.enabled') : $t('users.disabled') }}</span>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.users.totalPages"
        :total-elements="data.users.totalElements" />
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

.banner {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: var(--info-soft);
  color: var(--text-secondary);
}
</style>
