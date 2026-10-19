<script setup lang="ts">
import { Star } from 'lucide-vue-next'

/** Quality rating of a garage, from 1 to 5, as a number and a star. */
defineProps<{ value: number | null | undefined }>()
const f = useFormat()
</script>

<template>
  <span v-if="value" class="rating num" :class="{ low: value < 3, high: value >= 4.3 }">
    <Star :size="13" :stroke-width="2" /> {{ f.number(value, 1) }}
  </span>
  <span v-else class="muted">-</span>
</template>

<style scoped>
.rating {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--text-secondary);
  font-weight: 560;
}

.rating svg {
  color: #eab308;
  fill: #eab308;
}

.rating.low {
  color: var(--danger);
}

.rating.high {
  color: var(--success);
}
</style>
