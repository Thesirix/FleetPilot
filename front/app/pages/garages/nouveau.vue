<script setup lang="ts">
import { QM } from '~/graphql/quotes'
import type { GarageFormValue } from '~/components/quotes/GarageForm.vue'

const { t } = useI18n()
useHead({ title: () => `${t('garage.newTitle')} · FleetPilot` })

const form = ref<GarageFormValue>({
  name: '', network: 'INDEPENDENT', brand: '', siret: '', address: '', postalCode: '', city: '', latitude: null,
  longitude: null, email: '', phone: '', hourlyRate: null,
})

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const result = await run<{ createGarage: { id: string; name: string } }>(QM.createGarage, { input: compact({ ...form.value }) })
  if (result) {
    useToast().success(t('garage.created', { name: result.createGarage.name }))
    await navigateTo(`/garages/${result.createGarage.id}`)
  }
}
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('garage.newTitle')" :subtitle="$t('garage.newSubtitle')" back="/garages" />
    <GarageForm v-model="form" :running="running" :field-errors="fieldErrors" cancel-to="/garages"
      :submit-label="$t('app.create')" @submit="submit" />
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
