<script setup lang="ts">
import { M } from '~/graphql/fleet'
import type { VehicleFormValue } from '~/components/fleet/VehicleForm.vue'

const { t } = useI18n()
useHead({ title: () => `${t('vehicle.formTitle')} · FleetPilot` })

const form = ref<VehicleFormValue>({
  companyId: '', siteId: '', costCenterId: '', registration: '', vin: '', category: 'CAR', make: '', model: '',
  trimLevel: '', energy: 'ELECTRIC', critAir: 0, co2GramsPerKm: 0, fiscalPower: null,
  firstRegistrationDate: new Date().toISOString().slice(0, 10), acquisition: 'LEASE', mileage: 0, status: 'ACTIVE',
})

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const v = form.value
  const input = compact({ ...v, critAir: Number(v.critAir), co2GramsPerKm: Number(v.co2GramsPerKm),
    mileage: Number(v.mileage), fiscalPower: v.fiscalPower ? Number(v.fiscalPower) : null })
  const result = await run<{ createVehicle: { id: string; registration: string } }>(M.createVehicle, { input })
  if (result) {
    useToast().success(t('vehicle.created', { registration: result.createVehicle.registration }))
    await navigateTo(`/vehicules/${result.createVehicle.id}`)
  }
}
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('vehicle.formTitle')" :subtitle="$t('vehicle.formSubtitle')" back="/vehicules" />
    <VehicleForm v-model="form" :running="running" :field-errors="fieldErrors" cancel-to="/vehicules"
      :submit-label="$t('app.create')" @submit="submit" />
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
