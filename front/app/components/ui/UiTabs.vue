<script setup lang="ts">
defineProps<{ tabs: { key: string; label: string; count?: number }[] }>()
const active = defineModel<string>({ required: true })
</script>

<template>
  <div class="tabs" role="tablist">
    <button v-for="tab in tabs" :key="tab.key" role="tab" class="tab" :class="{ active: active === tab.key }"
      :aria-selected="active === tab.key" @click="active = tab.key">
      {{ tab.label }}
      <span v-if="tab.count !== undefined" class="count num">{{ tab.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--border);
  overflow-x: auto;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: -1px;
  padding: 9px 12px;
  border: none;
  border-bottom: 2px solid transparent;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 550;
  white-space: nowrap;
  cursor: pointer;
}

.tab:hover {
  color: var(--text);
}

.tab.active {
  border-bottom-color: var(--primary);
  color: var(--text);
}

.count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--neutral-soft);
  color: var(--text-secondary);
  font-size: 11px;
}
</style>
