<script setup lang="ts">
import { BadgeCheck, CircleSlash, FileCheck2, FilePen, HandCoins, MessageSquareReply, ReceiptText, Search, Wrench } from 'lucide-vue-next'
import { QM, QQ, type Quote } from '~/graphql/quotes'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
const id = computed(() => route.params.id as string)
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ quote: Quote | null }>(QQ.quote,
  () => ({ id: id.value }), { cacheKey: () => `quote:${id.value}` })
const q = computed(() => data.value?.quote ?? null)
useHead({ title: () => (q.value ? `${q.value.reference} · FleetPilot` : 'FleetPilot') })

const can = (action: string) => q.value?.actions.includes(action) ?? false
const back = computed(() => (session.isGarage && !session.isManager ? '/espace-garage' : '/devis'))
const steps = ['RECEIVED', 'IN_REVIEW', 'ACCEPTED', 'COMPLETED', 'INVOICED']
const stepIndex = computed(() => {
  const status = q.value?.status ?? ''
  if (status === 'TO_NEGOTIATE') return 1
  if (status === 'REJECTED') return -1
  return steps.indexOf(status)
})

const { run, running, fieldErrors } = useMutation()
const modal = ref<'' | 'counter' | 'accept' | 'reject' | 'identify' | 'answer' | 'invoice'>('')
const form = reactive({ amount: null as number | null, message: '', comment: '', reason: '', registration: '', number: '' })

function open(kind: typeof modal.value) {
  Object.assign(form, { amount: null, message: '', comment: '', reason: '', registration: '', number: '' })
  if (kind === 'counter' && q.value?.referenceAmount) form.amount = Math.floor(q.value.referenceAmount / 5) * 5
  if (kind === 'invoice' && q.value?.acceptedAmount) form.amount = q.value.acceptedAmount
  if (kind === 'identify' && q.value) form.registration = q.value.registration
  modal.value = kind
}

async function submit(mutation: string, variables: Record<string, unknown>, success: string) {
  const done = await run(mutation, { id: id.value, ...variables }, success)
  if (done) {
    modal.value = ''
    refresh()
  }
}

