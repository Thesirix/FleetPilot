<script setup lang="ts">
import { LocateFixed, Navigation, Phone, Star } from 'lucide-vue-next'
import { DQ, type NearbyGarage } from '~/graphql/driver'
import { graphql, GraphqlError } from '~/utils/graphql'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('driverApp.garages.title')} · FleetPilot` })

/** When the phone does not give its position, the driver picks the nearest city among the main ones. */
const CITIES = [
  { name: 'Marseille', latitude: 43.2965, longitude: 5.3698 },
  { name: 'Aix-en-Provence', latitude: 43.5297, longitude: 5.4474 },
  { name: 'Toulon', latitude: 43.1242, longitude: 5.928 },
  { name: 'Nice', latitude: 43.7102, longitude: 7.262 },
  { name: 'Lyon', latitude: 45.764, longitude: 4.8357 },
  { name: 'Paris', latitude: 48.8566, longitude: 2.3522 },
  { name: 'Lille', latitude: 50.6292, longitude: 3.0573 },
  { name: 'Bordeaux', latitude: 44.8378, longitude: -0.5792 },
  { name: 'Nantes', latitude: 47.2184, longitude: -1.5536 },
  { name: 'Toulouse', latitude: 43.6047, longitude: 1.4442 },
  { name: 'Strasbourg', latitude: 48.5734, longitude: 7.7521 },
]

const origin = ref<{ latitude: number; longitude: number; label: string } | null>(null)
const radius = ref(25)
const garages = ref<NearbyGarage[]>([])
const loading = ref(false)
const failure = ref<GraphqlError | null>(null)
const locating = ref(false)
const gpsRefused = ref(false)
const selected = ref<string | null>(null)

async function search() {
  if (!origin.value) return
  loading.value = true
  failure.value = null
  try {
    const { data } = await graphql<{ nearbyGarages: NearbyGarage[] }>(DQ.nearbyGarages,
      { latitude: origin.value.latitude, longitude: origin.value.longitude, radiusKm: radius.value })
    garages.value = data.nearbyGarages
  } catch (e) {
    failure.value = e instanceof GraphqlError ? e : new GraphqlError(String(e), 'ERROR')
  } finally {
    loading.value = false
  }
}

function locate() {
  if (!('geolocation' in navigator)) {
    gpsRefused.value = true
    return
  }
  locating.value = true
  navigator.geolocation.getCurrentPosition((position) => {
    locating.value = false
    gpsRefused.value = false
    origin.value = { latitude: position.coords.latitude, longitude: position.coords.longitude, label: t('driverApp.garages.here') }
  }, () => {
    locating.value = false
    gpsRefused.value = true
  }, { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 })
}

function pickCity(event: Event) {
  const city = CITIES.find(c => c.name === (event.target as HTMLSelectElement).value)
  if (city) origin.value = { latitude: city.latitude, longitude: city.longitude, label: city.name }
}

watch([origin, radius], search)
onMounted(locate)

function directions(g: NearbyGarage): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${g.latitude},${g.longitude}`
}

