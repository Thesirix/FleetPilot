<script setup lang="ts">
import { DQ, type Photo } from '~/graphql/driver'

/** The photos the driver took with the app when declaring the claim. */
const props = defineProps<{ incidentId: string }>()
const { data } = useGraphqlQuery<{ incident: { photos: Photo[] } | null }>(DQ.incidentPhotos,
  () => ({ id: props.incidentId }), { cacheKey: () => `incident-photos:${props.incidentId}` })
const photos = computed(() => data.value?.incident?.photos ?? [])

const viewing = ref<string | null>(null)
const viewerOpen = computed({ get: () => viewing.value !== null, set: (o: boolean) => { if (!o) viewing.value = null } })
</script>

<template>
  <section class="card">
    <header class="card-header">
      <h2>{{ $t('driverRecords.photos') }}</h2>
      <span class="small muted num">{{ photos.length }}</span>
    </header>
    <div class="card-body">
      <div v-if="photos.length" class="thumbs">
        <PhotoThumb v-for="p in photos" :key="p.id" :photo-id="p.id" :size="104" @open="viewing = $event" />
      </div>
      <p v-else class="small muted">{{ $t('driverRecords.noPhoto') }}</p>
    </div>
    <UiModal v-model:open="viewerOpen" :title="$t('driverApp.photos.photo')" width="820px">
      <img v-if="viewing" :src="viewing" class="full" alt="">
    </UiModal>
  </section>
</template>

<style scoped>
.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.full {
  display: block;
  width: 100%;
  border-radius: var(--radius);
}
</style>
