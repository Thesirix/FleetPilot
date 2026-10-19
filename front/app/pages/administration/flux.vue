<script setup lang="ts">
import { Download, FileCode2, FileSpreadsheet, Fuel, RotateCcw, ShieldCheck, Upload } from 'lucide-vue-next'
import type { Component } from 'vue'
import type { Page } from '~/graphql/fleet'
import {
  EXCHANGE_STATUSES, FLOWS, IM, IQ, downloadExchange, dropFile, type Exchange, type ExchangeDetail, type Flow,
  type IntegrationFlow,
} from '~/graphql/integration'

const { t } = useI18n()
const f = useFormat()
const toast = useToast()
useHead({ title: () => `${t('integration.title')} · FleetPilot` })

const state = useUrlState({ flow: '', status: '', page: 0 })
const variables = computed(() => ({ flow: state.flow || null, status: state.status || null, page: state.page, size: 25 }))
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ integrationFlows: IntegrationFlow[]; exchanges: Page<Exchange> }>(
  IQ.console, variables, { cacheKey: () => `integration:${JSON.stringify(variables.value)}` })
// the routes keep working in the background: the journal is refreshed every ten seconds
const timer = setInterval(refresh, 10_000)
onBeforeUnmount(() => clearInterval(timer))

const ICONS: Record<Flow, Component> = { GARAGE_QUOTES: FileCode2, LESSOR_INVOICES: FileSpreadsheet, FUEL_CARDS: Fuel,
  INSURER_CLAIMS: ShieldCheck }
const TONES: Record<string, string> = { PROCESSED: 'success', PARTIAL: 'warning', REJECTED: 'danger', FAILED: 'danger',
  DUPLICATE: 'info', RUNNING: 'info' }

function filterFlow(flow: Flow) {
  state.flow = state.flow === flow ? '' : flow
  state.page = 0
}
const started = (flow: IntegrationFlow) => flow.routes.filter(r => r.status === 'Started').length

const columns = computed(() => [
  { key: 'receivedAt', label: t('integration.receivedAt') },
  { key: 'flow', label: t('integration.flow') },
  { key: 'source', label: t('integration.source') },
  { key: 'status', label: t('integration.status') },
  { key: 'records', label: t('integration.records'), align: 'right' as const },
  { key: 'summary', label: t('integration.summary') },
])

// Detail of an exchange
const selectedId = ref<string | null>(null)
const detail = shallowRef<ExchangeDetail | null>(null)
watch(selectedId, async (id) => {
  detail.value = null
  if (!id) return
  const { data: result } = await graphql<{ exchange: ExchangeDetail }>(IQ.exchange, { id })
  detail.value = result.exchange
})
const detailOpen = computed({ get: () => selectedId.value !== null, set: (o: boolean) => { if (!o) selectedId.value = null } })
const replayable = computed(() => {
  const s = detail.value?.summary
  return !!s && s.flow !== 'INSURER_CLAIMS' && ['FAILED', 'REJECTED', 'PARTIAL'].includes(s.status)
})
const preview = computed(() => {
  const payload = detail.value?.payload ?? ''
  return payload.length > 6000 ? `${payload.slice(0, 6000)}\n…` : payload
})
const { run, running } = useMutation()
async function replay() {
  const done = await run(IM.replay, { id: selectedId.value }, t('integration.replayed'))
  if (done) {
    selectedId.value = null
    setTimeout(refresh, 2500)
  }
}

// Dropping a file by hand
const dropOpen = ref(false)
const dropFlow = ref<Flow>('GARAGE_QUOTES')
const dropped = ref<File | null>(null)
const sending = ref(false)
async function send() {
  if (!dropped.value) return
  sending.value = true
  try {
    const result = await dropFile(dropFlow.value, dropped.value)
    toast.success(t('integration.dropped', { name: result.fileName }))
    dropOpen.value = false
    dropped.value = null
    state.flow = dropFlow.value
    setTimeout(refresh, 3000)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    sending.value = false
  }
}
const accept = computed(() => (dropFlow.value === 'GARAGE_QUOTES' ? '.xml' : '.csv'))
</script>

