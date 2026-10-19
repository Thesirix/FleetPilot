<script setup lang="ts">
import { M, Q, type Vehicle } from '~/graphql/fleet'
import type { VehicleFormValue } from '~/components/fleet/VehicleForm.vue'

const route = useRoute()
const { t } = useI18n()
const id = route.params.id as string
const back = `/vehicules/${id}`

const { data, error, pending, refresh } = useGraphqlQuery<{ vehicle: Vehicle | null }>(Q.vehicle, { id })
useHead({ title: () => `${t('vehicle.editTitle', { registration: data.value?.vehicle?.registration ?? '' })} · FleetPilot` })

const form = ref<VehicleFormValue | null>(null)
watch(() => data.value?.vehicle, (v) => {
  if (!v || form.value) return
  form.value = {
    companyId: v.company.id, siteId: v.site?.id ?? '', costCenterId: v.costCenter?.id ?? '', registration: v.registration,
    vin: v.vin, category: v.category, make: v.make, model: v.model, trimLevel: v.trimLevel ?? '', energy: v.energy,
    critAir: v.critAir, co2GramsPerKm: v.co2GramsPerKm, fiscalPower: v.fiscalPower ?? null,
    firstRegistrationDate: v.firstRegistrationDate, acquisition: v.acquisition, mileage: v.mileage, status: v.status,
  }
}, { immediate: true })

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const v = form.value!
  const input = compact({
    siteId: v.siteId, costCenterId: v.costCenterId, registration: v.registration, category: v.category, make: v.make,
    model: v.model, trimLevel: v.trimLevel, energy: v.energy, critAir: Number(v.critAir),
    co2GramsPerKm: Number(v.co2GramsPerKm), fiscalPower: v.fiscalPower ? Number(v.fiscalPower) : null,
    firstRegistrationDate: v.firstRegistrationDate, version: data.value!.vehicle!.version,
  })
  const done = await run(M.updateVehicle, { id, input }, t('vehicle.updated', { registration: v.registration }))
  if (done) await navigateTo(back)
}
</script>

<template>
  <div class="narrow">
    <UiQueryState :error="error" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !data?.vehicle" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="form">
      <UiPageHeader :title="$t('vehicle.editTitle', { registration: data?.vehicle?.registration })"
        :subtitle="$t('vehicle.editSubtitle')" :back="back" />
      <VehicleForm v-model="form" edit :running="running" :field-errors="fieldErrors" :cancel-to="back"
        :submit-label="$t('app.save')" @submit="submit" />
    </template>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
