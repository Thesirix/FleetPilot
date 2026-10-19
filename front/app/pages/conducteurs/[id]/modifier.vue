<script setup lang="ts">
import { M, Q, type DriverFile } from '~/graphql/fleet'
import type { DriverFormValue } from '~/components/fleet/DriverForm.vue'

const route = useRoute()
const { t } = useI18n()
const id = route.params.id as string
const back = `/conducteurs/${id}`

const { data, error, pending, refresh } = useGraphqlQuery<{ driver: DriverFile | null }>(Q.driver, { id })
useHead({ title: () => `${t('driver.editTitle', { name: data.value?.driver?.driver.fullName ?? '' })} · FleetPilot` })

const form = ref<DriverFormValue | null>(null)
watch(() => data.value?.driver, (file) => {
  if (!file || form.value) return
  const d = file.driver
  form.value = {
    companyId: d.companyId, siteId: d.site?.id ?? '', employeeNumber: d.employeeNumber, firstName: d.firstName,
    lastName: d.lastName, email: d.email, phone: d.phone ?? '', licenseNumber: file.licenseNumber,
    licenseCategories: [...d.licenseCategories], licenseIssuedOn: file.licenseIssuedOn,
    licenseExpiresOn: d.licenseExpiresOn ?? '', active: d.active,
  }
}, { immediate: true })

const { run, running, fieldErrors } = useMutation()
async function submit() {
  const { companyId: _companyId, ...values } = form.value!
  const input = compact({ ...values, version: data.value!.driver!.version })
  const done = await run(M.updateDriver, { id, input }, t('driver.updated', { name: `${values.firstName} ${values.lastName}` }))
  if (done) await navigateTo(back)
}
</script>

<template>
  <div class="narrow">
    <UiQueryState :error="error" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !data?.driver" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="form">
      <UiPageHeader :title="$t('driver.editTitle', { name: data?.driver?.driver.fullName })" :back="back" />
      <DriverForm v-model="form" edit :running="running" :field-errors="fieldErrors" :cancel-to="back"
        :submit-label="$t('app.save')" @submit="submit" />
    </template>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}
</style>