<template>
  <div>
    <UiPageHeader :title="$t('integration.title')" :subtitle="$t('integration.subtitle')">
      <template #actions>
        <button class="btn btn-primary" data-cy="drop-file" @click="dropOpen = true"><Upload /> {{ $t('integration.drop') }}</button>
      </template>
    </UiPageHeader>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div class="flows" data-cy="flows">
      <button v-for="flow in data?.integrationFlows ?? []" :key="flow.flow" class="flow card"
        :class="{ active: state.flow === flow.flow }" @click="filterFlow(flow.flow)">
        <div class="row-between">
          <span class="flow-icon"><component :is="ICONS[flow.flow]" :size="18" /></span>
          <span class="route" :class="{ on: started(flow) === flow.routes.length }">
            <span class="dot" />{{ started(flow) === flow.routes.length ? $t('integration.routesOn')
              : $t('integration.routesOff', { n: flow.routes.length - started(flow) }) }}
          </span>
        </div>
        <strong class="flow-title">{{ $t(`enum.Flow.${flow.flow}`) }}</strong>
        <span class="xs muted">{{ $t(`integration.flowHint.${flow.flow}`) }}</span>
        <div class="flow-stats small">
          <span><b class="num">{{ f.number(flow.activity.exchanges) }}</b> {{ $t('integration.exchanges', flow.activity.exchanges) }}</span>
          <span v-if="flow.activity.partial + flow.activity.rejected" class="warn">
            <b class="num">{{ flow.activity.partial + flow.activity.rejected }}</b> {{ $t('integration.toLook') }}</span>
          <span v-if="flow.activity.failed" class="fail"><b class="num">{{ flow.activity.failed }}</b> {{ $t('integration.failed') }}</span>
        </div>
        <span class="xs muted">{{ flow.activity.lastReceivedAt ? $t('integration.last', { when: f.dateTime(flow.activity.lastReceivedAt) }) : $t('integration.never') }}</span>
      </button>
    </div>

    <section class="card">
      <div class="filters">
        <select v-model="state.flow" class="select" :aria-label="$t('integration.flow')" @change="state.page = 0">
          <option value="">{{ $t('integration.flow') }} : {{ $t('app.all') }}</option>
          <option v-for="flow in FLOWS" :key="flow" :value="flow">{{ $t(`enum.Flow.${flow}`) }}</option>
        </select>
        <select v-model="state.status" class="select" :aria-label="$t('integration.status')" @change="state.page = 0">
          <option value="">{{ $t('integration.status') }} : {{ $t('app.all') }}</option>
          <option v-for="s in EXCHANGE_STATUSES" :key="s" :value="s">{{ $t(`enum.ExchangeStatus.${s}`) }}</option>
        </select>
      </div>
      <UiDataTable :columns="columns" :rows="data?.exchanges.content ?? []" :loading="pending && !data" clickable
        :empty-text="$t('integration.empty')" @row-click="row => selectedId = row.id">
        <template #cell-receivedAt="{ row }"><span class="small nowrap">{{ f.dateTime(row.receivedAt) }}</span></template>
        <template #cell-flow="{ row }">
          <span class="small nowrap flow-cell"><component :is="ICONS[row.flow as Flow]" :size="14" /> {{ $t(`enum.Flow.${row.flow}`) }}</span>
        </template>
        <template #cell-source="{ row }">
          <div class="small mono source">{{ row.source }}</div>
          <div v-if="row.partner" class="xs muted">{{ row.partner }}</div>
        </template>
        <template #cell-status="{ row }">
          <span class="badge" :class="TONES[row.status]">{{ $t(`enum.ExchangeStatus.${row.status}`) }}</span>
          <div v-if="row.replayOf && row.status !== 'DUPLICATE'" class="xs muted">{{ $t('integration.replay') }}</div>
        </template>
        <template #cell-records="{ row }">
          <span v-if="row.records" class="num small">{{ f.number(row.accepted) }} / {{ f.number(row.records) }}</span>
          <span v-else class="muted small">-</span>
        </template>
        <template #cell-summary="{ row }"><span class="small summary">{{ row.summary ?? '-' }}</span></template>
      </UiDataTable>
      <UiPagination v-if="data" v-model:page="state.page" :total-pages="data.exchanges.totalPages"
        :total-elements="data.exchanges.totalElements" />
    </section>

    <UiModal v-model:open="detailOpen" :title="detail?.summary.source ?? $t('integration.exchange')" width="820px">
      <div v-if="detail" class="stack-sm" data-cy="exchange-detail">
        <div class="row-between">
          <span class="badge" :class="TONES[detail.summary.status]">{{ $t(`enum.ExchangeStatus.${detail.summary.status}`) }}</span>
          <span class="xs muted">{{ $t(`enum.Flow.${detail.summary.flow}`) }} · {{ f.dateTime(detail.summary.receivedAt) }}</span>
        </div>
        <p class="lead">{{ detail.summary.summary }}</p>
        <p v-if="detail.summary.records" class="small muted">
          {{ $t('integration.counts', { accepted: f.number(detail.summary.accepted), records: f.number(detail.summary.records) }) }}
        </p>
        <div v-if="detail.issues.length" class="issues">
          <p class="small strong">{{ $t('integration.issues', { n: detail.issues.length }) }}</p>
          <table class="table compact">
            <thead><tr><th>{{ $t('integration.line') }}</th><th>{{ $t('integration.reference') }}</th><th>{{ $t('integration.problem') }}</th></tr></thead>
            <tbody>
              <tr v-for="(issue, i) in detail.issues" :key="i">
                <td class="num small">{{ issue.line ?? '-' }}</td>
                <td class="small mono">{{ issue.reference ?? '-' }}</td>
                <td class="small"><span class="sev" :class="issue.severity === 'ERROR' ? 'danger' : 'warning'" />{{ issue.message }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <details v-if="detail.payload" class="payload">
          <summary class="small">{{ $t('integration.content') }}</summary>
          <pre>{{ preview }}</pre>
        </details>
      </div>
      <span v-else class="skeleton" style="height: 280px" />
      <template v-if="detail" #footer>
        <button v-if="detail.payload && detail.summary.flow !== 'INSURER_CLAIMS'" class="btn" @click="downloadExchange(detail.summary)">
          <Download /> {{ $t('integration.download') }}
        </button>
        <button v-if="replayable" class="btn btn-primary" :disabled="running" data-cy="replay" @click="replay">
          <RotateCcw /> {{ $t('integration.replayAction') }}
        </button>
      </template>
    </UiModal>

    <UiModal v-model:open="dropOpen" :title="$t('integration.drop')" width="520px">
      <div class="stack-sm">
        <p class="small muted">{{ $t('integration.dropHint') }}</p>
        <div class="field">
          <label for="flow">{{ $t('integration.flow') }}</label>
          <select id="flow" v-model="dropFlow" class="select">
            <option v-for="flow in FLOWS.filter(x => x !== 'INSURER_CLAIMS')" :key="flow" :value="flow">{{ $t(`enum.Flow.${flow}`) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="file">{{ $t('integration.file') }}</label>
          <input id="file" type="file" class="input" :accept="accept" data-cy="drop-input"
            @change="e => dropped = (e.target as HTMLInputElement).files?.[0] ?? null">
        </div>
      </div>
      <template #footer>
        <button class="btn" @click="dropOpen = false">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="!dropped || sending" data-cy="drop-send" @click="send"><Upload /> {{ $t('integration.send') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.flows {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.flow {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.flow:hover { border-color: var(--border-strong); }

.flow.active {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--ring);
}

.flow-icon {
  display: inline-flex;
  padding: 7px;
  border-radius: var(--radius-sm);
  background: var(--primary-soft);
  color: var(--primary);
}

.flow-title {
  margin-top: 4px;
  font-size: var(--text-md);
  font-weight: 600;
}

.flow-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin-top: 4px;
  color: var(--text-secondary);
}

.flow-stats .warn { color: var(--warning); }
.flow-stats .fail { color: var(--danger); }

.route {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.route .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-faint);
}

.route.on { color: var(--success); }
.route.on .dot { background: #12b76a; }

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

.flow-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
}

.mono { font-family: var(--font-mono); }

.source {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  display: -webkit-box;
  max-width: 420px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.lead {
  font-size: var(--text-md);
  font-weight: 500;
}

.strong { font-weight: 600; }

.issues {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}

.issues > p {
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-muted);
}

.table.compact td,
.table.compact th {
  padding: 6px 12px;
}

.sev {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 50%;
  vertical-align: 1px;
}

.sev.danger { background: var(--danger); }
.sev.warning { background: #f79009; }

.payload summary {
  cursor: pointer;
  color: var(--text-secondary);
}

.payload pre {
  max-height: 300px;
  margin-top: 8px;
  padding: 12px;
  overflow: auto;
  border-radius: var(--radius);
  background: #0f172a;
  color: #e2e8f0;
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.5;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

@media (max-width: 1200px) {
  .flows { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
