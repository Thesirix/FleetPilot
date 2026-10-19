<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{ totalPages: number; totalElements: number }>()
const page = defineModel<number>('page', { required: true })
const pages = computed(() => Math.max(props.totalPages, 1))
</script>

<template>
  <div class="pagination">
    <span class="muted small">{{ $t('app.results', { count: totalElements }, totalElements) }}</span>
    <div class="row">
      <span class="muted small">{{ $t('app.page', { page: page + 1, pages }) }}</span>
      <button class="btn btn-sm btn-icon" :disabled="page === 0" :aria-label="$t('app.previous')" @click="page--">
        <ChevronLeft :size="15" />
      </button>
      <button class="btn btn-sm btn-icon" :disabled="page + 1 >= pages" :aria-label="$t('app.next')" @click="page++">
        <ChevronRight :size="15" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-top: 1px solid var(--border);
}
</style>
