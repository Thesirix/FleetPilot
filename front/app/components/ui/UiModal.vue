<script setup lang="ts">
import { X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{ title: string; width?: string }>(), { width: '520px' })
const open = defineModel<boolean>('open', { default: false })
const dialog = ref<HTMLDialogElement | null>(null)

watch(open, (value) => {
  if (value && !dialog.value?.open) dialog.value?.showModal()
  if (!value && dialog.value?.open) dialog.value.close()
})
onMounted(() => { if (open.value) dialog.value?.showModal() })

function onBackdrop(event: MouseEvent) {
  if (event.target === dialog.value) open.value = false
}
</script>

<template>
  <dialog ref="dialog" class="modal" :style="{ width: props.width }" @close="open = false" @click="onBackdrop">
    <div v-if="open" class="panel">
      <header class="head">
        <h2>{{ title }}</h2>
        <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('app.close')" @click="open = false"><X :size="16" /></button>
      </header>
      <div class="body"><slot /></div>
      <footer v-if="$slots.footer" class="foot"><slot name="footer" /></footer>
    </div>
  </dialog>
</template>

<style scoped>
.modal {
  max-width: calc(100vw - 32px);
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-lg);
}

.modal::backdrop {
  background: rgba(16, 24, 40, 0.4);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 0;
}

.head h2 {
  font-size: var(--text-lg);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
}

.foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid var(--border);
  background: var(--surface-muted);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
</style>