const counter = () => submit(QM.counterOffer, { amount: Number(form.amount), message: form.message || null }, t('quote.done.counterOffer'))
const accept = () => submit(QM.acceptQuote, { comment: form.comment || null }, t('quote.done.accept'))
const reject = () => submit(QM.rejectQuote, { reason: form.reason }, t('quote.done.reject'))
const identify = () => submit(QM.identifyQuoteVehicle, { registration: form.registration }, t('quote.done.identify'))
const answer = (accept: boolean) => submit(QM.answerCounterOffer, { accept, message: form.message || null }, t('quote.done.answered'))
const complete = () => submit(QM.completeQuote, {}, t('quote.done.complete'))
const invoice = () => submit(QM.invoiceQuote, { number: form.number, amount: Number(form.amount) }, t('quote.done.invoice'))
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !q" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="q">
      <UiPageHeader :title="q.reference" :back="back"
        :subtitle="`${q.description} · ${q.garage.name}, ${q.garage.city} · ${f.dateTime(q.submittedAt)}`">
        <template #badges>
          <FleetBadge type="QuoteStatus" :value="q.status" />
          <span v-if="q.autoApproved" class="badge success plain">{{ $t('quote.autoApproved') }}</span>
          <span v-if="q.revision > 1" class="badge plain">{{ $t('quote.revision', { n: q.revision }) }}</span>
        </template>
        <template #actions>
          <button v-if="can('IDENTIFY_VEHICLE')" class="btn btn-primary" @click="open('identify')"><Search /> {{ $t('quote.actions.IDENTIFY_VEHICLE') }}</button>
          <button v-if="can('COUNTER_OFFER')" class="btn" :class="{ 'btn-primary': q.status === 'TO_NEGOTIATE' }" @click="open('counter')">
            <HandCoins /> {{ $t('quote.actions.COUNTER_OFFER') }}
          </button>
          <button v-if="can('ACCEPT')" class="btn" :class="{ 'btn-primary': q.status === 'IN_REVIEW' }" @click="open('accept')">
            <BadgeCheck /> {{ $t('quote.actions.ACCEPT') }}
          </button>
          <button v-if="can('REJECT')" class="btn btn-danger" @click="open('reject')"><CircleSlash /> {{ $t('quote.actions.REJECT') }}</button>
          <button v-if="can('ANSWER_COUNTER_OFFER')" class="btn btn-primary" @click="open('answer')">
            <MessageSquareReply /> {{ $t('quote.actions.ANSWER_COUNTER_OFFER') }}
          </button>
          <NuxtLink v-if="can('REVISE')" :to="`/espace-garage/nouveau?revise=${q.id}`" class="btn"><FilePen /> {{ $t('quote.actions.REVISE') }}</NuxtLink>
          <button v-if="can('COMPLETE')" class="btn" :disabled="running" @click="complete"><Wrench /> {{ $t('quote.actions.COMPLETE') }}</button>
          <button v-if="can('INVOICE')" class="btn btn-primary" @click="open('invoice')"><ReceiptText /> {{ $t('quote.actions.INVOICE') }}</button>
        </template>
      </UiPageHeader>

      <ol v-if="q.status !== 'REJECTED'" class="progress card">
        <li v-for="(s, index) in steps" :key="s" :class="{ done: index <= stepIndex, current: index === stepIndex }">
          <span class="dot">{{ index + 1 }}</span>
          {{ index === 1 && q.status === 'TO_NEGOTIATE' ? f.label('QuoteStatus', 'TO_NEGOTIATE') : f.label('QuoteStatus', s) }}
        </li>
      </ol>

      <div v-if="q.status === 'RECEIVED'" class="notice info">{{ $t('quote.vehicleNotFound', { registration: q.registration }) }}</div>
      <div v-if="q.counterOffer" class="notice warning">{{ $t('quote.counterOfferPending', { amount: f.money(q.counterOffer, 2) }) }}</div>

      <div class="summary">
        <section class="card">
          <header class="card-header"><h2>{{ $t('quote.totals') }}</h2></header>
          <div class="card-body">
            <dl class="totals">
              <dt>{{ $t('quote.amount') }}</dt><dd class="num">{{ f.money(q.amount, 2) }}</dd>
              <template v-if="q.initialAmount !== q.amount">
                <dt class="muted">{{ $t('quote.revision', { n: 1 }) }}</dt><dd class="num muted">{{ f.money(q.initialAmount, 2) }}</dd>
              </template>
              <template v-if="q.referenceAmount !== null">
                <dt>{{ $t('quote.referenceAmount') }}</dt><dd class="num">{{ f.money(q.referenceAmount, 2) }}</dd>
                <dt>{{ $t('quote.overcharge') }}</dt>
                <dd class="num" :class="{ over: q.overcharge > 0 }">{{ q.overcharge > 0 ? f.money(q.overcharge, 2) : '-' }}</dd>
              </template>
              <template v-if="q.acceptedAmount !== null">
                <dt class="sep">{{ $t('quote.accepted') }}</dt><dd class="num sep strong">{{ f.money(q.acceptedAmount, 2) }}</dd>
                <dt>{{ $t('quote.savings') }}</dt>
                <dd class="num" :class="{ saved: (q.savings ?? 0) > 0 }">{{ f.money(q.savings, 2) }}</dd>
              </template>
            </dl>
            <p v-if="can('COUNTER_OFFER') && q.amount > 5000" class="field-hint hint">{{ $t('quote.approvalLimit') }}</p>
          </div>
        </section>

        <section class="card">
          <header class="card-header"><h2>{{ $t('quote.vehicle') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('quote.vehicle') }}</dt>
              <dd>
                <NuxtLink v-if="q.vehicle && session.isManager" :to="`/vehicules/${q.vehicle.id}`">
                  <VehicleCell :vehicle="{ registration: q.vehicle.registration, make: q.vehicle.make ?? '', model: q.vehicle.model ?? '' }" />
                </NuxtLink>
                <VehicleCell v-else-if="q.vehicle" :vehicle="{ registration: q.vehicle.registration, make: q.vehicle.make ?? '', model: q.vehicle.model ?? '' }" />
                <span v-else class="mono">{{ q.registration }} · {{ $t('quote.noVehicle') }}</span>
              </dd>
              <dt>{{ $t('quote.mileage') }}</dt><dd>{{ f.km(q.mileage) }}</dd>
              <dt>{{ $t('quote.garage') }}</dt>
              <dd>
                <NuxtLink v-if="session.isManager" :to="`/garages/${q.garage.id}`">{{ q.garage.name }}</NuxtLink>
                <span v-else>{{ q.garage.name }}</span>
                <span class="muted"> · {{ f.label('GarageNetwork', q.garage.network) }}</span>
              </dd>
              <dt>{{ $t('quote.garageReference') }}</dt><dd class="mono">{{ q.garageReference ?? '-' }}</dd>
              <dt>{{ $t('quote.channel') }}</dt><dd>{{ f.label('QuoteChannel', q.channel) }}</dd>
            </dl>
          </div>
        </section>

      </div>

      <section class="card lines-card">
        <header class="card-header"><h2>{{ $t('quote.lines') }}</h2></header>
        <QuoteLinesTable :lines="q.lines" :amount="q.amount" :reference-amount="q.referenceAmount" :overcharge="q.overcharge" />
      </section>

      <div class="layout">
        <section class="card">
          <header class="card-header"><h2>{{ $t('quote.timeline') }}</h2></header>
          <div class="card-body"><QuoteTimeline :history="q.history" /></div>
        </section>
        <div class="stack">
          <section v-if="q.decidedAt" class="card">
            <header class="card-header"><h2>{{ $t('quote.decision') }}</h2></header>
            <div class="card-body stack-sm">
              <p class="small">{{ $t('quote.decidedBy', { name: q.decidedBy, date: f.dateTime(q.decidedAt) }) }}</p>
              <p v-if="q.decisionComment" class="quote-comment">{{ q.decisionComment }}</p>
              <p v-if="q.completedOn" class="small"><FileCheck2 :size="14" class="inline" /> {{ $t('quote.completedOn', { date: f.date(q.completedOn) }) }}</p>
            </div>
          </section>

          <section v-if="q.invoiceNumber" class="card">
            <header class="card-header">
              <h2>{{ $t('quote.invoice') }}</h2>
              <span class="badge" :class="q.invoiceDisputed ? 'danger' : 'success'">
                {{ q.invoiceDisputed ? $t('quote.invoiceDisputed') : $t('quote.invoiceOk') }}
              </span>
            </header>
            <div class="card-body">
              <dl class="dl">
                <dt>{{ $t('quote.invoiceNumber') }}</dt><dd class="mono">{{ q.invoiceNumber }}</dd>
                <dt>{{ $t('quote.invoiceAmount') }}</dt><dd class="num">{{ f.money(q.invoiceAmount, 2) }}</dd>
                <dt>{{ $t('quote.invoiceVariance') }}</dt>
                <dd class="num" :class="{ over: q.invoiceDisputed }">{{ f.money(q.invoiceVariance, 2) }}</dd>
              </dl>
            </div>
          </section>
        </div>
      </div>

      <UiModal :open="modal === 'counter'" :title="$t('quote.counterOfferTitle')" @update:open="modal = ''">
        <form id="counter-form" class="stack" @submit.prevent="counter">
          <p class="field-hint">{{ $t('quote.counterOfferHint', { amount: f.money(q.referenceAmount, 2) }) }}</p>
          <div class="field">
            <label for="counter-amount">{{ $t('quote.proposedAmount') }}</label>
            <input id="counter-amount" v-model="form.amount" type="number" min="1" step="0.01" class="input" required
              :aria-invalid="!!fieldErrors.amount">
          </div>
          <div class="field">
            <label for="counter-message">{{ $t('quote.message') }}</label>
            <textarea id="counter-message" v-model="form.message" class="textarea" rows="3" />
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="modal = ''">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" type="submit" form="counter-form" :disabled="running">{{ $t('quote.actions.COUNTER_OFFER') }}</button>
        </template>
      </UiModal>

      <UiModal :open="modal === 'accept'" :title="$t('quote.acceptTitle')" @update:open="modal = ''">
        <form id="accept-form" class="stack" @submit.prevent="accept">
          <div class="field">
            <label for="accept-comment">{{ $t('quote.justification') }}</label>
            <textarea id="accept-comment" v-model="form.comment" class="textarea" rows="3" :required="q.overcharge > 0" />
            <span v-if="q.overcharge > 0" class="field-hint">{{ $t('quote.justificationHint', { amount: f.money(q.overcharge, 2) }) }}</span>
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="modal = ''">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" type="submit" form="accept-form" :disabled="running">{{ $t('quote.actions.ACCEPT') }}</button>
        </template>
      </UiModal>

      <UiModal :open="modal === 'reject'" :title="$t('quote.rejectTitle')" @update:open="modal = ''">
        <form id="reject-form" class="stack" @submit.prevent="reject">
          <div class="field">
            <label for="reject-reason">{{ $t('quote.reason') }}</label>
            <textarea id="reject-reason" v-model="form.reason" class="textarea" rows="3" required />
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="modal = ''">{{ $t('app.cancel') }}</button>
          <button class="btn btn-danger" type="submit" form="reject-form" :disabled="running">{{ $t('quote.actions.REJECT') }}</button>
        </template>
      </UiModal>

      <UiModal :open="modal === 'identify'" :title="$t('quote.identifyTitle')" @update:open="modal = ''">
        <form id="identify-form" class="stack" @submit.prevent="identify">
          <div class="field">
            <label for="identify-registration">{{ $t('quote.registration') }}</label>
            <input id="identify-registration" v-model="form.registration" class="input mono" required>
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="modal = ''">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" type="submit" form="identify-form" :disabled="running">{{ $t('quote.actions.IDENTIFY_VEHICLE') }}</button>
        </template>
      </UiModal>

      <UiModal :open="modal === 'answer'" :title="$t('quote.answerTitle', { amount: f.money(q.counterOffer, 2) })" @update:open="modal = ''">
        <div class="stack">
          <p class="small">{{ $t('quote.counterOfferPending', { amount: f.money(q.counterOffer, 2) }) }}</p>
          <div class="field">
            <label for="answer-message">{{ $t('quote.message') }}</label>
            <textarea id="answer-message" v-model="form.message" class="textarea" rows="3" />
          </div>
        </div>
        <template #footer>
          <button class="btn btn-danger" :disabled="running" @click="answer(false)">{{ $t('quote.refuseCounter') }}</button>
          <button class="btn btn-primary" :disabled="running" @click="answer(true)">{{ $t('quote.acceptCounter') }}</button>
        </template>
      </UiModal>

      <UiModal :open="modal === 'invoice'" :title="$t('quote.invoiceTitle')" @update:open="modal = ''">
        <form id="invoice-form" class="stack" @submit.prevent="invoice">
          <div class="field">
            <label for="invoice-number">{{ $t('quote.invoiceNumber') }}</label>
            <input id="invoice-number" v-model="form.number" class="input mono" required>
          </div>
          <div class="field">
            <label for="invoice-amount">{{ $t('quote.invoiceAmount') }}</label>
            <input id="invoice-amount" v-model="form.amount" type="number" min="0.01" step="0.01" class="input" required>
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="modal = ''">{{ $t('app.cancel') }}</button>
          <button class="btn btn-primary" type="submit" form="invoice-form" :disabled="running">{{ $t('quote.actions.INVOICE') }}</button>
        </template>
      </UiModal>
    </template>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
  padding: 14px 18px;
  list-style: none;
  overflow-x: auto;
}

