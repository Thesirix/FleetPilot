<script setup lang="ts">
import { CircleAlert, Clock3, MailCheck, Paperclip, RotateCcw } from 'lucide-vue-next'
import { OM, OQ, type NotificationDetail, type NotificationStats, type NotificationSummary } from '~/graphql/operations'
import type { Page } from '~/graphql/fleet'

const { t, te } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('mails.title')} · FleetPilot` })

const state = useUrlState({ q: '', status: '', template: '', page: 0 })
const variables = computed(() => ({ filter: compact({ q: state.q, status: state.status, template: state.template }),
  page: state.page, size: 25 }))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ notifications: Page<NotificationSummary>;
  notificationStats: NotificationStats }>(OQ.notifications, variables,
{ cacheKey: () => `notifications:${JSON.stringify(variables.value)}` })
const stats = computed(() => data.value?.notificationStats)

const templates = ['quote-accepted', 'quote-rejected', 'quote-counter-offer', 'invoice-disputed', 'deadline-reminder',
  'vehicle-assigned', 'incident-declared', 'incident-received', 'contract-overrun', 'report-ready', 'vehicle-alert']
const templateLabel = (template: string) => (te(`mails.templates.${template}`) ? t(`mails.templates.${template}`) : template)
const TONES: Record<string, string> = { SENT: 'success', QUEUED: 'info', SENDING: 'info', FAILED: 'danger' }

const columns = computed(() => [
  { key: 'createdAt', label: t('mails.date') },
  { key: 'template', label: t('mails.template') },
  { key: 'recipient', label: t('mails.recipient') },
  { key: 'subject', label: t('mails.subject') },
  { key: 'status', label: t('mails.status') },
])

const selectedId = ref<string | null>(null)
const detail = shallowRef<NotificationDetail | null>(null)
watch(selectedId, async (id) => {
  detail.value = null
  if (!id) return
  const { data: result } = await graphql<{ notification: NotificationDetail }>(OQ.notification, { id })
  detail.value = result.notification
})

const { run, running } = useMutation()
async function retry() {
  if (!selectedId.value) return
  const done = await run(OM.retryNotification, { id: selectedId.value }, t('mails.retried'))
  if (done) {
    selectedId.value = null
    refresh()
  }
}
</script>

<template>
  <div>
    <UiPageHeader :title="$t('mails.title')" :subtitle="$t('mails.subtitle')">
      <template #actions>
        <a href="http://localhost:8025" target="_blank" rel="noopener" class="btn">{{ $t('mails.mailbox') }}</a>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="grid grid-3 kpis">
      <UiKpi :label="$t('mails.sent')" :value="stats ? f.number(stats.sent) : '-'" tone="success" :icon="MailCheck"
        :hint="$t('mails.last30')" />
      <UiKpi :label="$t('mails.queued')" :value="stats ? f.number(stats.queued) : '-'" :icon="Clock3" :hint="$t('mails.queuedHint')" />
      <UiKpi :label="$t('mails.failed')" :value="stats ? f.number(stats.failed) : '-'" :tone="stats?.failed ? 'danger' : 'default'"
        :icon="CircleAlert" :hint="$t('mails.failedHint')" />
    </div>

    <section class="card">
      <div class="filters">
        <UiSearch v-model="state.q" :placeholder="$t('mails.search')" class="grow" />
        <select v-model="state.template" class="select" :aria-label="$t('mails.template')">
          <option value="">{{ $t('mails.template') }} : {{ $t('app.all') }}</option>
          <option v-for="tpl in templates" :key="tpl" :value="tpl">{{ templateLabel(tpl) }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('mails.status')">
          <option value="">{{ $t('mails.status') }} : {{ $t('app.all') }}</option>
          <option v-for="s in ['SENT', 'QUEUED', 'FAILED']" :key="s" :value="s">{{ $t(`mails.statuses.${s}`) }}</option>
        </select>
      </div>
      <UiDataTable :columns="columns" :rows="data?.notifications.content ?? []" :loading="pending" clickable
        :empty-text="$t('mails.empty')" @row-click="row => selectedId = row.id">
        <template #cell-createdAt="{ row }"><span class="nowrap small">{{ f.dateTime(row.createdAt) }}</span></template>
        <template #cell-template="{ row }"><span class="small">{{ templateLabel(row.template) }}</span></template>
        <template #cell-recipient="{ row }">
          <div class="small">{{ row.recipientName ?? row.recipient }}</div>
          <div v-if="row.recipientName" class="xs muted">{{ row.recipient }}</div>
        </template>
        <template #cell-subject="{ row }">
          <span class="small">{{ row.subject ?? '-' }}</span>
          <Paperclip v-if="row.attachments" :size="13" class="clip" :aria-label="$t('mails.attachment')" />
        </template>
        <template #cell-status="{ row }">
          <span class="badge" :class="TONES[row.status]">{{ $t(`mails.statuses.${row.status}`) }}</span>
          <div v-if="row.attempts > 1" class="xs muted">{{ $t('mails.attempts', { n: row.attempts }) }}</div>
        </template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.notifications.totalPages"
        :total-elements="data.notifications.totalElements" />
    </section>

    <UiModal :open="!!selectedId" :title="detail?.summary.subject ?? $t('mails.preview')" width="760px"
      @update:open="selectedId = null">
      <div v-if="detail" class="stack-sm">
        <p class="small muted">
          {{ $t('mails.to') }} {{ detail.summary.recipient }} · {{ f.dateTime(detail.summary.sentAt ?? detail.summary.createdAt) }}
        </p>
        <p v-if="detail.summary.lastError" class="error small">{{ detail.summary.lastError }}</p>
        <iframe v-if="detail.bodyHtml" class="preview" :srcdoc="detail.bodyHtml" sandbox="" :title="$t('mails.preview')" />
        <p v-else class="muted small">{{ $t('mails.notRendered') }}</p>
      </div>
      <span v-else class="skeleton" style="height: 320px" />
      <template v-if="detail?.summary.status === 'FAILED'" #footer>
        <button class="btn btn-primary" :disabled="running" @click="retry"><RotateCcw /> {{ $t('mails.retry') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

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

.clip {
  margin-left: 6px;
  color: var(--text-muted);
  vertical-align: -2px;
}

.preview {
  width: 100%;
  height: 480px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #f5f6f8;
}

.error {
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
}
</style>
