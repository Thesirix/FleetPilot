<script setup lang="ts">
/** "In 12 days", "3 days late", "In 850 km": what remains before a deadline, whichever limit comes first. */
const props = defineProps<{ daysRemaining: number | null | undefined; kilometresRemaining: number | null | undefined }>()
const { number } = useFormat()
const { t } = useI18n()

/** One piece of information only: the limit already passed if any, otherwise the date, otherwise the mileage. */
const parts = computed(() => {
  const days = props.daysRemaining
  const km = props.kilometresRemaining
  const hasDays = days !== null && days !== undefined
  const hasKm = km !== null && km !== undefined
  if (hasDays && days < 0) return [t('deadline.late', { count: -days }, -days)]
  if (hasKm && km <= 0) return [t('deadline.kmLate', { value: number(-km) })]
  if (hasDays) return [days === 0 ? t('deadline.today') : t('deadline.inDays', { count: days }, days)]
  if (hasKm) return [t('deadline.inKm', { value: number(km) })]
  return []
})
const late = computed(() => (props.daysRemaining ?? 1) < 0 || (props.kilometresRemaining ?? 1) <= 0)
</script>

<template>
  <span :class="{ late }" class="small">{{ parts.join(' · ') || '-' }}</span>
</template>

<style scoped>
.late {
  color: var(--danger);
  font-weight: 550;
}
</style>
