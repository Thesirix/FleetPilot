<script setup lang="ts">
import type { Component } from 'vue'

const props = withDefaults(defineProps<{
  label: string
  value: string | number
  hint?: string
  tone?: 'default' | 'danger' | 'warning' | 'success'
  icon?: Component
  to?: string
}>(), { tone: 'default', hint: undefined, icon: undefined, to: undefined })

const link = resolveComponent('NuxtLink')
const { locale } = useI18n()

/**
 * The figure counts up to its value when it arrives or changes ("1 454", "698 657 €", "24,6 %"): the text around the
 * number and its decimals are kept, and the last frame shows the value exactly as given.
 */
const shown = ref(String(props.value))
let from = 0
let frame = 0

function parse(text: string) {
  const decimalSeparator = (1.5).toLocaleString(locale.value).charAt(1)
  const match = text.match(/^(\D*?)(-?\d[\d\s  .,]*)(\D*)$/)
  if (!match) return null
  const digits = match[2]!.replace(new RegExp(`[^\\d${decimalSeparator === '.' ? '\\.' : decimalSeparator}-]`, 'g'), '')
  const decimals = digits.includes(decimalSeparator) ? digits.split(decimalSeparator)[1]!.length : 0
  const value = Number(digits.replace(decimalSeparator, '.'))
  return Number.isFinite(value) ? { prefix: match[1]!, suffix: match[3]!, value, decimals } : null
}

watch(() => props.value, (value) => {
  const text = String(value)
  const target = parse(text)
  cancelAnimationFrame(frame)
  if (!import.meta.client || !target || matchMedia('(prefers-reduced-motion: reduce)').matches) {
    shown.value = text
    from = target?.value ?? 0
    return
  }
  const start = performance.now()
  const origin = from
  const format = new Intl.NumberFormat(locale.value,
    { minimumFractionDigits: target.decimals, maximumFractionDigits: target.decimals })
  const step = (now: number) => {
    const progress = Math.min(1, (now - start) / 900)
    const eased = 1 - (1 - progress) ** 3
    shown.value = progress < 1 ? `${target.prefix}${format.format(origin + (target.value - origin) * eased)}${target.suffix}` : text
    if (progress < 1) frame = requestAnimationFrame(step)
  }
  frame = requestAnimationFrame(step)
  from = target.value
}, { immediate: true })

onBeforeUnmount(() => cancelAnimationFrame(frame))
</script>

<template>
  <component :is="to ? link : 'div'" :to="to" class="kpi card" :class="[tone, { link: to }]">
    <div class="row-between">
      <span class="label">{{ label }}</span>
      <span v-if="icon" class="icon"><component :is="icon" :size="16" /></span>
    </div>
    <span class="value num">{{ shown }}</span>
    <span v-if="hint" class="hint">{{ hint }}</span>
  </component>
</template>

<style scoped>
.kpi {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  color: var(--text);
}

.kpi {
  position: relative;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

/* A thin line of the tone colour along the top, drawn when the card appears */
.kpi::before {
  content: "";
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, var(--kpi-tone), color-mix(in srgb, var(--kpi-tone) 30%, transparent));
  transform-origin: left;
  animation: kpi-line 0.8s 0.15s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.kpi { --kpi-tone: var(--primary); }
.kpi.danger { --kpi-tone: var(--danger); }
.kpi.warning { --kpi-tone: var(--warning); }
.kpi.success { --kpi-tone: var(--success); }

.kpi.link:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

.kpi.link:hover .icon {
  background: color-mix(in srgb, var(--kpi-tone) 14%, transparent);
  color: var(--kpi-tone);
}

@keyframes kpi-line {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

@media (prefers-reduced-motion: reduce) {
  .kpi::before { animation: none; }
  .kpi.link:hover { transform: none; }
}

.label {
  color: var(--text-muted);
  font-size: var(--text-sm);
  font-weight: 500;
}

.icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius);
  background: var(--neutral-soft);
  color: var(--text-secondary);
}

.value {
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.danger .value { color: var(--danger); }
.danger .icon { background: var(--danger-soft); color: var(--danger); }
.warning .value { color: var(--warning); }
.warning .icon { background: var(--warning-soft); color: var(--warning); }
.success .icon { background: var(--success-soft); color: var(--success); }
</style>
