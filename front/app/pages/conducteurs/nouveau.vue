<script setup lang="ts">
import { M } from '~/graphql/fleet'
import type { DriverFormValue } from '~/components/fleet/DriverForm.vue'

const { t } = useI18n()
useHead({ title: () => `${t('driver.formTitle')} · FleetPilot` })

const form = ref<DriverFormValue>({
  companyId: '', siteId: '', employeeNumber: '', firstName: '', lastName: '', email: '', phone: '',
  licenseNumber: '', licenseCategories: ['B'], licenseIssuedOn: '', licenseExpiresOn: '', active: true,
})

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const { active: _active, ...input } = form.value
  const result = await run<{ createDriver: { driver: { id: string; fullName: string } } }>(M.createDriver,
    { input: compact({ ...input }) })
  if (result) {
    useToast().success(t('driver.created', { name: result.createDriver.driver.fullName }))
    await navigateTo(`/conducteurs/${result.createDriver.driver.id}`)
  }
}
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('driver.formTitle')" back="/conducteurs" />
    <DriverForm v-model="form" :running="running" :field-errors="fieldErrors" cancel-to="/conducteurs"
      :submit-label="$t('app.create')" @submit="submit" />
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
