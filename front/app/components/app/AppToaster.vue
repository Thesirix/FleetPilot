<script setup lang="ts">
import { CircleAlert, CircleCheck, Info, X } from 'lucide-vue-next'

const { toasts, dismiss } = useToast()
const icons = { success: CircleCheck, error: CircleAlert, info: Info }
</script>

<template>
  <div class="toaster" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="toast in toasts" :key="toast.id" class="toast card" :class="toast.kind" role="status">
        <component :is="icons[toast.kind]" :size="18" class="icon" />
        <div class="grow">
          <p>{{ toast.message }}</p>
          <p v-if="toast.reference" class="xs muted">{{ $t('app.errorReference', { id: toast.reference }) }}</p>
        </div>
        <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('app.close')" @click="dismiss(toast.id)">
          <X :size="14" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toaster {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: min(380px, calc(100vw - 40px));
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 12px 12px 14px;
  box-shadow: var(--shadow-lg);
  font-size: var(--text-sm);
}

.icon { flex: none; margin-top: 1px; }
.success .icon { color: var(--success); }
.error .icon { color: var(--danger); }
.info .icon { color: var(--info); }

.toast-enter-active,
.toast-leave-active { transition: all 0.2s ease; }
.toast-enter-from,
.toast-leave-to { opacity: 0; transform: translateY(8px); }
</style>
