<script setup lang="ts">
import { ImageOff } from 'lucide-vue-next'
import { photoUrl } from '~/utils/photos'

/** A photo stored on the server (it needs the access token) or still on the phone (the bytes of the outbox). */
const props = withDefaults(defineProps<{ photoId?: string; bytes?: ArrayBuffer; size?: number; alt?: string }>(), { size: 72, alt: '' })
const emit = defineEmits<{ open: [url: string] }>()

const url = ref<string | null>(null)
const failed = ref(false)

async function load() {
  if (url.value) {
    URL.revokeObjectURL(url.value)
    url.value = null
  }
  failed.value = false
  if (props.bytes) {
    url.value = URL.createObjectURL(new Blob([props.bytes], { type: 'image/jpeg' }))
  } else if (props.photoId) {
    url.value = await photoUrl(props.photoId).catch(() => null)
    failed.value = url.value === null
  }
}

watch(() => [props.photoId, props.bytes], load, { immediate: true })
onBeforeUnmount(() => {
  if (url.value) URL.revokeObjectURL(url.value)
})
</script>

<template>
  <button type="button" class="thumb" :style="{ width: `${size}px`, height: `${size}px` }" :disabled="!url"
    @click="url && emit('open', url)">
    <img v-if="url" :src="url" :alt="alt" loading="lazy">
    <ImageOff v-else-if="failed" :size="18" class="muted" />
    <span v-else class="skeleton fill" />
  </button>
</template>

<style scoped>
.thumb {
  position: relative;
  flex: none;
  display: grid;
  place-items: center;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--surface-muted);
  cursor: zoom-in;
}

.thumb:disabled {
  cursor: default;
}

img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fill {
  width: 100%;
  height: 100%;
}
</style>
