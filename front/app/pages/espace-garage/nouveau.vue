<script setup lang="ts">
import { Plus, Send, Trash2 } from 'lucide-vue-next'
import { QM, QQ, type Garage, type LineDraft, type Quote, type Tariff } from '~/graphql/quotes'
import { graphql } from '~/utils/graphql'

const { t } = useI18n()
const f = useFormat()
const route = useRoute()
/** A new version of an existing quote (after a counter-offer, or to correct it): same vehicle, new lines. */
const reviseId = computed(() => (typeof route.query.revise === 'string' ? route.query.revise : null))
const title = computed(() => (reviseId.value ? t('garageSpace.reviseTitle') : t('garageSpace.newQuote')))
useHead({ title: () => `${title.value} · FleetPilot` })

const { data: tariffData } = useGraphqlQuery<{ tariff: Tariff }>(QQ.tariff, {}, { cacheKey: 'tariff' })
const { data: space } = useGraphqlQuery<{ myGarage: Garage | null }>(
  `{ myGarage { id name hourlyRate region regionLabel } }`, {}, { cacheKey: 'my-garage' })
const hourlyRate = computed(() => space.value?.myGarage?.hourlyRate ?? null)

const form = reactive({ registration: '', garageReference: '', mileage: null as number | null, description: '', message: '' })
const lines = ref<LineDraft[]>([
  { kind: 'LABOUR', code: '', label: '', quantity: null, unitPrice: null },
  { kind: 'PART', code: '', label: '', quantity: 1, unitPrice: null },
])

// The quote to revise, read only when there is one (a new quote asks for nothing)
const revised = { data: shallowRef<{ quote: Quote | null } | null>(null) }
watch(reviseId, async (id) => {
  revised.data.value = id ? (await graphql<{ quote: Quote | null }>(QQ.quote, { id })).data : null
}, { immediate: true })
watch(() => revised.data.value?.quote, (quote) => {
  if (!quote || !reviseId.value) return
  form.registration = quote.registration
  form.garageReference = quote.garageReference ?? ''
  form.mileage = quote.mileage ?? null
  form.description = quote.description
  lines.value = quote.lines.map(line => ({ kind: line.kind, code: line.code ?? '', label: line.label,
    quantity: line.quantity, unitPrice: line.unitPrice }))
}, { immediate: true })

const parts = computed(() => tariffData.value?.tariff.parts ?? [])
const operations = computed(() => tariffData.value?.tariff.operations ?? [])

function options(kind: string) {
  return kind === 'PART' ? parts.value.map(p => ({ code: p.code, label: p.label }))
    : kind === 'LABOUR' ? operations.value.map(o => ({ code: o.code, label: o.label })) : []
}

/** Choosing an item of the tariff fills the label, and the standard time and the garage rate for labour. */
function pick(line: LineDraft) {
  if (line.kind === 'PART') {
    const part = parts.value.find(p => p.code === line.code)
    if (part) line.label = part.label
  } else if (line.kind === 'LABOUR') {
    const operation = operations.value.find(o => o.code === line.code)
    if (operation) {
      line.label = operation.label
      line.quantity = operation.hoursCar
      line.unitPrice = hourlyRate.value
    }
  }
}

function hint(line: LineDraft) {
  if (line.kind === 'PART') {
    const part = parts.value.find(p => p.code === line.code)
    return part ? t('garageSpace.referencePrice', { value: f.money(part.priceCar, 2) }) : ''
  }
  if (line.kind === 'LABOUR') {
    const operation = operations.value.find(o => o.code === line.code)
    return operation ? t('garageSpace.standardTime', { value: f.number(operation.hoursCar, 2) }) : ''
  }
  return ''
}

function changeKind(line: LineDraft) {
  line.code = ''
  if (line.kind === 'SUPPLIES') line.quantity = 1
  if (line.kind === 'LABOUR' && !line.unitPrice) line.unitPrice = hourlyRate.value
}

const add = () => lines.value.push({ kind: 'PART', code: '', label: '', quantity: 1, unitPrice: null })
const remove = (index: number) => lines.value.splice(index, 1)
const lineTotal = (line: LineDraft) => (Number(line.quantity) || 0) * (Number(line.unitPrice) || 0)
const total = computed(() => lines.value.reduce((sum, line) => sum + lineTotal(line), 0))

const { run, running, fieldErrors } = useMutation()
const lineInputs = () => lines.value.map(line => ({ kind: line.kind, code: line.code || null, label: line.label,
  quantity: Number(line.quantity), unitPrice: Number(line.unitPrice) }))
async function submit() {
  if (reviseId.value) {
    const done = await run<{ reviseQuote: { id: string; revision: number } }>(QM.reviseQuote,
      { id: reviseId.value, lines: lineInputs(), message: form.message || null })
    if (done) {
      useToast().success(t('garageSpace.revised', { revision: done.reviseQuote.revision }))
      await navigateTo(`/devis/${reviseId.value}`)
    }
    return
  }
  const input = {
    registration: form.registration,
    garageReference: form.garageReference || null,
    mileage: form.mileage === null || form.mileage === undefined || (form.mileage as unknown) === '' ? null : Number(form.mileage),
    description: form.description,
    lines: lineInputs(),
  }
  const result = await run<{ submitQuote: { id: string; reference: string } }>(QM.submitQuote, { input })
  if (result) {
    useToast().success(t('quote.done.submitted', { reference: result.submitQuote.reference }))
    await navigateTo(`/devis/${result.submitQuote.id}`)
  }
}
</script>

