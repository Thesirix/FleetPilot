<script setup lang="ts">
import { M } from '~/graphql/fleet'

/** Records the lease contract of a vehicle: lessor, number, duration, mileage and rent. */
const props = defineProps<{ vehicleId: string; registration: string }>()
const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ created: [id: string] }>()
const { t } = useI18n()
const f = useFormat()
const { run, running, fieldErrors } = useMutation()

const LESSORS = ['Ayvens', 'Arval', 'Leasys', 'Alphabet', 'Free2Move Lease', 'Athlon']
const form = reactive({
  lessor: 'Ayvens', contractNumber: '', startDate: new Date().toISOString().slice(0, 10), durationMonths: 36,
  contractualMileage: 90000, monthlyRent: 450, maintenanceIncluded: true, tyresIncluded: false, replacementVehicle: false,
})
const totalRent = computed(() => Number(form.monthlyRent) * Number(form.durationMonths))

async function submit() {
  const input = { ...form, durationMonths: Number(form.durationMonths), contractualMileage: Number(form.contractualMileage),
    monthlyRent: Number(form.monthlyRent) }
  const result = await run<{ createContract: { id: string; contractNumber: string } }>(M.createContract,
    { vehicleId: props.vehicleId, input })
  if (result) {
    useToast().success(t('contract.created', { number: result.createContract.contractNumber, registration: props.registration }))
    open.value = false
    emit('created', result.createContract.id)
  }
}
const err = (field: string) => fieldErrors.value[field]
</script>

<template>
  <UiModal v-model:open="open" :title="$t('contract.newTitle', { registration })" width="620px">
    <form id="contract-form" class="grid grid-2" @submit.prevent="submit">
      <div class="field">
        <label for="lessor">{{ $t('contract.lessor') }}</label>
        <select id="lessor" v-model="form.lessor" class="select">
          <option v-for="l in LESSORS" :key="l" :value="l">{{ l }}</option>
        </select>
      </div>
      <div class="field">
        <label for="number">{{ $t('contract.number') }}</label>
        <input id="number" v-model="form.contractNumber" class="input mono" required placeholder="AYV-2026-112000"
          :aria-invalid="!!err('contractNumber')">
        <span v-if="err('contractNumber')" class="field-error">{{ err('contractNumber') }}</span>
      </div>
      <div class="field">
        <label for="start">{{ $t('contract.startDate') }}</label>
        <input id="start" v-model="form.startDate" type="date" class="input" required :aria-invalid="!!err('startDate')">
        <span v-if="err('startDate')" class="field-error">{{ err('startDate') }}</span>
      </div>
      <div class="field">
        <label for="months">{{ $t('contract.durationMonths') }}</label>
        <input id="months" v-model.number="form.durationMonths" type="number" min="6" max="84" class="input" required
          :aria-invalid="!!err('durationMonths')">
        <span v-if="err('durationMonths')" class="field-error">{{ err('durationMonths') }}</span>
      </div>
      <div class="field">
        <label for="km">{{ $t('contract.contractualMileage') }}</label>
        <input id="km" v-model.number="form.contractualMileage" type="number" min="1000" step="1000" class="input" required
          :aria-invalid="!!err('contractualMileage')">
        <span v-if="err('contractualMileage')" class="field-error">{{ err('contractualMileage') }}</span>
      </div>
      <div class="field">
        <label for="rent">{{ $t('contract.monthlyRent') }}</label>
        <input id="rent" v-model.number="form.monthlyRent" type="number" min="0" step="0.01" class="input" required
          :aria-invalid="!!err('monthlyRent')">
        <span class="field-hint">{{ $t('contract.totalRentHint', { total: f.money(totalRent, 2) }) }}</span>
      </div>
      <fieldset class="services">
        <legend>{{ $t('contract.services') }}</legend>
        <label class="check"><input v-model="form.maintenanceIncluded" type="checkbox"> {{ $t('contract.maintenance') }}</label>
        <label class="check"><input v-model="form.tyresIncluded" type="checkbox"> {{ $t('contract.tyres') }}</label>
        <label class="check"><input v-model="form.replacementVehicle" type="checkbox"> {{ $t('contract.replacement') }}</label>
      </fieldset>
    </form>
    <template #footer>
      <button class="btn" @click="open = false">{{ $t('app.cancel') }}</button>
      <button type="submit" form="contract-form" class="btn btn-primary" :disabled="running">{{ $t('app.create') }}</button>
    </template>
  </UiModal>
</template>

<style scoped>
.services {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  border: 0;
  padding: 0;
  margin: 0;
}

.services legend {
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
</style>
