<script setup lang="ts">
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { Activity, LiveState, TrackPoint } from '~/graphql/telemetry'

/**
 * Map of the connected vehicles (Leaflet, OpenStreetMap tiles, desaturated so that the vehicles stand out). Vehicles are drawn on a
 * canvas: hundreds of markers move every two seconds without touching the DOM. A vehicle with an open alert has a
 * red outline; the selected one is larger and its track of the last hour is drawn.
 */
const props = withDefaults(defineProps<{
  states: LiveState[]
  selectedId?: string | null
  track?: TrackPoint[]
  height?: string
}>(), { selectedId: null, track: () => [], height: '600px' })
const emit = defineEmits<{ select: [vehicleId: string] }>()

const COLORS: Record<Activity, string> = { MOVING: '#12b76a', IDLING: '#f79009', PARKED: '#475467', OFFLINE: '#98a2b3' }
const ALERT_OUTLINE = '#d92d20'

const container = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let renderer: L.Canvas | null = null
let trackLine: L.Polyline | null = null
const markers = new Map<string, L.CircleMarker>()
let fitted = false

function style(state: LiveState): L.CircleMarkerOptions {
  const selected = state.vehicleId === props.selectedId
  return {
    renderer: renderer!,
    radius: selected ? 9 : 6,
    color: state.openAlerts > 0 ? ALERT_OUTLINE : selected ? '#101828' : '#ffffff',
    weight: state.openAlerts > 0 || selected ? 2.5 : 1.5,
    fillColor: COLORS[state.activity],
    fillOpacity: state.activity === 'OFFLINE' ? 0.55 : 0.95,
  }
}

function draw() {
  if (!map) return
  const seen = new Set<string>()
  for (const state of props.states) {
    seen.add(state.vehicleId)
    const position: L.LatLngTuple = [state.latitude, state.longitude]
    let marker = markers.get(state.vehicleId)
    if (!marker) {
      marker = L.circleMarker(position, style(state)).addTo(map)
      marker.on('click', () => emit('select', state.vehicleId))
      markers.set(state.vehicleId, marker)
    } else {
      marker.setLatLng(position)
      marker.setStyle(style(state))
    }
    const label = `<strong>${state.registration}</strong><br>${state.speedKmh} km/h`
    if (marker.getTooltip()) marker.setTooltipContent(label)
    else marker.bindTooltip(label, { direction: 'top', offset: [0, -6], className: 'vehicle-tooltip' })
    if (state.vehicleId === props.selectedId) marker.bringToFront()
  }
  for (const [id, marker] of markers) {
    if (!seen.has(id)) {
      marker.remove()
      markers.delete(id)
    }
  }
  if (!fitted && props.states.length) {
    fit()
  }
}

/** Frames every vehicle and the track; a single vehicle without track is shown at street level. */
function fit() {
  const points = [...props.states.map(s => [s.latitude, s.longitude] as L.LatLngTuple),
    ...props.track.map(p => [p.latitude, p.longitude] as L.LatLngTuple)]
  const bounds = L.latLngBounds(points)
  if (bounds.getNorthEast().distanceTo(bounds.getSouthWest()) < 300) map!.setView(bounds.getCenter(), 14)
  else map!.fitBounds(bounds, { padding: [30, 30], maxZoom: 15 })
  fitted = true
}

function drawTrack() {
  trackLine?.remove()
  trackLine = null
  if (map && props.track.length > 1) {
    trackLine = L.polyline(props.track.map(p => [p.latitude, p.longitude] as L.LatLngTuple),
      { color: '#0f766e', weight: 3, opacity: 0.8, renderer: renderer! }).addTo(map)
    trackLine.bringToBack()
    // a vehicle file opens on its track
    if (props.states.length === 1) fit()
  }
}

/** Centres the map on a vehicle, keeping the zoom when it is already close. */
function focus(vehicleId: string) {
  const state = props.states.find(s => s.vehicleId === vehicleId)
  if (map && state) map.flyTo([state.latitude, state.longitude], Math.max(map.getZoom(), 13), { duration: 0.6 })
}

onMounted(() => {
  renderer = L.canvas({ padding: 0.3 })
  map = L.map(container.value!, { zoomControl: true, attributionControl: true, preferCanvas: true })
    .setView([46.6, 2.4], 6)
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(map)
  draw()
  drawTrack()
})

onBeforeUnmount(() => {
  map?.remove()
  map = null
  markers.clear()
})

watch(() => props.states, draw)
watch(() => props.selectedId, draw)
watch(() => props.track, drawTrack)

defineExpose({ focus })
</script>

<template>
  <div ref="container" class="fleet-map" :style="{ height }" data-cy="fleet-map" />
</template>

<style scoped>
.fleet-map {
  width: 100%;
  border-radius: var(--radius-lg);
  background: #eef0f3;
  z-index: 0;
}

.fleet-map :deep(.vehicle-tooltip) {
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow);
  color: var(--text);
  font-family: var(--font);
  font-size: var(--text-xs);
}

.fleet-map :deep(.leaflet-tile-pane) {
  filter: grayscale(0.9) brightness(1.04) contrast(0.92);
}

.fleet-map :deep(.leaflet-control-attribution) {
  font-size: 10px;
}
</style>
