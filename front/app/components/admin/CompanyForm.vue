<script setup lang="ts">
/** Identity, address and contact of a client company. */
export interface CompanyFormValue {
  name: string
  legalName: string
  siren: string
  segment: string
  address: string
  postalCode: string
  city: string
  contactName: string
  contactEmail: string
  contactPhone: string
}

const props = defineProps<{ fieldErrors: Record<string, string>; formId: string }>()
const form = defineModel<CompanyFormValue>({ required: true })
const emit = defineEmits<{ submit: [] }>()
const f = useFormat()
const err = (field: string) => props.fieldErrors[field]
</script>

<template>
  <form :id="formId" class="stack" @submit.prevent="emit('submit')">
    <fieldset class="group">
      <legend>{{ $t('companies.identity') }}</legend>
      <div class="grid grid-2">
        <div class="field">
          <label for="c-name">{{ $t('companies.name') }}</label>
          <input id="c-name" v-model="form.name" class="input" required maxlength="120" :aria-invalid="!!err('name')">
          <span v-if="err('name')" class="field-error">{{ err('name') }}</span>
        </div>
        <div class="field">
          <label for="c-legal">{{ $t('companies.legalName') }}</label>
          <input id="c-legal" v-model="form.legalName" class="input" required maxlength="160" :aria-invalid="!!err('legalName')">
          <span v-if="err('legalName')" class="field-error">{{ err('legalName') }}</span>
        </div>
        <div class="field">
          <label for="c-siren">{{ $t('companies.siren') }}</label>
          <input id="c-siren" v-model="form.siren" class="input mono" required inputmode="numeric" maxlength="9"
            :aria-invalid="!!err('siren')">
          <span v-if="err('siren')" class="field-error">{{ err('siren') }}</span>
          <span v-else class="field-hint">{{ $t('companies.sirenHint') }}</span>
        </div>
        <div class="field">
          <label for="c-segment">{{ $t('companies.segment') }}</label>
          <select id="c-segment" v-model="form.segment" class="select">
            <option v-for="s in ['LARGE_ACCOUNT', 'PUBLIC_SECTOR', 'SME']" :key="s" :value="s">{{ f.label('CompanySegment', s) }}</option>
          </select>
        </div>
      </div>
    </fieldset>

    <fieldset class="group">
      <legend>{{ $t('companies.headOffice') }}</legend>
      <div class="grid grid-2">
        <div class="field wide">
          <label for="c-address">{{ $t('companies.address') }}</label>
          <input id="c-address" v-model="form.address" class="input" maxlength="200">
        </div>
        <div class="field">
          <label for="c-postal">{{ $t('companies.postalCode') }}</label>
          <input id="c-postal" v-model="form.postalCode" class="input mono" inputmode="numeric" maxlength="5"
            :aria-invalid="!!err('postalCode')">
          <span v-if="err('postalCode')" class="field-error">{{ err('postalCode') }}</span>
        </div>
        <div class="field">
          <label for="c-city">{{ $t('companies.city') }}</label>
          <input id="c-city" v-model="form.city" class="input" required maxlength="80" :aria-invalid="!!err('city')">
          <span v-if="err('city')" class="field-error">{{ err('city') }}</span>
        </div>
      </div>
    </fieldset>

    <fieldset class="group">
      <legend>{{ $t('companies.contact') }}</legend>
      <div class="grid grid-3">
        <div class="field">
          <label for="c-contact">{{ $t('companies.contactName') }}</label>
          <input id="c-contact" v-model="form.contactName" class="input" maxlength="120">
        </div>
        <div class="field">
          <label for="c-email">{{ $t('companies.contactEmail') }}</label>
          <input id="c-email" v-model="form.contactEmail" type="email" class="input" maxlength="160"
            :aria-invalid="!!err('contactEmail')">
          <span v-if="err('contactEmail')" class="field-error">{{ err('contactEmail') }}</span>
        </div>
        <div class="field">
          <label for="c-phone">{{ $t('companies.contactPhone') }}</label>
          <input id="c-phone" v-model="form.contactPhone" class="input" maxlength="30">
        </div>
      </div>
    </fieldset>
  </form>
</template>

<style scoped>
.group {
  border: 0;
  padding: 0;
  margin: 0;
}

.group legend {
  margin-bottom: 10px;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-secondary);
}

.wide {
  grid-column: 1 / -1;
}
</style>