.progress li {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  color: var(--text-faint);
  font-size: var(--text-sm);
  font-weight: 550;
  white-space: nowrap;
}

.progress li + li::before {
  content: "";
  flex: 0 0 24px;
  height: 1px;
  margin-right: 4px;
  background: var(--border-strong);
}

.dot {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  font-size: 11px;
}

.progress li.done {
  color: var(--text-secondary);
}

.progress li.done .dot {
  border-color: var(--primary);
  background: var(--primary);
  color: #fff;
}

.progress li.current {
  color: var(--text);
}

.notice {
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid;
  border-radius: var(--radius);
  font-size: var(--text-sm);
}

.notice.info {
  border-color: #b2ddff;
  background: var(--info-soft);
  color: var(--info);
}

.notice.warning {
  border-color: #fedf89;
  background: var(--warning-soft);
  color: var(--warning);
}

.summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 16px;
  align-items: stretch;
  margin-bottom: 16px;
}

.lines-card {
  margin-bottom: 16px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout,
  .summary {
    grid-template-columns: minmax(0, 1fr);
  }
}

.totals {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px 16px;
  margin: 0;
  font-size: var(--text-sm);
}

.totals dt {
  color: var(--text-secondary);
}

.totals dd {
  margin: 0;
  text-align: right;
  font-weight: 560;
}

.totals .sep {
  padding-top: 10px;
  border-top: 1px solid var(--border);
}

.strong {
  font-weight: 650;
}

.over {
  color: var(--danger);
}

.saved {
  color: var(--success);
}

.hint {
  margin: 14px 0 0;
}

.quote-comment {
  margin: 0;
  padding: 10px 12px;
  border-left: 3px solid var(--primary);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  font-size: var(--text-sm);
}

.inline {
  vertical-align: -2px;
}
</style>
