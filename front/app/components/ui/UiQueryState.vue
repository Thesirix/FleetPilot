<script setup lang="ts">
import { CloudOff, TriangleAlert } from 'lucide-vue-next'
import type { GraphqlError } from '~/utils/graphql'

defineProps<{ error: GraphqlError | null; stale?: boolean }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="stale" class="banner small"><CloudOff :size="15" /> {{ $t('app.offline') }}</div>
  <div v-if="error" class="error card">
    <TriangleAlert :size="18" class="icon" />
    <div class="grow">
      <p v-if="error.code === 'NOT_FOUND' || error.status === 404">{{ $t('app.notFoundText') }}</p>
      <p v-else-if="error.status === 403">{{ $t('app.forbidden') }}</p>
      <p v-else>{{ $te(`errors.${error.code}`) ? $t(`errors.${error.code}`) : $t('app.unknownError') }}</p>
      <p v-if="error.correlationId" class="xs muted">{{ $t('app.errorReference', { id: error.correlationId }) }}</p>
    </div>
    <button class="btn btn-sm" @click="$emit('retry')">{{ $t('app.retry') }}</button>
  </div>
</template>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 8px 12px;
  border-radius: var(--radius);
  background: var(--warning-soft);
  color: var(--warning);
}

.error {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-color: #fecdca;
  background: var(--danger-soft);
}

.icon {
  flex: none;
  color: var(--danger);
}
</style>
