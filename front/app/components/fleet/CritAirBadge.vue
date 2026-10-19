<script setup lang="ts">
/** The French Crit'Air sticker, in its official color. 0 is the green "E" sticker of zero-emission vehicles. */
const props = defineProps<{ value: number; labelled?: boolean }>()
const label = computed(() => (props.value === 0 ? 'E' : String(props.value)))
</script>

<template>
  <span class="critair" :class="[`critair-${label.toLowerCase()}`, { labelled }]" :title="`Crit'Air ${label}`">
    {{ labelled ? `Crit'Air ${label}` : label }}
  </span>
</template>

<style scoped>
.critair {
  display: inline-grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
}

/* With its name, where the sticker would not be recognised alone (the vehicle card of the driver) */
.critair.labelled {
  width: auto;
  height: 24px;
  padding: 0 10px;
  border-radius: 12px;
  font-size: 12px;
  white-space: nowrap;
}

.critair-e { background: var(--critair-e); }
.critair-1 { background: var(--critair-1); }
.critair-2 { background: var(--critair-2); color: #422006; }
.critair-3 { background: var(--critair-3); }
.critair-4 { background: var(--critair-4); }
.critair-5 { background: var(--critair-5); }
</style>
