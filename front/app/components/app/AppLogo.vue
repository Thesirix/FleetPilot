<script setup lang="ts">
defineProps<{ compact?: boolean }>()
</script>

<template>
  <span class="logo">
    <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--primary)" />
      <path d="M9 23 L21 11" stroke="#fff" stroke-width="3" stroke-linecap="round" />
      <circle cx="22.5" cy="9.5" r="3" fill="#5eead4" />
    </svg>
    <span v-if="!compact" class="wordmark">FleetPilot</span>
  </span>
</template>

<style scoped>
.logo {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.wordmark {
  font-size: 16px;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text);
}
</style>
