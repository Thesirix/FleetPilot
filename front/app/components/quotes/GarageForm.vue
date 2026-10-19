<script setup lang="ts">
/** The details of a garage of the network: identity, address (and position for the driver app), contact, rate. */
export interface GarageFormValue {
  name: string
  network: string
  brand: string
  siret: string
  address: string
  postalCode: string
  city: string
  latitude: number | null
  longitude: number | null
  email: string
  phone: string
  hourlyRate: number | null
}

const props = defineProps<{
  running?: boolean
  fieldErrors: Record<string, string>
  cancelTo: string
  submitLabel: string
}>()
const form = defineModel<GarageFormValue>({ required: true })
const emit = defineEmits<{ submit: [] }>()
const f = useFormat()
const err = (field: string) => props.fieldErrors[field]
</script>

<template>
  <form class="stack" @submit.prevent="emit('submit')">
    <section class="card">
      <header class="card-header"><h2>{{ $t('garage.identityTitle') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field">
          <label for="name">{{ $t('garage.name') }}</label>
          <input id="name" v-model="form.name" class="input" required maxlength="120" :aria-invalid="!!err('name')">
          <span v-if="err('name')" class="field-error">{{ err('name') }}</span>
        </div>
        <div class="field">
          <label for="siret">{{ $t('garage.siret') }}</label>
          <input id="siret" v-model="form.siret" class="input mono" required inputmode="numeric" maxlength="14"
            placeholder="12345678900012" :aria-invalid="!!err('siret')">
          <span v-if="err('siret')" class="field-error">{{ err('siret') }}</span>
          <span v-else class="field-hint">{{ $t('garage.siretHint') }}</span>
        </div>
        <div class="field">
          <label for="network">{{ $t('garage.network') }}</label>
          <select id="network" v-model="form.network" class="select">
            <option v-for="n in ['DEALER', 'INDEPENDENT', 'FAST_FIT', 'BODYWORK', 'TRUCK']" :key="n" :value="n">
              {{ f.label('GarageNetwork', n) }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="brand">{{ $t('garage.brand') }}</label>
          <input id="brand" v-model="form.brand" class="input" maxlength="40" :placeholder="$t('garage.brandHint')">
        </div>
        <div class="field">
          <label for="rate">{{ $t('garage.hourlyRateExclVat') }}</label>
          <input id="rate" v-model.number="form.hourlyRate" type="number" min="1" step="0.01" class="input" required
            :aria-invalid="!!err('hourlyRate')">
          <span v-if="err('hourlyRate')" class="field-error">{{ err('hourlyRate') }}</span>
        </div>
      </div>
    </section>

    <section class="card">
      <header class="card-header"><h2>{{ $t('garage.identity') }}</h2></header>
      <div class="card-body grid grid-2">
        <div class="field wide">
          <label for="address">{{ $t('garage.address') }}</label>
          <input id="address" v-model="form.address" class="input" required maxlength="160" :aria-invalid="!!err('address')">
          <span v-if="err('address')" class="field-error">{{ err('address') }}</span>
        </div>
        <div class="field">
          <label for="postal">{{ $t('garage.postalCode') }}</label>
          <input id="postal" v-model="form.postalCode" class="input mono" required inputmode="numeric" maxlength="5"
            :aria-invalid="!!err('postalCode')">
          <span v-if="err('postalCode')" class="field-error">{{ err('postalCode') }}</span>
        </div>
        <div class="field">
          <label for="city">{{ $t('garage.city') }}</label>
          <input id="city" v-model="form.city" class="input" required maxlength="80" :aria-invalid="!!err('city')">
          <span v-if="err('city')" class="field-error">{{ err('city') }}</span>
        </div>
        <div class="field">
          <label for="lat">{{ $t('garage.latitude') }}</label>
          <input id="lat" v-model.number="form.latitude" type="number" step="0.000001" min="-90" max="90" class="input"
            :aria-invalid="!!err('latitude')">
          <span v-if="err('latitude')" class="field-error">{{ err('latitude') }}</span>
        </div>
        <div class="field">
          <label for="lon">{{ $t('garage.longitude') }}</label>
          <input id="lon" v-model.number="form.longitude" type="number" step="0.000001" min="-180" max="180" class="input"
            :aria-invalid="!!err('longitude')">
          <span v-if="err('longitude')" class="field-error">{{ err('longitude') }}</span>
          <span v-else class="field-hint">{{ $t('garage.positionHint') }}</span>
        </div>
        <div class="field">
          <label for="email">{{ $t('garage.email') }}</label>
          <input id="email" v-model="form.email" type="email" class="input" required maxlength="160" :aria-invalid="!!err('email')">
          <span v-if="err('email')" class="field-error">{{ err('email') }}</span>
        </div>
        <div class="field">
          <label for="phone">{{ $t('garage.phone') }}</label>
          <input id="phone" v-model="form.phone" class="input" maxlength="30">
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

.wide {
  grid-column: 1 / -1;
}
</style>