function select(id: string) {
  selected.value = id
  document.getElementById(`garage-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
</script>

<template>
  <div class="stack page">
    <div class="intro">
      <h1>{{ $t('driverApp.garages.title') }}</h1>
      <p class="muted small">{{ $t('driverApp.garages.subtitle') }}</p>
    </div>

    <div class="card">
      <div class="card-body controls">
        <button class="btn" :disabled="locating" @click="locate">
          <LocateFixed :size="16" /> {{ locating ? $t('driverApp.garages.locating') : $t('driverApp.garages.aroundMe') }}
        </button>
        <select class="select" :aria-label="$t('driverApp.garages.city')" @change="pickCity">
          <option value="">{{ $t('driverApp.garages.orCity') }}</option>
          <option v-for="c in CITIES" :key="c.name" :value="c.name">{{ c.name }}</option>
        </select>
        <select v-model.number="radius" class="select radius" :aria-label="$t('driverApp.garages.radius')">
          <option v-for="r in [10, 25, 50, 100]" :key="r" :value="r">{{ r }} km</option>
        </select>
      </div>
      <p v-if="gpsRefused && !origin" class="card-body hint small muted">{{ $t('driverApp.garages.gpsRefused') }}</p>
    </div>

    <UiQueryState :error="failure" :stale="false" @retry="search" />

    <template v-if="origin">
      <div class="results">
      <div class="map-col">
        <GarageMap :origin="origin" :garages="garages" :selected-id="selected" @select="select" />
      </div>
      <div class="list-col stack">
      <p class="small muted">
        {{ loading ? $t('app.loading') : $t('driverApp.garages.found', { count: garages.length, radius, place: origin.label }) }}
      </p>
      <ol v-if="garages.length" class="garages">
        <li v-for="(g, index) in garages" :id="`garage-${g.id}`" :key="g.id" class="card garage" :class="{ selected: selected === g.id }"
          @click="selected = g.id">
          <div class="card-body">
            <div class="row-between top">
              <div class="who">
                <span class="index">{{ index + 1 }}</span>
                <div>
                  <p class="name">{{ g.name }}</p>
                  <p class="xs muted">{{ f.label('GarageNetwork', g.network) }}<template v-if="g.brand"> · {{ g.brand }}</template></p>
                </div>
              </div>
              <strong class="num distance">{{ f.number(g.distanceKm, 1) }} km</strong>
            </div>
            <p class="small secondary address">{{ g.address }}, {{ g.postalCode }} {{ g.city }}</p>
            <p class="xs muted facts">
              <span v-if="g.rating"><Star :size="12" /> {{ f.number(g.rating, 1) }}</span>
              <span v-if="g.hourlyRate">{{ $t('driverApp.garages.rate', { rate: f.money(g.hourlyRate) }) }}</span>
            </p>
            <div class="grid grid-2 buttons">
              <a v-if="g.phone" :href="`tel:${g.phone.replace(/\s/g, '')}`" class="btn"><Phone :size="15" /> {{ $t('driverApp.garages.call') }}</a>
              <a :href="directions(g)" target="_blank" rel="noopener" class="btn btn-primary" :class="{ wide: !g.phone }">
                <Navigation :size="15" /> {{ $t('driverApp.garages.go') }}
              </a>
            </div>
          </div>
        </li>
      </ol>
      <UiEmpty v-else-if="!loading" :text="$t('driverApp.garages.none')" />
      </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  gap: 14px;
}

.results {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.list-col {
  gap: 10px;
}

.list-col > p {
  margin: 0;
}


.intro h1 {
  margin: 4px 0 2px;
  font-size: var(--text-xl);
}

.intro p {
  margin: 0;
}

.controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  gap: 8px;
}

.controls .btn {
  grid-column: 1 / -1;
  justify-content: center;
}

.hint {
  padding-top: 0;
}

.garages {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.garage.selected {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--ring);
}

.garage p {
  margin: 0;
}

.top {
  align-items: flex-start;
}

.who {
  display: flex;
  gap: 10px;
  min-width: 0;
}

.index {
  display: grid;
  flex: none;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.name {
  font-weight: 600;
  font-size: var(--text-md);
}

.distance {
  flex: none;
  font-size: var(--text-sm);
}

.address {
  margin-top: 8px !important;
}

.facts {
  display: flex;
  gap: 12px;
  margin-top: 4px !important;
}

.facts span {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.buttons {
  gap: 8px;
  margin-top: 12px;
}

.buttons .btn {
  justify-content: center;
  height: 38px;
}

.wide {
  grid-column: 1 / -1;
}

/* On a computer: the map stays in view beside the list */
@media (min-width: 1024px) {
  .results {
    display: grid;
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
    align-items: start;
    gap: 24px;
  }

  .map-col {
    position: sticky;
    top: calc(var(--topbar-height) + 24px);
  }

  .controls {
    grid-template-columns: auto minmax(0, 1fr) 96px;
  }

  .controls .btn {
    grid-column: auto;
  }
}
</style>
