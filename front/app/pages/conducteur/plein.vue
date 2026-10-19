<script setup lang="ts">
import { CloudOff, Fuel, Zap } from 'lucide-vue-next'
import { DM, DQ, type FuelEntry, type FuelKind, type FuelPayment } from '~/graphql/driver'
import { Q, type Vehicle } from '~/graphql/fleet'
import { newId, newItem } from '~/utils/outbox'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('driverApp.fuel.title')} · FleetPilot` })

const space = useGraphqlQuery<{ mySpace: { vehicle: Vehicle | null } }>(Q.mySpace, {}, { cacheKey: 'my-space' })
const vehicle = computed(() => space.data.value?.mySpace.vehicle ?? null)
const history = useGraphqlQuery<{ myFuelEntries: FuelEntry[] }>(DQ.myFuelEntries, {}, { cacheKey: 'my-fuel-entries' })
const outbox = useOutbox()
const waiting = computed(() => outbox.items.value.filter(i => i.kind === 'fuel'))

/** Which kinds the vehicle takes: an electric car only charges, a plug-in hybrid does both. */
const kinds = computed<FuelKind[]>(() => {
  switch (vehicle.value?.energy) {
    case 'ELECTRIC': return ['CHARGE']
    case 'PLUG_IN_HYBRID': return ['FUEL', 'CHARGE']
    default: return ['FUEL']
  }
})

function localNow(): string {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
}

const form = reactive({
  kind: 'FUEL' as FuelKind,
  filledAt: localNow(),
  quantity: '' as string | number,
  amount: '' as string | number,
  mileage: '' as string | number,
  station: '',
  payment: 'FUEL_CARD' as FuelPayment,
})

watch(vehicle, (value) => {
  if (value) {
    form.kind = kinds.value[0]!
    if (form.mileage === '') {
      form.mileage = value.mileage
    }
  }
}, { immediate: true })

const unit = computed(() => (form.kind === 'CHARGE' ? 'kWh' : 'L'))
const unitPrice = computed(() => {
  const quantity = Number(form.quantity)
  const amount = Number(form.amount)
  return quantity > 0 && amount >= 0 && form.amount !== '' ? amount / quantity : null
})
/** Same bounds as the server: a typo in the quantity is caught before sending. */
const implausible = computed(() => {
  const price = unitPrice.value
  if (price === null) {
    return false
  }
  return form.kind === 'CHARGE' ? price > 1.5 : price < 0.5 || price > 4
})
const valid = computed(() => Number(form.quantity) > 0 && form.amount !== '' && Number(form.amount) >= 0
  && form.mileage !== '' && Number(form.mileage) >= 0 && !implausible.value)

const sending = ref(false)

async function save() {
  sending.value = true
  try {
    const quantity = Number(form.quantity)
    const input = {
      filledAt: new Date(form.filledAt).toISOString(),
      kind: form.kind,
      quantity,
      amount: Number(form.amount),
      mileage: Number(form.mileage),
      station: form.station || null,
      payment: form.payment,
    }
    const summary = t('driverApp.outbox.fuel', { date: f.date(input.filledAt), quantity: f.number(quantity, 2), unit: unit.value })
    const id = newId()
    const item = newItem('fuel', summary, DM.recordMyFuel, { id, input }, { id })
    if (await outbox.send(item, t('driverApp.fuel.saved'))) {
      Object.assign(form, { quantity: '', amount: '', station: '', filledAt: localNow() })
      history.refresh()
      space.refresh()
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="stack page split">
    <div class="intro">
      <h1>{{ $t('driverApp.fuel.title') }}</h1>
      <p class="muted small">{{ $t('driverApp.fuel.subtitle') }}</p>
    </div>
    <UiQueryState :error="space.error.value" :stale="space.stale.value" @retry="space.refresh" />

    <form v-if="vehicle" class="card" @submit.prevent="save">
      <div class="card-body stack">
        <div v-if="kinds.length > 1" class="segmented" role="radiogroup" :aria-label="$t('driverApp.fuel.kind')">
          <button v-for="k in kinds" :key="k" type="button" role="radio" :aria-checked="form.kind === k"
            :class="{ on: form.kind === k }" @click="form.kind = k">
            <component :is="k === 'CHARGE' ? Zap : Fuel" :size="16" /> {{ $t(`driverApp.fuel.kinds.${k}`) }}
          </button>
        </div>
        <p v-else class="vehicle-line small">
          <component :is="form.kind === 'CHARGE' ? Zap : Fuel" :size="15" />
          <span class="mono">{{ vehicle.registration }}</span> · {{ $t(`driverApp.fuel.kinds.${form.kind}`) }}
        </p>

        <div class="grid grid-2 tight">
          <div class="field">
            <label for="quantity">{{ $t('driverApp.fuel.quantity', { unit }) }}</label>
            <input id="quantity" v-model="form.quantity" type="number" inputmode="decimal" step="0.01" min="0" class="input" required>
          </div>
          <div class="field">
            <label for="amount">{{ $t('driverApp.fuel.amount') }}</label>
            <input id="amount" v-model="form.amount" type="number" inputmode="decimal" step="0.01" min="0" class="input" required>
          </div>
        </div>
        <p v-if="unitPrice !== null" class="price small" :class="{ bad: implausible }">
          {{ implausible ? $t('driverApp.fuel.implausible', { price: f.number(unitPrice, 3), unit }) : $t('driverApp.fuel.unitPrice', { price: f.number(unitPrice, 3), unit }) }}
        </p>

        <div class="grid grid-2 tight">
          <div class="field">
            <label for="filledAt">{{ $t('driverApp.fuel.when') }}</label>
            <input id="filledAt" v-model="form.filledAt" type="datetime-local" class="input" required>
          </div>
          <div class="field">
            <label for="odometer">{{ $t('vehicle.mileage') }}</label>
            <input id="odometer" v-model="form.mileage" type="number" inputmode="numeric" min="0" class="input" required>
          </div>
        </div>

        <div class="field">
          <label for="station">{{ $t('driverApp.fuel.station') }}</label>
          <input id="station" v-model="form.station" class="input" maxlength="120" :placeholder="$t('driverApp.fuel.stationHint')">
        </div>

        <div class="field">
          <span class="field-label">{{ $t('driverApp.fuel.payment') }}</span>
          <div class="segmented" role="radiogroup" :aria-label="$t('driverApp.fuel.payment')">
            <button v-for="p in (['FUEL_CARD', 'PERSONAL'] as const)" :key="p" type="button" role="radio"
              :aria-checked="form.payment === p" :class="{ on: form.payment === p }" @click="form.payment = p">
              {{ $t(`driverApp.fuel.payments.${p}`) }}
            </button>
          </div>
          <p v-if="form.payment === 'PERSONAL'" class="field-hint">{{ $t('driverApp.fuel.personalHint') }}</p>
        </div>

        <button type="submit" class="btn btn-primary big-btn" :disabled="!valid || sending">{{ $t('driverApp.fuel.save') }}</button>
      </div>
    </form>
    <UiEmpty v-else-if="space.data.value" :text="$t('me.noVehicle')" />

    <section class="card">
      <header class="card-header"><h2>{{ $t('driverApp.fuel.history') }}</h2></header>
      <ul v-if="waiting.length || history.data.value?.myFuelEntries.length" class="list">
        <li v-for="item in waiting" :key="item.id" class="row-between">
          <span class="small">{{ item.summary }}</span>
          <span class="badge warning"><CloudOff :size="12" /> {{ $t('driverApp.outbox.pending') }}</span>
        </li>
        <li v-for="e in history.data.value?.myFuelEntries ?? []" :key="e.id" class="row-between">
          <div>
            <p class="small"><strong class="num">{{ f.number(e.quantity, 2) }} {{ e.kind === 'CHARGE' ? 'kWh' : 'L' }}</strong>
              <span class="muted"> · {{ f.dateTime(e.filledAt) }}</span></p>
            <p class="xs muted">{{ e.station ?? $t('driverApp.fuel.noStation') }} · {{ f.km(e.mileage) }}</p>
          </div>
          <div class="right">
            <p class="small num strong">{{ f.money(e.amount, 2) }}</p>
            <p class="xs muted">{{ $t(`driverApp.fuel.payments.${e.payment}`) }}</p>
          </div>
        </li>
      </ul>
      <UiEmpty v-else :text="$t('driverApp.fuel.none')" />
    </section>
  </div>
</template>

<style scoped>
.page {
  gap: 14px;
}

.intro h1 {
  margin: 4px 0 2px;
  font-size: var(--text-xl);
}

.intro p {
  margin: 0;
}

.tight {
  gap: 12px;
}

.vehicle-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--text-secondary);
}

.segmented {
  display: flex;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--neutral-soft);
}

.segmented button {
  display: inline-flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 500;
  cursor: pointer;
}

.segmented button.on {
  background: var(--surface);
  box-shadow: var(--shadow-sm);
  color: var(--text);
}

.price {
  margin: -4px 0 0;
  color: var(--text-secondary);
}

.price.bad {
  color: var(--danger);
}

.big-btn {
  width: 100%;
  height: 44px;
  font-size: var(--text-md);
}

.list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.list li {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
}

.list li:last-child {
  border-bottom: none;
}

.list p {
  margin: 0;
}

.strong {
  font-weight: 600;
}

.badge.warning {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--warning-soft);
  color: var(--warning);
}
</style>
