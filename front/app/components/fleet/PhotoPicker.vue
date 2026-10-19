<script setup lang="ts">
import { Camera, X } from 'lucide-vue-next'
import type { OutboxPhoto } from '~/utils/outbox'
import { preparePhoto } from '~/utils/photos'

/** Takes photos with the camera of the phone (or picks them from the gallery), resized before they are kept. */
const props = withDefaults(defineProps<{ max?: number }>(), { max: 10 })
const photos = defineModel<OutboxPhoto[]>({ required: true })
const input = ref<HTMLInputElement | null>(null)
const preparing = ref(false)
const viewing = ref<string | null>(null)
const viewerOpen = computed({ get: () => viewing.value !== null, set: (o: boolean) => { if (!o) viewing.value = null } })

async function onFiles(event: Event) {
  const files = Array.from((event.target as HTMLInputElement).files ?? [])
  preparing.value = true
  try {
    for (const file of files.slice(0, props.max - photos.value.length)) {
      photos.value = [...photos.value, await preparePhoto(file)]
    }
  } finally {
    preparing.value = false
    if (input.value) input.value.value = ''
  }
}

function remove(id: string) {
  photos.value = photos.value.filter(p => p.id !== id)
}
</script>

<template>
  <div class="picker">
    <div class="row-wrap">
      <div v-for="p in photos" :key="p.id" class="item">
        <PhotoThumb :bytes="p.bytes" :size="76" :alt="$t('driverApp.photos.photo')" @open="viewing = $event" />
        <button type="button" class="remove" :aria-label="$t('driverApp.photos.remove')" @click="remove(p.id)"><X :size="12" /></button>
      </div>
      <label v-if="photos.length < max" class="add" :class="{ busy: preparing }">
        <Camera :size="20" />
        <span class="xs">{{ preparing ? $t('app.loading') : $t('driverApp.photos.add') }}</span>
        <input ref="input" type="file" accept="image/*" capture="environment" multiple class="sr-only"
          data-testid="photo-input" @change="onFiles">
      </label>
    </div>
    <p class="field-hint">{{ $t('driverApp.photos.hint', { count: photos.length, max }) }}</p>
    <UiModal v-model:open="viewerOpen" :title="$t('driverApp.photos.photo')" width="720px">
      <img v-if="viewing" :src="viewing" class="full" alt="">
    </UiModal>
  </div>
</template>

<style scoped>
.row-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.item {
  position: relative;
}

.remove {
  position: absolute;
  top: -6px;
  right: -6px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 2px solid var(--surface);
  border-radius: 50%;
  background: var(--text);
  color: #fff;
  cursor: pointer;
}

.add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 76px;
  height: 76px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  color: var(--text-secondary);
  cursor: pointer;
}

.add.busy {
  opacity: 0.6;
}

.full {
  display: block;
  width: 100%;
  border-radius: var(--radius);
}
</style>
