<script setup lang="ts">
import { Q, type Company, type CostCenter, type Site } from '~/graphql/fleet'

/** What the form edits: the fields of a new vehicle; the ones that only exist at creation are ignored in edit mode. */
export interface VehicleFormValue {
  companyId: string
  siteId: string
  costCenterId: string
  registration: string
  vin: string
  category: string
  make: string
  model: string
  trimLevel: string
  energy: string
  critAir: number
  co2GramsPerKm: number
  fiscalPower: number | null
  firstRegistrationDate: string
  acquisition: string
  mileage: number
  status: string
}

const props = defineProps<{
  /** Edit an existing vehicle: the VIN, the company, the acquisition, the mileage and the status are not changed here. */
  edit?: boolean
  running?: boolean
  fieldErrors: Record<string, string>
  cancelTo: string
  submitLabel: string
}>()
const form = defineModel<VehicleFormValue>({ required: true })
const emit = defineEmits<{ submit: [] }>()

const f = useFormat()
const session = useSessionStore()
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
watch(() => companies.value?.companies, (list) => {
  if (list?.length && !form.value.companyId) form.value.companyId = session.viewer?.companyId ?? list[0]!.id
}, { immediate: true })

const options = useGraphqlQuery<{ company: { sites: Site[]; costCenters: CostCenter[] } | null }>(Q.companyOptions,
  () => ({ id: form.value.companyId || '00000000-0000-0000-0000-000000000000' }))

// A zero-emission vehicle always has the Crit'Air E sticker and no CO2
watch(() => form.value.energy, (energy, previous) => {
  if (previous === undefined) return
  if (energy === 'ELECTRIC' || energy === 'HYDROGEN') {
    form.value.critAir = 0
    form.value.co2GramsPerKm = 0
  } else if (form.value.critAir === 0) {
    form.value.critAir = energy === 'DIESEL' ? 2 : 1
  }
})
const zeroEmission = computed(() => form.value.energy === 'ELECTRIC' || form.value.energy === 'HYDROGEN')
const err = (field: string) => props.fieldErrors[field]
</script>

