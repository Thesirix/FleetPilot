<script setup lang="ts">
/** Registration number and model of a vehicle, as shown in every list. */
defineProps<{ vehicle: { registration: string; make: string; model: string } }>()
</script>

<template>
  <span class="vehicle-cell">
    <span class="plate mono">{{ vehicle.registration }}</span>
    <span class="model">{{ vehicle.make }} {{ vehicle.model }}</span>
  </span>
</template>

<style scoped>
.vehicle-cell {
  display: inline-flex;
  flex-direction: column;
  min-width: 0;
}

.plate {
  font-weight: 600;
  color: var(--text);
}

.model {
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
