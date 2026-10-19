<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next'

defineProps<{ title: string; subtitle?: string; back?: string }>()
</script>

<template>
  <header class="page-header">
    <div class="grow">
      <NuxtLink v-if="back" :to="back" class="back small"><ArrowLeft :size="14" /> {{ $t('app.back') }}</NuxtLink>
      <div class="title-row">
        <h1>{{ title }}</h1>
        <slot name="badges" />
      </div>
      <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      <slot name="meta" />
    </div>
    <div v-if="$slots.actions" class="actions"><slot name="actions" /></div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  color: var(--text-muted);
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.subtitle {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
