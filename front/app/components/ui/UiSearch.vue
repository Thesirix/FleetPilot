<script setup lang="ts">
import { Search, X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{ placeholder?: string; delay?: number }>(), { placeholder: '', delay: 300 })
const model = defineModel<string>({ default: '' })
const draft = ref(model.value)
let timer: ReturnType<typeof setTimeout> | undefined

watch(draft, (value) => {
  clearTimeout(timer)
  timer = setTimeout(() => { model.value = value.trim() }, props.delay)
})
watch(model, (value) => { if (value !== draft.value.trim()) draft.value = value })
</script>

<template>
  <div class="search">
    <Search :size="15" class="icon" />
    <input v-model="draft" type="search" class="input" :placeholder="placeholder" :aria-label="placeholder">
    <button v-if="draft" class="clear" :aria-label="$t('app.close')" @click="draft = ''"><X :size="14" /></button>
  </div>
</template>

<style scoped>
.search {
  position: relative;
  min-width: 300px;
}

.icon {
  position: absolute;
  top: 50%;
  left: 10px;
  transform: translateY(-50%);
  color: var(--text-faint);
}

.input {
  padding-left: 32px;
  padding-right: 28px;
}

.input::-webkit-search-cancel-button {
  display: none;
}

.clear {
  position: absolute;
  top: 50%;
  right: 6px;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  transform: translateY(-50%);
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.clear:hover {
  background: var(--surface-hover);
}
</style>