<template>
  <form class="stack" @submit.prevent="emit('submit')">
    <section class="card">
      <header class="card-header"><h2>{{ $t('vehicle.identity') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="registration">{{ $t('vehicle.registration') }}</label>
          <input id="registration" v-model="form.registration" class="input mono" placeholder="AB-123-CD" required
            :aria-invalid="!!err('registration')">
          <span v-if="err('registration')" class="field-error">{{ err('registration') }}</span>
        </div>
        <div class="field">
          <label for="vin">{{ $t('vehicle.vin') }}</label>
          <input id="vin" v-model="form.vin" class="input mono" maxlength="17" required :disabled="edit"
            :aria-invalid="!!err('vin')">
          <span v-if="err('vin')" class="field-error">{{ err('vin') }}</span>
        </div>
        <div class="field">
          <label for="first">{{ $t('vehicle.firstRegistration') }}</label>
          <input id="first" v-model="form.firstRegistrationDate" type="date" class="input" required
            :aria-invalid="!!err('firstRegistrationDate')">
          <span v-if="err('firstRegistrationDate')" class="field-error">{{ err('firstRegistrationDate') }}</span>
        </div>
        <div class="field">
          <label for="acq">{{ $t('vehicle.acquisition') }}</label>
          <select id="acq" v-model="form.acquisition" class="select" :disabled="edit">
            <option value="LEASE">{{ f.label('Acquisition', 'LEASE') }}</option>
            <option value="OWNED">{{ f.label('Acquisition', 'OWNED') }}</option>
          </select>
        </div>
      </div>
    </section>

    <section class="card">
      <header class="card-header"><h2>{{ $t('vehicle.technical') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="make">{{ $t('vehicle.make') }}</label>
          <input id="make" v-model="form.make" class="input" required :aria-invalid="!!err('make')">
          <span v-if="err('make')" class="field-error">{{ err('make') }}</span>
        </div>
        <div class="field">
          <label for="model">{{ $t('vehicle.model') }}</label>
          <input id="model" v-model="form.model" class="input" required :aria-invalid="!!err('model')">
          <span v-if="err('model')" class="field-error">{{ err('model') }}</span>
        </div>
        <div class="field">
          <label for="trim">{{ $t('vehicle.trimLevel') }}</label>
          <input id="trim" v-model="form.trimLevel" class="input">
        </div>
        <div class="field">
          <label for="category">{{ $t('vehicle.category') }}</label>
          <select id="category" v-model="form.category" class="select">
            <option v-for="c in ['CAR', 'VAN', 'TRUCK', 'MACHINE']" :key="c" :value="c">{{ f.label('VehicleCategoryLong', c) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="energy">{{ $t('vehicle.energy') }}</label>
          <select id="energy" v-model="form.energy" class="select">
            <option v-for="e in ['ELECTRIC', 'HYBRID', 'PLUG_IN_HYBRID', 'PETROL', 'DIESEL', 'LPG', 'CNG', 'HYDROGEN']" :key="e" :value="e">
              {{ f.label('Energy', e) }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="critair">{{ $t('vehicle.critAir') }}</label>
          <select id="critair" v-model.number="form.critAir" class="select" :disabled="zeroEmission">
            <option v-for="n in [0, 1, 2, 3, 4, 5]" :key="n" :value="n">{{ n === 0 ? 'E' : n }}</option>
          </select>
        </div>
        <div class="field">
          <label for="co2">{{ $t('vehicle.co2') }} (g/km)</label>
          <input id="co2" v-model.number="form.co2GramsPerKm" type="number" min="0" class="input" :disabled="zeroEmission"
            :aria-invalid="!!err('co2GramsPerKm')">
          <span v-if="err('co2GramsPerKm')" class="field-error">{{ err('co2GramsPerKm') }}</span>
        </div>
        <div class="field">
          <label for="cv">{{ $t('vehicle.fiscalPower') }}</label>
          <input id="cv" v-model.number="form.fiscalPower" type="number" min="1" max="99" class="input">
        </div>
        <div v-if="!edit" class="field">
          <label for="mileage">{{ $t('vehicle.mileage') }}</label>
          <input id="mileage" v-model.number="form.mileage" type="number" min="0" class="input">
        </div>
      </div>
    </section>

    <section class="card">
      <header class="card-header"><h2>{{ $t('vehicle.fleet') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="company">{{ $t('vehicle.company') }}</label>
          <select id="company" v-model="form.companyId" class="select" :disabled="edit || !session.isStaff">
            <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="field">
          <label for="site">{{ $t('vehicle.site') }}</label>
          <select id="site" v-model="form.siteId" class="select">
            <option value="">-</option>
            <option v-for="s in options.data.value?.company?.sites ?? []" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </div>
        <div class="field">
          <label for="cc">{{ $t('vehicle.costCenter') }}</label>
          <select id="cc" v-model="form.costCenterId" class="select">
            <option value="">-</option>
            <option v-for="c in options.data.value?.company?.costCenters ?? []" :key="c.id" :value="c.id">{{ c.code }} · {{ c.label }}</option>
          </select>
        </div>
        <div v-if="!edit" class="field">
          <label for="status">{{ $t('vehicle.status') }}</label>
          <select id="status" v-model="form.status" class="select">
            <option value="ACTIVE">{{ f.label('VehicleStatus', 'ACTIVE') }}</option>
            <option value="AWAITING_DELIVERY">{{ f.label('VehicleStatus', 'AWAITING_DELIVERY') }}</option>
          </select>
        </div>
      </div>
    </section>

    <div class="row form-actions">
      <NuxtLink :to="cancelTo" class="btn">{{ $t('app.cancel') }}</NuxtLink>
      <button type="submit" class="btn btn-primary" :disabled="running">{{ submitLabel }}</button>
    </div>
  </form>
</template>

<style scoped>
.form-actions {
  justify-content: flex-end;
}
</style>
