<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

defineProps<{ title?: string; text?: string }>()
</script>

<template>
  <div class="empty">
    <span class="icon"><Inbox :size="20" /></span>
    <p v-if="title" class="title">{{ title }}</p>
    <p v-if="text" class="muted small">{{ text }}</p>
    <slot />
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 40px 20px;
  text-align: center;
}

.icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin-bottom: 4px;
  border-radius: 50%;
  background: var(--neutral-soft);
  color: var(--text-muted);
}

.title {
  font-weight: 600;
}
</style>