<template>
  <div>
    <UiPageHeader :title="title" :subtitle="reviseId ? $t('garageSpace.reviseSubtitle', { reference: revised.data.value?.quote?.reference ?? '' })
      : $t('garageSpace.newQuoteSubtitle')" :back="reviseId ? `/devis/${reviseId}` : '/espace-garage'" />

    <form class="stack" @submit.prevent="submit">
      <section class="card">
        <header class="card-header"><h2>{{ $t('garageSpace.vehicle') }}</h2></header>
        <div class="card-body grid grid-3">
          <div class="field">
            <label for="registration">{{ $t('quote.registration') }}</label>
            <input id="registration" v-model="form.registration" class="input mono" required placeholder="AB-123-CD" :disabled="!!reviseId"
              :aria-invalid="!!fieldErrors.registration">
            <span v-if="fieldErrors.registration" class="field-error">{{ fieldErrors.registration }}</span>
            <span v-else class="field-hint">{{ $t('garageSpace.registrationHint') }}</span>
          </div>
          <div class="field">
            <label for="garage-reference">{{ $t('garageSpace.garageReference') }}</label>
            <input id="garage-reference" v-model="form.garageReference" class="input mono" maxlength="40" :disabled="!!reviseId">
          </div>
          <div class="field">
            <label for="mileage">{{ $t('quote.mileage') }}</label>
            <input id="mileage" v-model="form.mileage" type="number" min="0" class="input" :disabled="!!reviseId">
          </div>
          <div class="field span-3">
            <label for="description">{{ $t('quote.description') }}</label>
            <input id="description" v-model="form.description" class="input" required maxlength="1000" :disabled="!!reviseId"
              :aria-invalid="!!fieldErrors.description">
          </div>
          <div v-if="reviseId" class="field span-3">
            <label for="message">{{ $t('garageSpace.reviseMessage') }}</label>
            <input id="message" v-model="form.message" class="input" maxlength="500" :placeholder="$t('garageSpace.reviseMessageHint')">
          </div>
        </div>
      </section>

      <section class="card">
        <header class="card-header">
          <h2>{{ $t('garageSpace.linesTitle') }}</h2>
          <span v-if="hourlyRate" class="small muted">{{ $t('garageSpace.yourRate', { rate: f.money(hourlyRate, 2) }) }}</span>
        </header>
        <div class="table-wrap">
          <table class="table editor">
            <thead>
              <tr>
                <th>{{ $t('garageSpace.kind') }}</th>
                <th>{{ $t('garageSpace.code') }}</th>
                <th>{{ $t('garageSpace.label') }}</th>
                <th class="right">{{ $t('garageSpace.quantity') }}</th>
                <th class="right">{{ $t('garageSpace.price') }}</th>
                <th class="right">{{ $t('garageSpace.total') }}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, index) in lines" :key="index">
                <td>
                  <select v-model="line.kind" class="select" :aria-label="$t('garageSpace.kind')" @change="changeKind(line)">
                    <option v-for="k in ['LABOUR', 'PART', 'SUPPLIES']" :key="k" :value="k">{{ f.label('LineKind', k) }}</option>
                  </select>
                </td>
                <td>
                  <select v-model="line.code" class="select code" :aria-label="$t('garageSpace.code')" :disabled="line.kind === 'SUPPLIES'"
                    @change="pick(line)">
                    <option value="">{{ $t('garageSpace.codeNone') }}</option>
                    <option v-for="o in options(line.kind)" :key="o.code" :value="o.code">{{ o.code }} · {{ o.label }}</option>
                  </select>
                  <div class="xs muted hint">{{ hint(line) }}</div>
                </td>
                <td><input v-model="line.label" class="input" required :aria-label="$t('garageSpace.label')"></td>
                <td class="right">
                  <input v-model="line.quantity" type="number" min="0.01" step="0.01" class="input num-input" required
                    :aria-label="line.kind === 'LABOUR' ? $t('garageSpace.hours') : $t('garageSpace.quantity')">
                </td>
                <td class="right">
                  <input v-model="line.unitPrice" type="number" min="0" step="0.01" class="input num-input" required
                    :aria-label="line.kind === 'LABOUR' ? $t('garageSpace.rate') : $t('garageSpace.price')">
                </td>
                <td class="right num nowrap">{{ f.money(lineTotal(line), 2) }}</td>
                <td class="right">
                  <button type="button" class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('garageSpace.removeLine')"
                    :disabled="lines.length === 1" @click="remove(index)"><Trash2 /></button>
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="5">
                  <button type="button" class="btn btn-sm" @click="add"><Plus /> {{ $t('garageSpace.addLine') }}</button>
                </td>
                <td class="right num total">{{ f.money(total, 2) }}</td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      </section>

      <div class="row actions">
        <NuxtLink :to="reviseId ? `/devis/${reviseId}` : '/espace-garage'" class="btn">{{ $t('app.cancel') }}</NuxtLink>
        <button class="btn btn-primary" type="submit" :disabled="running"><Send /> {{ $t('garageSpace.send') }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.span-3 {
  grid-column: 1 / -1;
}

.editor td {
  vertical-align: top;
}

.editor .select {
  min-width: 130px;
}

.editor .code {
  min-width: 220px;
  max-width: 300px;
}

.hint {
  min-height: 16px;
  margin-top: 4px;
}

.num-input {
  width: 100px;
  text-align: right;
}

tfoot td {
  border-top: 1px solid var(--border-strong);
  background: var(--surface-muted);
}

.total {
  font-weight: 650;
}

.actions {
  justify-content: flex-end;
}
</style>
