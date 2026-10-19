<script setup lang="ts">
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { AM, AQ, type AnalysisRules, type EvModelRow, type LowEmissionZone } from '~/graphql/admin'

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('settings.title')} · FleetPilot` })

const state = useUrlState({ tab: 'rules' })
const tabs = computed(() => [
  { key: 'rules', label: t('settings.rulesTab') },
  { key: 'models', label: t('settings.modelsTab'), count: refs.data.value?.evCatalog.length },
  { key: 'zones', label: t('settings.zonesTab'), count: refs.data.value?.lowEmissionZones.length },
])
const tab = computed({ get: () => state.tab, set: (v: string) => { state.tab = v } })

const rulesQuery = useGraphqlQuery<{ analysisRules: AnalysisRules }>(AQ.rules)
const refs = useGraphqlQuery<{ evCatalog: EvModelRow[]; lowEmissionZones: LowEmissionZone[] }>(AQ.references)
const { run, running, fieldErrors } = useMutation()
const readOnly = computed(() => !session.isAdmin)

// ---------------------------------------------------------------- analysis rules (shares edited as percentages)
const rules = reactive({ partTolerance: 0, timeTolerance: 0, rateTolerance: 0, suppliesShare: 0, suppliesMinimum: 0,
  negotiationShare: 0, negotiationMinimum: 0, autoApprovalLimit: 0, managerApprovalLimit: 0 })
const PERCENT = ['partTolerance', 'timeTolerance', 'rateTolerance', 'suppliesShare', 'negotiationShare'] as const
const AMOUNTS = ['suppliesMinimum', 'negotiationMinimum', 'autoApprovalLimit', 'managerApprovalLimit'] as const
watch(() => rulesQuery.data.value?.analysisRules, (r) => {
  if (!r) return
  AMOUNTS.forEach((k) => { rules[k] = r[k] })
  PERCENT.forEach((k) => { rules[k] = Math.round(r[k] * 10000) / 100 })
}, { immediate: true })
async function saveRules() {
  // Only the fields of the form: the date and the author of the last change are not sent back
  const input: Record<string, number> = { version: rulesQuery.data.value!.analysisRules.version }
  PERCENT.forEach((k) => { input[k] = Number(rules[k]) / 100 })
  AMOUNTS.forEach((k) => { input[k] = Number(rules[k]) })
  if (await run(AM.updateRules, { input }, t('settings.rulesSaved'))) await rulesQuery.refresh()
}

// ---------------------------------------------------------------- electric models
const modelDialog = ref(false)
const model = reactive({ code: '', make: '', model: '', category: 'CAR', segment: 'CITY', batteryKwh: 50, wltpRangeKm: 400,
  consumptionKwh100km: 16, fastChargeKw: 100, monthlyRent: 400, existing: false })
const SEGMENTS: Record<string, string[]> = { CAR: ['CITY', 'COMPACT', 'LARGE'], VAN: ['SMALL', 'MEDIUM', 'LARGE'], TRUCK: ['TRUCK'] }
function editModel(row?: EvModelRow) {
  Object.assign(model, row
    ? { code: row.code, make: row.make ?? '', model: row.model ?? row.name, category: row.category, segment: row.segment,
        batteryKwh: row.batteryKwh, wltpRangeKm: row.wltpRangeKm, consumptionKwh100km: row.consumptionKwh100km,
        fastChargeKw: row.fastChargeKw, monthlyRent: row.monthlyRent, existing: true }
    : { code: '', make: '', model: '', category: 'CAR', segment: 'CITY', batteryKwh: 50, wltpRangeKm: 400,
        consumptionKwh100km: 16, fastChargeKw: 100, monthlyRent: 400, existing: false })
  modelDialog.value = true
}
watch(() => model.category, (c) => { if (!SEGMENTS[c]!.includes(model.segment)) model.segment = SEGMENTS[c]![0]! })
async function saveModel() {
  const { code, existing: _existing, ...rest } = model
  const input = { ...rest, batteryKwh: Number(rest.batteryKwh), wltpRangeKm: Number(rest.wltpRangeKm),
    consumptionKwh100km: Number(rest.consumptionKwh100km), fastChargeKw: Number(rest.fastChargeKw),
    monthlyRent: Number(rest.monthlyRent) }
  const result = await run<{ saveEvModel: number }>(AM.saveEvModel, { code: code.trim().toUpperCase(), input })
  if (result) {
    useToast().success(t('settings.modelSaved', { name: `${model.make} ${model.model}`, count: result.saveEvModel }))
    modelDialog.value = false
    await refs.refresh()
  }
}
const toDelete = ref<{ kind: 'model' | 'zone'; key: string; label: string } | null>(null)
const deleteOpen = computed({ get: () => toDelete.value !== null, set: (v: boolean) => { if (!v) toDelete.value = null } })
async function confirmDelete() {
  const target = toDelete.value!
  const result = target.kind === 'model'
    ? await run<{ deleteEvModel: number }>(AM.deleteEvModel, { code: target.key })
    : await run<{ deleteLowEmissionZone: number }>(AM.deleteZone, { city: target.key })
  if (result) {
    useToast().success(t('settings.deleted', { name: target.label }))
    toDelete.value = null
    await refs.refresh()
  }
}

// ---------------------------------------------------------------- low emission zones
const zoneDialog = ref(false)
const zone = reactive({ city: '', zone: '', maxCritAir: 2, effectiveFrom: '', status: 'IN_FORCE', existing: false })
function editZone(row?: LowEmissionZone) {
  Object.assign(zone, row ? { ...row, existing: true }
    : { city: '', zone: '', maxCritAir: 3, effectiveFrom: new Date().toISOString().slice(0, 10), status: 'ANNOUNCED', existing: false })
  zoneDialog.value = true
}
async function saveZone() {
  const result = await run<{ saveLowEmissionZone: number }>(AM.saveZone, { city: zone.city.trim(),
    input: { zone: zone.zone, maxCritAir: Number(zone.maxCritAir), effectiveFrom: zone.effectiveFrom, status: zone.status } })
  if (result) {
    useToast().success(t('settings.zoneSaved', { city: zone.city, count: result.saveLowEmissionZone }))
    zoneDialog.value = false
    await refs.refresh()
  }
}
const err = (field: string) => fieldErrors.value[field]
</script>

<template>
  <div>
    <UiPageHeader :title="$t('settings.title')" :subtitle="$t('settings.subtitle')" />
    <p v-if="readOnly" class="banner small">{{ $t('settings.readOnly') }}</p>
    <UiTabs v-model="tab" :tabs="tabs" class="tabs" />

    <section v-if="tab === 'rules'" class="card">
      <header class="card-header">
        <h2>{{ $t('settings.rulesTitle') }}</h2>
        <span v-if="rulesQuery.data.value" class="xs muted">
          {{ $t('settings.lastChange', { date: f.dateTime(rulesQuery.data.value.analysisRules.updatedAt), by: rulesQuery.data.value.analysisRules.updatedBy }) }}
        </span>
      </header>
      <form class="card-body stack" @submit.prevent="saveRules">
        <p class="small muted">{{ $t('settings.rulesIntro') }}</p>
        <fieldset class="group">
          <legend>{{ $t('settings.tolerances') }}</legend>
          <div class="grid grid-3">
            <div class="field">
              <label for="r-part">{{ $t('settings.partTolerance') }}</label>
              <div class="suffix"><input id="r-part" v-model.number="rules.partTolerance" type="number" min="0" max="100" step="any" class="input" :disabled="readOnly"><span>%</span></div>
              <span class="field-hint">{{ $t('settings.partToleranceHint') }}</span>
            </div>
            <div class="field">
              <label for="r-time">{{ $t('settings.timeTolerance') }}</label>
              <div class="suffix"><input id="r-time" v-model.number="rules.timeTolerance" type="number" min="0" max="100" step="any" class="input" :disabled="readOnly"><span>%</span></div>
              <span class="field-hint">{{ $t('settings.timeToleranceHint') }}</span>
            </div>
            <div class="field">
              <label for="r-rate">{{ $t('settings.rateTolerance') }}</label>
              <div class="suffix"><input id="r-rate" v-model.number="rules.rateTolerance" type="number" min="0" max="100" step="any" class="input" :disabled="readOnly"><span>%</span></div>
              <span class="field-hint">{{ $t('settings.rateToleranceHint') }}</span>
            </div>
            <div class="field">
              <label for="r-supplies">{{ $t('settings.suppliesShare') }}</label>
              <div class="suffix"><input id="r-supplies" v-model.number="rules.suppliesShare" type="number" min="0" max="100" step="any" class="input" :disabled="readOnly"><span>%</span></div>
            </div>
            <div class="field">
              <label for="r-supplies-min">{{ $t('settings.suppliesMinimum') }}</label>
              <div class="suffix"><input id="r-supplies-min" v-model.number="rules.suppliesMinimum" type="number" min="0" step="any" class="input" :disabled="readOnly"><span>€</span></div>
            </div>
          </div>
        </fieldset>
        <fieldset class="group">
          <legend>{{ $t('settings.decisions') }}</legend>
          <div class="grid grid-3">
            <div class="field">
              <label for="r-nego">{{ $t('settings.negotiationShare') }}</label>
              <div class="suffix"><input id="r-nego" v-model.number="rules.negotiationShare" type="number" min="0" max="100" step="any" class="input" :disabled="readOnly"><span>%</span></div>
              <span class="field-hint">{{ $t('settings.negotiationHint', { min: f.money(rules.negotiationMinimum) }) }}</span>
            </div>
            <div class="field">
              <label for="r-nego-min">{{ $t('settings.negotiationMinimum') }}</label>
              <div class="suffix"><input id="r-nego-min" v-model.number="rules.negotiationMinimum" type="number" min="0" step="any" class="input" :disabled="readOnly"><span>€</span></div>
            </div>
            <div class="field">
              <label for="r-auto">{{ $t('settings.autoApprovalLimit') }}</label>
              <div class="suffix"><input id="r-auto" v-model.number="rules.autoApprovalLimit" type="number" min="0" step="any" class="input" :disabled="readOnly"><span>€ HT</span></div>
              <span v-if="err('autoApprovalLimit')" class="field-error">{{ err('autoApprovalLimit') }}</span>
              <span v-else class="field-hint">{{ $t('settings.autoApprovalHint') }}</span>
            </div>
            <div class="field">
              <label for="r-manager">{{ $t('settings.managerApprovalLimit') }}</label>
              <div class="suffix"><input id="r-manager" v-model.number="rules.managerApprovalLimit" type="number" min="1" step="any" class="input" :disabled="readOnly"><span>€ HT</span></div>
              <span class="field-hint">{{ $t('settings.managerApprovalHint') }}</span>
            </div>
          </div>
        </fieldset>
        <div v-if="!readOnly" class="row actions">
          <button type="submit" class="btn btn-primary" :disabled="running">{{ $t('settings.saveRules') }}</button>
        </div>
      </form>
    </section>

    <section v-else-if="tab === 'models'" class="card">
      <header class="card-header">
        <h2>{{ $t('settings.modelsTitle') }}</h2>
        <button v-if="!readOnly" class="btn btn-sm" @click="editModel()"><Plus /> {{ $t('settings.addModel') }}</button>
      </header>
      <div class="card-body flush">
        <UiDataTable :rows="refs.data.value?.evCatalog ?? []" row-key="code" :loading="refs.pending.value" :columns="[
          { key: 'name', label: $t('settings.model') },
          { key: 'category', label: $t('settings.category') },
          { key: 'battery', label: $t('settings.battery'), align: 'right' },
          { key: 'range', label: $t('settings.range'), align: 'right' },
          { key: 'consumption', label: $t('settings.consumption'), align: 'right' },
          { key: 'rent', label: $t('settings.rent'), align: 'right' },
          { key: 'actions', label: '', align: 'right' },
        ]">
          <template #cell-name="{ row }"><div class="strong">{{ row.name }}</div><div class="xs muted mono">{{ row.code }}</div></template>
          <template #cell-category="{ row }">
            <span class="secondary small">{{ f.label('VehicleCategoryLong', row.category) }}<template v-if="row.segment !== 'TRUCK'">
              · {{ $t(`settings.segments.${row.segment}`) }}</template></span>
          </template>
          <template #cell-battery="{ row }"><span class="num">{{ f.number(row.batteryKwh, 0) }} kWh</span></template>
          <template #cell-range="{ row }">
            <span class="num">{{ f.km(row.realisticRangeKm) }}</span><div class="xs muted">WLTP {{ f.km(row.wltpRangeKm) }}</div>
          </template>
          <template #cell-consumption="{ row }"><span class="num">{{ f.number(row.consumptionKwh100km, 1) }} kWh</span></template>
          <template #cell-rent="{ row }"><span class="num">{{ f.money(row.monthlyRent) }}</span></template>
          <template #cell-actions="{ row }">
            <div v-if="!readOnly" class="row-actions">
              <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('vehicle.edit')" @click="editModel(row)"><Pencil /></button>
              <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('companies.delete')"
                @click="toDelete = { kind: 'model', key: row.code, label: row.name }"><Trash2 /></button>
            </div>
          </template>
        </UiDataTable>
      </div>
    </section>

    <section v-else class="card">
      <header class="card-header">
        <h2>{{ $t('settings.zonesTitle') }}</h2>
        <button v-if="!readOnly" class="btn btn-sm" @click="editZone()"><Plus /> {{ $t('settings.addZone') }}</button>
      </header>
      <div class="card-body flush">
        <UiDataTable :rows="refs.data.value?.lowEmissionZones ?? []" row-key="city" :loading="refs.pending.value" :columns="[
          { key: 'city', label: $t('settings.city') },
          { key: 'zone', label: $t('settings.zone') },
          { key: 'critair', label: $t('settings.maxCritAir') },
          { key: 'from', label: $t('settings.effectiveFrom') },
          { key: 'status', label: $t('settings.zoneStatus') },
          { key: 'actions', label: '', align: 'right' },
        ]">
          <template #cell-city="{ row }"><span class="strong">{{ row.city }}</span></template>
          <template #cell-zone="{ row }"><span class="secondary">{{ row.zone }}</span></template>
          <template #cell-critair="{ row }"><span class="row critair"><CritAirBadge :value="row.maxCritAir" /> {{ $t('settings.andBetter') }}</span></template>
          <template #cell-from="{ row }">{{ f.date(row.effectiveFrom) }}</template>
          <template #cell-status="{ row }">
            <span class="badge" :class="row.status === 'IN_FORCE' ? 'warning' : 'info'">{{ $t(`settings.zoneStatuses.${row.status}`) }}</span>
          </template>
          <template #cell-actions="{ row }">
            <div v-if="!readOnly" class="row-actions">
              <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('vehicle.edit')" @click="editZone(row)"><Pencil /></button>
              <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('companies.delete')"
                @click="toDelete = { kind: 'zone', key: row.city, label: row.zone + ' (' + row.city + ')' }"><Trash2 /></button>
            </div>
          </template>
        </UiDataTable>
      </div>
    </section>

    <UiModal v-if="modelDialog" v-model:open="modelDialog" :title="model.existing ? $t('settings.editModel') : $t('settings.addModel')" width="640px">
      <form id="model-form" class="grid grid-2" @submit.prevent="saveModel">
        <div class="field">
          <label for="m-code">{{ $t('settings.code') }}</label>
          <input id="m-code" v-model="model.code" class="input mono" required pattern="[A-Za-z0-9-]{2,20}" maxlength="20"
            :disabled="model.existing" placeholder="E-RIFTER">
        </div>
        <div class="field">
          <label for="m-make">{{ $t('settings.make') }}</label>
          <input id="m-make" v-model="model.make" class="input" required maxlength="40" :aria-invalid="!!err('make')">
        </div>
        <div class="field wide">
          <label for="m-model">{{ $t('settings.model') }}</label>
          <input id="m-model" v-model="model.model" class="input" required maxlength="80" placeholder="e-Rifter 50 kWh" :aria-invalid="!!err('model')">
        </div>
        <div class="field">
          <label for="m-category">{{ $t('settings.category') }}</label>
          <select id="m-category" v-model="model.category" class="select">
            <option v-for="c in ['CAR', 'VAN', 'TRUCK']" :key="c" :value="c">{{ f.label('VehicleCategoryLong', c) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="m-segment">{{ $t('settings.segment') }}</label>
          <select id="m-segment" v-model="model.segment" class="select">
            <option v-for="s in SEGMENTS[model.category]" :key="s" :value="s">{{ $t(`settings.segments.${s}`) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="m-battery">{{ $t('settings.battery') }} (kWh)</label>
          <input id="m-battery" v-model.number="model.batteryKwh" type="number" min="10" max="1000" step="0.1" class="input" required :aria-invalid="!!err('batteryKwh')">
          <span v-if="err('batteryKwh')" class="field-error">{{ err('batteryKwh') }}</span>
        </div>
        <div class="field">
          <label for="m-range">{{ $t('settings.wltpRange') }} (km)</label>
          <input id="m-range" v-model.number="model.wltpRangeKm" type="number" min="50" max="1500" class="input" required :aria-invalid="!!err('wltpRangeKm')">
          <span v-if="err('wltpRangeKm')" class="field-error">{{ err('wltpRangeKm') }}</span>
          <span v-else class="field-hint">{{ $t('settings.realisticRange', { km: f.km(Math.round(Number(model.wltpRangeKm) * 0.7)) }) }}</span>
        </div>
        <div class="field">
          <label for="m-consumption">{{ $t('settings.consumption') }} (kWh/100 km)</label>
          <input id="m-consumption" v-model.number="model.consumptionKwh100km" type="number" min="5" max="200" step="0.1" class="input" required>
        </div>
        <div class="field">
          <label for="m-charge">{{ $t('settings.fastCharge') }} (kW)</label>
          <input id="m-charge" v-model.number="model.fastChargeKw" type="number" min="7" max="1000" class="input" required>
        </div>
        <div class="field">
          <label for="m-rent">{{ $t('settings.rent') }} (€)</label>
          <input id="m-rent" v-model.number="model.monthlyRent" type="number" min="50" step="1" class="input" required :aria-invalid="!!err('monthlyRent')">
          <span v-if="err('monthlyRent')" class="field-error">{{ err('monthlyRent') }}</span>
        </div>
      </form>
      <p class="xs muted">{{ $t('settings.reassessHint') }}</p>
      <template #footer>
        <button class="btn" @click="modelDialog = false">{{ $t('app.cancel') }}</button>
        <button type="submit" form="model-form" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="zoneDialog" v-model:open="zoneDialog" :title="zone.existing ? $t('settings.editZone') : $t('settings.addZone')" width="560px">
      <form id="zone-form" class="grid grid-2" @submit.prevent="saveZone">
        <div class="field">
          <label for="z-city">{{ $t('settings.city') }}</label>
          <input id="z-city" v-model="zone.city" class="input" required minlength="2" maxlength="80" :disabled="zone.existing">
        </div>
        <div class="field">
          <label for="z-zone">{{ $t('settings.zone') }}</label>
          <input id="z-zone" v-model="zone.zone" class="input" required maxlength="120" placeholder="ZFE Marseille Provence" :aria-invalid="!!err('zone')">
        </div>
        <div class="field">
          <label for="z-critair">{{ $t('settings.maxCritAir') }}</label>
          <select id="z-critair" v-model.number="zone.maxCritAir" class="select">
            <option v-for="n in [1, 2, 3, 4, 5]" :key="n" :value="n">Crit'Air {{ n }} {{ $t('settings.andBetter') }}</option>
          </select>
        </div>
        <div class="field">
          <label for="z-from">{{ $t('settings.effectiveFrom') }}</label>
          <input id="z-from" v-model="zone.effectiveFrom" type="date" class="input" required>
        </div>
        <div class="field wide">
          <span class="field-label">{{ $t('settings.zoneStatus') }}</span>
          <div class="row segmented">
            <label class="checkbox"><input v-model="zone.status" type="radio" value="IN_FORCE"> {{ $t('settings.zoneStatuses.IN_FORCE') }}</label>
            <label class="checkbox"><input v-model="zone.status" type="radio" value="ANNOUNCED"> {{ $t('settings.zoneStatuses.ANNOUNCED') }}</label>
          </div>
        </div>
      </form>
      <p class="xs muted">{{ $t('settings.reassessHint') }}</p>
      <template #footer>
        <button class="btn" @click="zoneDialog = false">{{ $t('app.cancel') }}</button>
        <button type="submit" form="zone-form" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="toDelete" v-model:open="deleteOpen" :title="$t('settings.deleteTitle')" width="440px">
      <p class="small">{{ $t('settings.deleteText', { name: toDelete.label }) }}</p>
      <template #footer>
        <button class="btn" @click="toDelete = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-danger" :disabled="running" @click="confirmDelete">{{ $t('companies.delete') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.tabs {
  margin-bottom: 16px;
}

.banner {
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: var(--info-soft);
  color: var(--text-secondary);
}

.group {
  border: 0;
  padding: 0;
  margin: 0;
}

.group legend {
  margin-bottom: 10px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
}

.suffix {
  display: flex;
  align-items: center;
  gap: 8px;
}

.suffix span {
  color: var(--text-muted);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.actions {
  justify-content: flex-end;
}

.row-actions {
  display: inline-flex;
  gap: 2px;
}

.strong {
  font-weight: 540;
}

.critair {
  gap: 6px;
  font-size: var(--text-sm);
  color: var(--text-secondary);
}

.wide {
  grid-column: 1 / -1;
}

.segmented {
  gap: 18px;
  min-height: 34px;
}
</style>
