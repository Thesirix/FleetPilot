<script setup lang="ts">
import { CloudOff, LocateFixed, Phone } from 'lucide-vue-next'
import { DM } from '~/graphql/driver'
import { Q, type DriverFile, type Vehicle } from '~/graphql/fleet'
import { newId, newItem, type OutboxPhoto } from '~/utils/outbox'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('me.declareIncident')} · FleetPilot` })

const space = useGraphqlQuery<{ mySpace: { driver: DriverFile; vehicle: Vehicle | null } }>(Q.mySpace, {}, { cacheKey: 'my-space' })
const vehicle = computed(() => space.data.value?.mySpace.vehicle ?? null)
const incidents = computed(() => space.data.value?.mySpace.driver.incidents ?? [])
const outbox = useOutbox()
const waiting = computed(() => outbox.items.value.filter(i => i.kind === 'incident'))

const TYPES = ['COLLISION', 'GLASS_BREAKAGE', 'MECHANICAL_FAILURE', 'VANDALISM', 'WEATHER', 'THEFT', 'FIRE']

function localNow(): string {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  return now.toISOString().slice(0, 16)
}

function blank() {
  return {
    type: 'COLLISION',
    occurredAt: localNow(),
    location: '',
    latitude: null as number | null,
    longitude: null as number | null,
    description: '',
    thirdPartyInvolved: false,
    injuries: false,
  }
}

const form = reactive(blank())
const photos = ref<OutboxPhoto[]>([])
const locating = ref(false)
const valid = computed(() => form.location.trim() && form.description.trim() && form.occurredAt)
const sending = ref(false)

function locate() {
  if (!('geolocation' in navigator)) {
    useToast().error(t('driverApp.incident.noGps'))
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition((position) => {
    form.latitude = Math.round(position.coords.latitude * 1e6) / 1e6
    form.longitude = Math.round(position.coords.longitude * 1e6) / 1e6
    if (!form.location.trim()) {
      form.location = t('driverApp.incident.gpsLocation', { lat: form.latitude.toFixed(5), lon: form.longitude.toFixed(5) })
    }
    locating.value = false
  }, () => {
    locating.value = false
    useToast().error(t('driverApp.incident.gpsRefused'))
  }, { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 })
}

async function declare() {
  sending.value = true
  try {
    const id = newId()
    const input = { ...form, occurredAt: new Date(form.occurredAt).toISOString(), location: form.location.trim(),
      description: form.description.trim() }
    const summary = t('driverApp.outbox.incident', { type: f.label('IncidentType', form.type), date: f.date(input.occurredAt) })
    const item = newItem('incident', summary, DM.declareMyIncidentWithId, { id, input },
      { id, photos: photos.value, photoPath: `/api/v1/me/incidents/${id}/photos` })
    if (await outbox.send(item, t('driverApp.incident.declared'))) {
      Object.assign(form, blank())
      photos.value = []
      space.refresh()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    }
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="stack page split">
    <div class="intro">
      <h1>{{ $t('me.declareIncident') }}</h1>
      <p class="muted small">{{ $t('driverApp.incident.subtitle') }}</p>
    </div>

    <a href="tel:112" class="emergency card">
      <Phone :size="18" />
      <span><strong>{{ $t('driverApp.incident.emergency') }}</strong><span class="xs">{{ $t('driverApp.incident.emergencyHint') }}</span></span>
    </a>

    <UiQueryState :error="space.error.value" :stale="space.stale.value" @retry="space.refresh" />

    <form v-if="vehicle" class="card" @submit.prevent="declare">
      <div class="card-body stack">
        <p class="small secondary"><span class="mono">{{ vehicle.registration }}</span> · {{ vehicle.make }} {{ vehicle.model }}</p>
        <div class="field">
          <label for="type">{{ $t('incident.type') }}</label>
          <select id="type" v-model="form.type" class="select">
            <option v-for="k in TYPES" :key="k" :value="k">{{ f.label('IncidentType', k) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="when">{{ $t('incident.occurredAt') }}</label>
          <input id="when" v-model="form.occurredAt" type="datetime-local" class="input" :max="localNow()" required>
        </div>
        <div class="field">
          <label for="where">{{ $t('incident.location') }}</label>
          <div class="with-button">
            <input id="where" v-model="form.location" class="input" maxlength="200" required
              :placeholder="$t('driverApp.incident.locationHint')">
            <button type="button" class="btn btn-icon" :disabled="locating" :aria-label="$t('driverApp.incident.useGps')"
              :title="$t('driverApp.incident.useGps')" @click="locate">
              <LocateFixed :size="17" :class="{ spin: locating }" />
            </button>
          </div>
          <p v-if="form.latitude !== null" class="field-hint">{{ $t('driverApp.incident.gpsSaved') }}</p>
        </div>
        <div class="field">
          <label for="what">{{ $t('incident.description') }}</label>
          <textarea id="what" v-model="form.description" class="textarea" rows="4" maxlength="2000" required
            :placeholder="$t('driverApp.incident.descriptionHint')" />
        </div>
        <div class="toggles">
          <label class="checkbox"><input v-model="form.thirdPartyInvolved" type="checkbox"> {{ $t('incident.thirdParty') }}</label>
          <label class="checkbox"><input v-model="form.injuries" type="checkbox"> {{ $t('incident.injuries') }}</label>
        </div>
        <div class="field">
          <span class="field-label">{{ $t('driverApp.photos.title') }}</span>
          <PhotoPicker v-model="photos" />
        </div>
        <button type="submit" class="btn btn-primary big-btn" :disabled="!valid || sending">{{ $t('driverApp.incident.send') }}</button>
      </div>
    </form>
    <UiEmpty v-else-if="space.data.value" :text="$t('me.noVehicle')" />

    <section class="card">
      <header class="card-header"><h2>{{ $t('me.myIncidents') }}</h2></header>
      <ul v-if="waiting.length || incidents.length" class="list">
        <li v-for="item in waiting" :key="item.id" class="row-between">
          <span class="small">{{ item.summary }}</span>
          <span class="badge warning"><CloudOff :size="12" /> {{ $t('driverApp.outbox.pending') }}</span>
        </li>
        <li v-for="i in incidents" :key="i.id" class="row-between">
          <span><span class="mono small">{{ i.reference }}</span><span class="muted small"> · {{ f.label('IncidentType', i.type) }} · {{ f.date(i.occurredAt) }}</span></span>
          <FleetBadge type="IncidentStatus" :value="i.status" />
        </li>
      </ul>
      <UiEmpty v-else :text="$t('me.noIncidents')" />
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

.emergency {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-color: var(--danger);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: var(--text-sm);
  text-decoration: none;
}

.emergency span {
  display: flex;
  flex-direction: column;
}

.secondary {
  margin: 0;
}

.with-button {
  display: flex;
  gap: 8px;
}

.with-button .input {
  flex: 1;
  min-width: 0;
}

.with-button .btn {
  flex: none;
  width: 40px;
  height: 38px;
}

.toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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

.badge.warning {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--warning-soft);
  color: var(--warning);
}
</style>
