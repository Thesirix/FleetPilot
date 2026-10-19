<script setup lang="ts">
/** Colored badge of an enum value (vehicle status, urgency, claim status...), with its translated label. */
const props = defineProps<{ type: keyof typeof TONES; value: string | null | undefined }>()

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'primary' | ''

const TONES = {
  VehicleStatus: { ACTIVE: 'success', IN_REPAIR: 'warning', IMMOBILIZED: 'danger', AWAITING_DELIVERY: 'info', DISPOSED: '' },
  Urgency: { OVERDUE: 'danger', DUE_SOON: 'warning', UPCOMING: '' },
  IncidentStatus: { DECLARED: 'info', UNDER_REVIEW: 'warning', IN_REPAIR: 'warning', CLOSED: 'success', REJECTED: '' },
  ContractStatus: { ACTIVE: 'success', ENDED: '' },
  Responsibility: { UNKNOWN: '', NONE: 'success', PARTIAL: 'warning', FULL: 'danger' },
  QuoteStatus: { RECEIVED: 'info', IN_REVIEW: 'warning', TO_NEGOTIATE: 'danger', ACCEPTED: 'success', REJECTED: '',
    COMPLETED: 'primary', INVOICED: '' },
  Finding: { OVERPRICED_PART: 'danger', EXCESSIVE_TIME: 'danger', OVERPRICED_RATE: 'danger', DUPLICATE: 'danger',
    EXCESSIVE_SUPPLIES: 'warning', UNKNOWN_ITEM: 'info' },
  GarageNetwork: { DEALER: '', INDEPENDENT: '', FAST_FIT: '', BODYWORK: '', TRUCK: '' },
  Energy: { ELECTRIC: 'primary', HYDROGEN: 'primary', PLUG_IN_HYBRID: 'info', HYBRID: 'info', PETROL: '', DIESEL: '',
    LPG: '', CNG: '' },
} as const satisfies Record<string, Record<string, Tone>>

const tone = computed(() => (props.value ? (TONES[props.type] as Record<string, Tone>)[props.value] ?? '' : ''))
</script>

<template>
  <span v-if="value" class="badge" :class="[tone, { plain: type === 'Energy' || type === 'GarageNetwork' }]">{{ $t(`enum.${type}.${value}`) }}</span>
  <span v-else class="muted">-</span>
</template>
