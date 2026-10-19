<script setup lang="ts">
import { AQ, MANAGED_ROLES } from '~/graphql/admin'
import { Q, type Company, type DriverSummary, type Page } from '~/graphql/fleet'
import { QQ, type GarageSummary } from '~/graphql/quotes'

/** The fields of an account; what it is attached to depends on the role. */
export interface UserFormValue {
  email: string
  firstName: string
  lastName: string
  role: string
  companyId: string
  driverId: string
  garageId: string
  lessor: string
  enabled: boolean
}

const props = defineProps<{ fieldErrors: Record<string, string>; formId: string; edit?: boolean }>()
const form = defineModel<UserFormValue>({ required: true })
const emit = defineEmits<{ submit: [] }>()
const { t } = useI18n()

const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const { data: lessorData } = useGraphqlQuery<{ lessors: string[] }>(AQ.lessors, {}, { cacheKey: 'lessors' })

// Drivers of the company, searched by name, for a driver account
const driverQuery = ref('')
const drivers = useGraphqlQuery<{ drivers: Page<DriverSummary> }>(Q.drivers, () => ({
  filter: compact({ companyId: form.value.companyId || '00000000-0000-0000-0000-000000000000', active: true,
    q: driverQuery.value }),
  size: 30, sort: ['lastName,asc'],
}))
// Garages of the network, searched by name or city, for a garage account
const garageQuery = ref('')
const garages = useGraphqlQuery<{ garages: Page<GarageSummary> }>(QQ.garages, () => ({
  filter: compact({ q: garageQuery.value }), size: 30, sort: ['name,asc'],
}))

const needsCompany = computed(() => form.value.role === 'client-manager' || form.value.role === 'driver')
watch(() => form.value.role, () => {
  if (!needsCompany.value) form.value.companyId = ''
  if (form.value.role !== 'driver') form.value.driverId = ''
  if (form.value.role !== 'garage') form.value.garageId = ''
  if (form.value.role !== 'api-supplier') form.value.lessor = ''
})
watch(() => form.value.companyId, (now, before) => {
  if (before !== undefined && now !== before) form.value.driverId = ''
})
const roleHint = computed(() => t(`users.roleHint.${form.value.role}`))
const err = (field: string) => props.fieldErrors[field]
</script>

<template>
  <form :id="formId" class="stack" @submit.prevent="emit('submit')">
    <div class="grid grid-2">
      <div class="field">
        <label for="u-first">{{ $t('users.firstName') }}</label>
        <input id="u-first" v-model="form.firstName" class="input" required maxlength="60" :aria-invalid="!!err('firstName')">
        <span v-if="err('firstName')" class="field-error">{{ err('firstName') }}</span>
      </div>
      <div class="field">
        <label for="u-last">{{ $t('users.lastName') }}</label>
        <input id="u-last" v-model="form.lastName" class="input" required maxlength="60" :aria-invalid="!!err('lastName')">
        <span v-if="err('lastName')" class="field-error">{{ err('lastName') }}</span>
      </div>
      <div class="field wide">
        <label for="u-email">{{ $t('users.email') }}</label>
        <input id="u-email" v-model="form.email" type="email" class="input" required maxlength="160" :aria-invalid="!!err('email')">
        <span v-if="err('email')" class="field-error">{{ err('email') }}</span>
        <span v-else-if="!edit" class="field-hint">{{ $t('users.emailHint') }}</span>
      </div>
      <div class="field wide">
        <label for="u-role">{{ $t('users.role') }}</label>
        <select id="u-role" v-model="form.role" class="select">
          <option v-for="r in MANAGED_ROLES" :key="r" :value="r">{{ $t(`roles.${r}`) }}</option>
        </select>
        <span class="field-hint">{{ roleHint }}</span>
      </div>

      <div v-if="needsCompany" class="field" :class="{ wide: form.role !== 'driver' }">
        <label for="u-company">{{ $t('users.company') }}</label>
        <select id="u-company" v-model="form.companyId" class="select" required :aria-invalid="!!err('companyId')">
          <option value="" disabled>{{ $t('users.pickCompany') }}</option>
          <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <div v-if="form.role === 'driver'" class="field">
        <label for="u-driver">{{ $t('users.driver') }}</label>
        <input v-model="driverQuery" class="input search" :placeholder="$t('users.searchDriver')" :aria-label="$t('users.searchDriver')"
          :disabled="!form.companyId">
        <select id="u-driver" v-model="form.driverId" class="select" required :disabled="!form.companyId">
          <option value="" disabled>{{ $t('users.pickDriver') }}</option>
          <option v-for="d in drivers.data.value?.drivers.content ?? []" :key="d.id" :value="d.id">
            {{ d.lastName }} {{ d.firstName }} · {{ d.employeeNumber }}
          </option>
        </select>
      </div>
      <div v-if="form.role === 'garage'" class="field wide">
        <label for="u-garage">{{ $t('users.garage') }}</label>
        <input v-model="garageQuery" class="input search" :placeholder="$t('users.searchGarage')" :aria-label="$t('users.searchGarage')">
        <select id="u-garage" v-model="form.garageId" class="select" required>
          <option value="" disabled>{{ $t('users.pickGarage') }}</option>
          <option v-for="g in garages.data.value?.garages.content ?? []" :key="g.id" :value="g.id">{{ g.name }} · {{ g.city }}</option>
        </select>
      </div>
      <div v-if="form.role === 'api-supplier'" class="field wide">
        <label for="u-lessor">{{ $t('users.lessor') }}</label>
        <select id="u-lessor" v-model="form.lessor" class="select" required>
          <option value="" disabled>{{ $t('users.pickLessor') }}</option>
          <option v-for="l in lessorData?.lessors ?? []" :key="l" :value="l">{{ l }}</option>
        </select>
      </div>

      <div class="field wide">
        <label class="checkbox"><input id="u-enabled" v-model="form.enabled" type="checkbox"> {{ $t('users.enabledLabel') }}</label>
      </div>
    </div>
  </form>
</template>

<style scoped>
.wide {
  grid-column: 1 / -1;
}

.search {
  margin-bottom: 6px;
}
</style>
