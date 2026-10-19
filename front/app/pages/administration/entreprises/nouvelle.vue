<script setup lang="ts">
import { AM } from '~/graphql/admin'
import type { CompanyFormValue } from '~/components/admin/CompanyForm.vue'

const { t } = useI18n()
useHead({ title: () => `${t('companies.new')} · FleetPilot` })

const form = ref<CompanyFormValue>({
  name: '', legalName: '', siren: '', segment: 'SME', address: '', postalCode: '', city: '', contactName: '',
  contactEmail: '', contactPhone: '',
})
const { run, running, fieldErrors } = useMutation()
async function submit() {
  const result = await run<{ createCompany: { id: string; name: string } }>(AM.createCompany,
    { input: compact({ ...form.value }) })
  if (result) {
    useToast().success(t('companies.created', { name: result.createCompany.name }))
    await navigateTo(`/administration/entreprises/${result.createCompany.id}`)
  }
}
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('companies.new')" :subtitle="$t('companies.newSubtitle')" back="/administration/entreprises" />
    <section class="card">
      <div class="card-body">
        <CompanyForm v-model="form" form-id="company-form" :field-errors="fieldErrors" @submit="submit" />
      </div>
    </section>
    <div class="row actions">
      <NuxtLink to="/administration/entreprises" class="btn">{{ $t('app.cancel') }}</NuxtLink>
      <button type="submit" form="company-form" class="btn btn-primary" :disabled="running">{{ $t('app.create') }}</button>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 880px;
}

.actions {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
