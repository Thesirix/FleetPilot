<script setup lang="ts">
import { Check, Copy } from 'lucide-vue-next'

/** A temporary password shown once to the administrator, with a copy button. */
const props = defineProps<{ password: string; username: string }>()
const copied = ref(false)
async function copy() {
  try {
    await navigator.clipboard.writeText(props.password)
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch {
    copied.value = false
  }
}
</script>

<template>
  <div class="temporary">
    <p class="small">{{ $t('users.temporaryText', { username }) }}</p>
    <div class="row password">
      <code class="mono" data-testid="temporary-password">{{ password }}</code>
      <button type="button" class="btn btn-sm" @click="copy">
        <Check v-if="copied" /><Copy v-else /> {{ copied ? $t('users.copied') : $t('users.copy') }}
      </button>
    </div>
    <p class="xs muted">{{ $t('users.temporaryHint') }}</p>
  </div>
</template>

<style scoped>
.temporary {
  display: grid;
  gap: 10px;
}

.password {
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius);
  background: var(--surface-muted);
}

.password code {
  font-size: 18px;
  letter-spacing: 0.04em;
}
</style>
