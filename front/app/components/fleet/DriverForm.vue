<script setup lang="ts">
import { Q, type Company, type Site } from '~/graphql/fleet'

/** The fields of a driver; in edit mode the company is fixed and the driver can be deactivated. */
export interface DriverFormValue {
  companyId: string
  siteId: string
  employeeNumber: string
  firstName: string
  lastName: string
  email: string
  phone: string
  licenseNumber: string
  licenseCategories: string[]
  licenseIssuedOn: string
  licenseExpiresOn: string
  active: boolean
}

const props = defineProps<{
  edit?: boolean
  running?: boolean
  fieldErrors: Record<string, string>
  cancelTo: string
  submitLabel: string
}>()
const form = defineModel<DriverFormValue>({ required: true })
const emit = defineEmits<{ submit: [] }>()

const session = useSessionStore()
const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
watch(() => companies.value?.companies, (list) => {
  if (list?.length && !form.value.companyId) form.value.companyId = session.viewer?.companyId ?? list[0]!.id
}, { immediate: true })
const options = useGraphqlQuery<{ company: { sites: Site[] } | null }>(Q.companyOptions,
  () => ({ id: form.value.companyId || '00000000-0000-0000-0000-000000000000' }))
const err = (field: string) => props.fieldErrors[field]
</script>

<template>
  <form class="stack" @submit.prevent="emit('submit')">
    <section class="card">
      <header class="card-header"><h2>{{ $t('vehicle.identity') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="fn">{{ $t('driver.firstName') }}</label>
          <input id="fn" v-model="form.firstName" class="input" required :aria-invalid="!!err('firstName')">
          <span v-if="err('firstName')" class="field-error">{{ err('firstName') }}</span>
        </div>
        <div class="field">
          <label for="ln">{{ $t('driver.lastName') }}</label>
          <input id="ln" v-model="form.lastName" class="input" required :aria-invalid="!!err('lastName')">
          <span v-if="err('lastName')" class="field-error">{{ err('lastName') }}</span>
        </div>
        <div class="field">
          <label for="email">{{ $t('driver.email') }}</label>
          <input id="email" v-model="form.email" type="email" class="input" required :aria-invalid="!!err('email')">
          <span v-if="err('email')" class="field-error">{{ err('email') }}</span>
        </div>
        <div class="field">
          <label for="phone">{{ $t('driver.phone') }}</label>
          <input id="phone" v-model="form.phone" class="input" :aria-invalid="!!err('phone')">
          <span v-if="err('phone')" class="field-error">{{ err('phone') }}</span>
        </div>
        <div class="field">
          <label for="num">{{ $t('driver.employeeNumber') }}</label>
          <input id="num" v-model="form.employeeNumber" class="input mono" required :aria-invalid="!!err('employeeNumber')">
          <span v-if="err('employeeNumber')" class="field-error">{{ err('employeeNumber') }}</span>
        </div>
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
        <div v-if="edit" class="field">
          <span class="field-label">{{ $t('driver.status') }}</span>
          <label class="checkbox switch-line">
            <input id="active" v-model="form.active" type="checkbox"> {{ $t('driver.activeLabel') }}
          </label>
          <span class="field-hint">{{ $t('driver.activeHint') }}</span>
        </div>
      </div>
    </section>
    <section class="card">
      <header class="card-header"><h2>{{ $t('driver.license') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="lic">{{ $t('driver.licenseNumber') }}</label>
          <input id="lic" v-model="form.licenseNumber" class="input mono" required :aria-invalid="!!err('licenseNumber')">
          <span v-if="err('licenseNumber')" class="field-error">{{ err('licenseNumber') }}</span>
        </div>
        <div class="field">
          <span class="field-label">{{ $t('driver.licenseCategories') }}</span>
          <div class="row wrap categories">
            <label v-for="c in ['B', 'BE', 'C1', 'C', 'CE', 'D']" :key="c" class="checkbox">
              <input v-model="form.licenseCategories" type="checkbox" :value="c"> {{ c }}
            </label>
          </div>
          <span v-if="err('licenseCategories')" class="field-error">{{ err('licenseCategories') }}</span>
        </div>
        <div class="field">
          <label for="issued">{{ $t('driver.licenseIssuedOn') }}</label>
          <input id="issued" v-model="form.licenseIssuedOn" type="date" class="input" required :aria-invalid="!!err('licenseIssuedOn')">
          <span v-if="err('licenseIssuedOn')" class="field-error">{{ err('licenseIssuedOn') }}</span>
        </div>
        <div class="field">
          <label for="exp">{{ $t('driver.licenseExpiresOn') }}</label>
          <input id="exp" v-model="form.licenseExpiresOn" type="date" class="input" :aria-invalid="!!err('licenseExpiresOn')">
          <span v-if="err('licenseExpiresOn')" class="field-error">{{ err('licenseExpiresOn') }}</span>
        </div>
      </div>
    </section>
    <div class="row actions">
      <NuxtLink :to="cancelTo" class="btn">{{ $t('app.cancel') }}</NuxtLink>
      <button type="submit" class="btn btn-primary" :disabled="running">{{ submitLabel }}</button>
    </div>
  </form>
</template>

<style scoped>
.actions {
  justify-content: flex-end;
}

.categories {
  gap: 14px;
  min-height: 34px;
}

.switch-line {
  min-height: 34px;
}
</style>
