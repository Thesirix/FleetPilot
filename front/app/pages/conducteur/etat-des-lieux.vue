<script setup lang="ts">
import { CloudOff } from 'lucide-vue-next'
import {
  DM, DQ, INSPECTION_ITEMS, type Inspection, type InspectionCondition, type InspectionItem, type InspectionKind,
} from '~/graphql/driver'
import { Q, type Vehicle } from '~/graphql/fleet'
import { newId, newItem, type OutboxPhoto } from '~/utils/outbox'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('driverApp.inspection.title')} · FleetPilot` })

const space = useGraphqlQuery<{ mySpace: { vehicle: Vehicle | null } }>(Q.mySpace, {}, { cacheKey: 'my-space' })
const vehicle = computed(() => space.data.value?.mySpace.vehicle ?? null)
const history = useGraphqlQuery<{ myInspections: Inspection[] }>(DQ.myInspections, {}, { cacheKey: 'my-inspections' })
const outbox = useOutbox()
const waiting = computed(() => outbox.items.value.filter(i => i.kind === 'inspection'))

const CONDITIONS: InspectionCondition[] = ['OK', 'DAMAGED', 'MISSING']

function blankChecks() {
  return Object.fromEntries(INSPECTION_ITEMS.map(item => [item, { condition: 'OK' as InspectionCondition, comment: '' }])) as
    Record<InspectionItem, { condition: InspectionCondition; comment: string }>
}

const form = reactive({
  kind: 'PERIODIC' as InspectionKind,
  mileage: '' as string | number,
  fuelLevel: 75,
  remarks: '',
  checks: blankChecks(),
})
const photos = ref<OutboxPhoto[]>([])

watch(vehicle, (value) => {
  if (value && form.mileage === '') {
    form.mileage = value.mileage
  }
}, { immediate: true })

const defects = computed(() => INSPECTION_ITEMS.filter(item => form.checks[item].condition !== 'OK'))
const missingComment = computed(() => defects.value.filter(item => !form.checks[item].comment.trim()))
const valid = computed(() => form.mileage !== '' && Number(form.mileage) >= 0 && missingComment.value.length === 0)
const electric = computed(() => vehicle.value?.energy === 'ELECTRIC')
const sending = ref(false)

async function save() {
  sending.value = true
  try {
    const id = newId()
    const input = {
      kind: form.kind,
      performedAt: new Date().toISOString(),
      mileage: Number(form.mileage),
      fuelLevel: form.fuelLevel,
      remarks: form.remarks || null,
      checks: INSPECTION_ITEMS.map(item => ({
        item,
        condition: form.checks[item].condition,
        comment: form.checks[item].comment.trim() || null,
      })),
    }
    const summary = t('driverApp.outbox.inspection', {
      kind: t(`driverApp.inspection.kinds.${form.kind}`), defects: defects.value.length,
    })
    const item = newItem('inspection', summary, DM.recordMyInspection, { id, input },
      { id, photos: photos.value, photoPath: `/api/v1/me/inspections/${id}/photos` })
    if (await outbox.send(item, t('driverApp.inspection.saved'))) {
      Object.assign(form, { remarks: '', checks: blankChecks() })
      photos.value = []
      history.refresh()
      space.refresh()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } finally {
    sending.value = false
  }
}

const viewing = ref<string | null>(null)
const viewerOpen = computed({ get: () => viewing.value !== null, set: (o: boolean) => { if (!o) viewing.value = null } })
</script>

<template>
  <div class="stack page split">
    <div class="intro">
      <h1>{{ $t('driverApp.inspection.title') }}</h1>
      <p class="muted small">{{ $t('driverApp.inspection.subtitle') }}</p>
    </div>
    <UiQueryState :error="space.error.value" :stale="space.stale.value" @retry="space.refresh" />

    <form v-if="vehicle" class="stack form" @submit.prevent="save">
      <section class="card">
        <div class="card-body stack">
          <div class="segmented" role="radiogroup" :aria-label="$t('driverApp.inspection.kind')">
            <button v-for="k in (['PICKUP', 'RETURN', 'PERIODIC'] as const)" :key="k" type="button" role="radio"
              :aria-checked="form.kind === k" :class="{ on: form.kind === k }" @click="form.kind = k">
              {{ $t(`driverApp.inspection.kinds.${k}`) }}
            </button>
          </div>
          <div class="grid grid-2 tight">
            <div class="field">
              <label for="odometer">{{ $t('vehicle.mileage') }}</label>
              <input id="odometer" v-model="form.mileage" type="number" inputmode="numeric" min="0" class="input" required>
            </div>
            <div class="field">
              <label for="level">{{ electric ? $t('driverApp.inspection.battery') : $t('driverApp.inspection.fuelLevel') }}</label>
              <div class="level-row">
                <input id="level" v-model.number="form.fuelLevel" type="range" min="0" max="100" step="5">
                <strong class="num">{{ form.fuelLevel }} %</strong>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <header class="card-header">
          <h2>{{ $t('driverApp.inspection.checklist') }}</h2>
          <span class="small" :class="defects.length ? 'warn' : 'muted'">{{ $t('driverApp.inspection.defects', defects.length) }}</span>
        </header>
        <ul class="checklist">
          <li v-for="item in INSPECTION_ITEMS" :key="item" :data-item="item">
            <div class="check-row">
              <span class="small strong">{{ $t(`driverApp.inspection.items.${item}`) }}</span>
              <div class="choices" role="radiogroup" :aria-label="$t(`driverApp.inspection.items.${item}`)">
                <button v-for="c in CONDITIONS" :key="c" type="button" role="radio" :aria-checked="form.checks[item].condition === c"
                  class="choice" :class="[c.toLowerCase(), { on: form.checks[item].condition === c }]"
                  @click="form.checks[item].condition = c">
                  {{ $t(`driverApp.inspection.conditions.${c}`) }}
                </button>
              </div>
            </div>
            <input v-if="form.checks[item].condition !== 'OK'" v-model="form.checks[item].comment" class="input comment"
              :class="{ invalid: !form.checks[item].comment.trim() }" maxlength="300"
              :placeholder="$t('driverApp.inspection.whatIsWrong')" :aria-label="$t('driverApp.inspection.whatIsWrong')">
          </li>
        </ul>
      </section>

      <section class="card">
        <div class="card-body stack">
          <div class="field">
            <span class="field-label">{{ $t('driverApp.photos.title') }}</span>
            <PhotoPicker v-model="photos" />
          </div>
          <div class="field">
            <label for="remarks">{{ $t('driverApp.inspection.remarks') }}</label>
            <textarea id="remarks" v-model="form.remarks" class="textarea" maxlength="1000" rows="3" />
          </div>
          <p v-if="missingComment.length" class="field-error">{{ $t('driverApp.inspection.commentRequired') }}</p>
          <button type="submit" class="btn btn-primary big-btn" :disabled="!valid || sending">{{ $t('driverApp.inspection.save') }}</button>
        </div>
      </section>
    </form>
    <UiEmpty v-else-if="space.data.value" :text="$t('me.noVehicle')" />

    <section class="card">
      <header class="card-header"><h2>{{ $t('driverApp.inspection.history') }}</h2></header>
      <ul v-if="waiting.length || history.data.value?.myInspections.length" class="list">
        <li v-for="item in waiting" :key="item.id" class="row-between">
          <span class="small">{{ item.summary }}</span>
          <span class="badge warning"><CloudOff :size="12" /> {{ $t('driverApp.outbox.pending') }}</span>
        </li>
        <li v-for="i in history.data.value?.myInspections ?? []" :key="i.id">
          <div class="row-between">
            <div>
              <p class="small strong">{{ $t(`driverApp.inspection.kinds.${i.kind}`) }}<span class="muted"> · {{ f.dateTime(i.performedAt) }}</span></p>
              <p class="xs muted">{{ f.km(i.mileage) }} · {{ i.fuelLevel }} %</p>
            </div>
            <span class="badge" :class="i.defects ? 'warning' : 'success'">{{ $t('driverApp.inspection.defects', i.defects) }}</span>
          </div>
          <ul v-if="i.defects" class="defects xs">
            <li v-for="c in i.checks.filter(c => c.condition !== 'OK')" :key="c.item">
              <strong>{{ $t(`driverApp.inspection.items.${c.item}`) }}</strong> ({{ $t(`driverApp.inspection.conditions.${c.condition}`).toLowerCase() }}) : {{ c.comment }}
            </li>
          </ul>
          <div v-if="i.photos.length" class="thumbs">
            <PhotoThumb v-for="p in i.photos" :key="p.id" :photo-id="p.id" :size="56" @open="viewing = $event" />
          </div>
        </li>
      </ul>
      <UiEmpty v-else :text="$t('driverApp.inspection.none')" />
    </section>

    <UiModal v-model:open="viewerOpen" :title="$t('driverApp.photos.photo')" width="720px">
      <img v-if="viewing" :src="viewing" class="full" alt="">
    </UiModal>
  </div>
</template>

<style scoped>
.page {
  gap: 14px;
}

.form {
  gap: 14px;
}

.intro h1 {
  margin: 4px 0 2px;
  font-size: var(--text-xl);
}

.intro p {
  margin: 0;
}

.tight {
  gap: 12px;
}

.segmented {
  display: flex;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--neutral-soft);
}

.segmented button {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
}

.segmented button.on {
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: var(--text);
}

.level-row {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
}

.level-row input {
  flex: 1;
  min-width: 0;
  accent-color: var(--primary);
}

.level-row strong {
  width: 44px;
  text-align: right;
  font-size: var(--text-sm);
}

.checklist {
  margin: 0;
  padding: 0;
  list-style: none;
}

.checklist > li {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
}

.checklist > li:last-child {
  border-bottom: none;
}

.check-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.choices {
  display: flex;
  flex: none;
  gap: 4px;
}

.choice {
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-xs);
  font-weight: 500;
  cursor: pointer;
}

.choice.ok.on {
  border-color: var(--success);
  background: var(--success-soft);
  color: var(--success);
}

.choice.damaged.on {
  border-color: var(--warning);
  background: var(--warning-soft);
  color: var(--warning);
}

.choice.missing.on {
  border-color: var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
}

.comment {
  margin-top: 8px;
}

.comment.invalid {
  border-color: var(--warning);
}

.warn {
  color: var(--warning);
  font-weight: 600;
}

.strong {
  font-weight: 600;
}

.big-btn {
  width: 100%;
  height: 44px;
  font-size: var(--text-md);
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.list > li {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
}

.list > li:last-child {
  border-bottom: none;
}

.list p {
  margin: 0;
}

.defects {
  margin: 8px 0 0;
  padding-left: 16px;
  color: var(--text-secondary);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.badge.warning {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--warning-soft);
  color: var(--warning);
}

.badge.success {
  background: var(--success-soft);
  color: var(--success);
}

.full {
  display: block;
  width: 100%;
  border-radius: var(--radius);
}

@media (max-width: 380px) {
  .check-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
