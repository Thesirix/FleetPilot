<script setup lang="ts">
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { NearbyGarage } from '~/graphql/driver'

/** The driver's position and the approved garages around, numbered as in the list below the map. */
const props = defineProps<{ origin: { latitude: number; longitude: number }; garages: NearbyGarage[]; selectedId?: string | null }>()
const emit = defineEmits<{ select: [id: string] }>()

const CLOSEST_FRAMED = 8

const container = ref<HTMLElement | null>(null)
let map: L.Map | null = null
let layer: L.LayerGroup | null = null
const markers = new Map<string, L.Marker>()

function pin(index: number, selected: boolean) {
  return L.divIcon({
    className: '',
    html: `<span class="garage-pin${selected ? ' selected' : ''}">${index + 1}</span>`,
    iconSize: [26, 26],
    iconAnchor: [13, 13],
  })
}

/** A new search (place, radius): every pin again, and the view framed on the closest ones. */
function draw() {
  if (!map || !layer) return
  layer.clearLayers()
  markers.clear()
  const me: L.LatLngTuple = [props.origin.latitude, props.origin.longitude]
  L.circleMarker(me, { radius: 7, color: '#ffffff', weight: 2.5, fillColor: '#175cd3', fillOpacity: 1 }).addTo(layer)
  const points: L.LatLngTuple[] = [me]
  props.garages.forEach((g, index) => {
    if (g.latitude === null || g.longitude === null) return
    const selected = g.id === props.selectedId
    const marker = L.marker([g.latitude, g.longitude], { icon: pin(index, selected), zIndexOffset: selected ? 1000 : 0 })
      .addTo(layer!)
    marker.on('click', () => emit('select', g.id))
    markers.set(g.id, marker)
    // The view frames the position and the closest garages (the list is sorted by distance): framing all of them
    // would pile the pins up on a phone; the others appear when zooming out
    if (index < CLOSEST_FRAMED) points.push([g.latitude, g.longitude])
  })
  map.fitBounds(L.latLngBounds(points), { padding: [28, 28], maxZoom: 14 })
}

/**
 * A garage picked in the list or on the map: only the pins change, and the map moves to it if it is out of view.
 * The zoom chosen by the driver is kept (framing all the garages again would undo it).
 */
function highlight(id: string | null | undefined, previous: string | null | undefined) {
  props.garages.forEach((g, index) => {
    if (g.id !== id && g.id !== previous) return
    const marker = markers.get(g.id)
    marker?.setIcon(pin(index, g.id === id))
    marker?.setZIndexOffset(g.id === id ? 1000 : 0)
  })
  const target = id ? markers.get(id) : undefined
  if (map && target && !map.getBounds().pad(-0.1).contains(target.getLatLng())) {
    map.panTo(target.getLatLng())
  }
}

onMounted(() => {
  map = L.map(container.value!, { zoomControl: true, attributionControl: true }).setView([46.6, 2.4], 6)
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  }).addTo(map)
  layer = L.layerGroup().addTo(map)
  draw()
})
watch(() => [props.garages, props.origin], draw, { deep: true })
watch(() => props.selectedId, highlight)
onBeforeUnmount(() => map?.remove())
</script>

<template>
  <div ref="container" class="garage-map" />
</template>

<style scoped>
.garage-map {
  height: 300px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  overflow: hidden;
  z-index: 0;
}

/* Beside the list on a computer: as tall as the screen allows */
@media (min-width: 1024px) {
  .garage-map {
    height: max(420px, calc(100vh - var(--topbar-height) - 220px));
  }
}

:deep(.garage-pin) {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 1px 4px rgba(16, 24, 40, 0.3);
  color: #fff;
  font: 600 11px/1 var(--font);
}

:deep(.garage-pin) {
  transition: transform 0.15s, background-color 0.15s;
}

:deep(.garage-pin:hover) {
  transform: scale(1.12);
}

:deep(.garage-pin.selected) {
  background: var(--text);
  transform: scale(1.2);
}
</style>
