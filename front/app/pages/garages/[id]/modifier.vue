<script setup lang="ts">
import { QM, QQ, type Garage } from '~/graphql/quotes'
import type { GarageFormValue } from '~/components/quotes/GarageForm.vue'

const route = useRoute()
const { t } = useI18n()
const id = route.params.id as string
const back = `/garages/${id}`

const { data, error, pending, refresh } = useGraphqlQuery<{ garage: Garage | null }>(QQ.garage, { id })
useHead({ title: () => `${t('garage.editTitle', { name: data.value?.garage?.name ?? '' })} · FleetPilot` })

const form = ref<GarageFormValue | null>(null)
watch(() => data.value?.garage, (g) => {
  if (!g || form.value) return
  form.value = {
    name: g.name, network: g.network, brand: g.brand ?? '', siret: g.siret, address: g.address, postalCode: g.postalCode,
    city: g.city, latitude: g.latitude ?? null, longitude: g.longitude ?? null, email: g.email, phone: g.phone ?? '',
    hourlyRate: g.hourlyRate,
  }
}, { immediate: true })

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const done = await run(QM.updateGarage, { id, input: compact({ ...form.value! }) },
    t('garage.updated', { name: form.value!.name }))
  if (done) await navigateTo(back)
}
</script>

<template>
  <div class="narrow">
    <UiQueryState :error="error" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !data?.garage" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="form">
      <UiPageHeader :title="$t('garage.editTitle', { name: data?.garage?.name })" :back="back" />
      <GarageForm v-model="form" :running="running" :field-errors="fieldErrors" :cancel-to="back"
        :submit-label="$t('app.save')" @submit="submit" />
    </template>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
