<script setup lang="ts">
import type { QuoteLine } from '~/graphql/quotes'

/**
 * The heart of the savings module: each line of the garage quote next to its value at the reference tariff, with the
 * gap and what the analysis found. Before the analysis (vehicle not identified) only the quoted values are shown.
 */
const props = defineProps<{ lines: QuoteLine[]; amount: number; referenceAmount: number | null; overcharge: number }>()
const f = useFormat()
const { t } = useI18n()

const analysed = computed(() => props.referenceAmount !== null)

function quantity(line: QuoteLine) {
  return line.kind === 'LABOUR' ? t('garage.hours', { value: f.number(line.quantity, 1) }) : f.number(line.quantity, line.quantity % 1 ? 1 : 0)
}

function referenceQuantity(line: QuoteLine) {
  if (line.referenceQuantity === null || line.kind !== 'LABOUR') return null
  return line.referenceQuantity === line.quantity ? null : t('garage.hours', { value: f.number(line.referenceQuantity, 1) })
}

function referencePrice(line: QuoteLine) {
  if (line.referenceUnitPrice === null || line.referenceUnitPrice === line.unitPrice) return null
  return f.money(line.referenceUnitPrice, 2)
}
</script>

<template>
  <div class="table-wrap">
    <table class="table lines">
      <thead>
        <tr>
          <th class="pos">#</th>
          <th>{{ $t('quote.line') }}</th>
          <th class="right">{{ $t('quote.quantity') }}</th>
          <th class="right">{{ $t('quote.unitPrice') }}</th>
          <th class="right">{{ $t('quote.quoted') }}</th>
          <th v-if="analysed" class="right">{{ $t('quote.referenceCol') }}</th>
          <th v-if="analysed" class="right">{{ $t('quote.gap') }}</th>
          <th v-if="analysed">{{ $t('quote.findings') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in lines" :key="line.id" :class="{ flagged: line.finding && line.finding !== 'UNKNOWN_ITEM' }">
          <td class="pos muted num">{{ line.position }}</td>
          <td>
            <div class="label">{{ line.label }}</div>
            <div class="meta">
              <span>{{ f.label('LineKind', line.kind) }}</span>
              <span v-if="line.code" class="mono">{{ line.code }}</span>
            </div>
          </td>
          <td class="right num nowrap">
            {{ quantity(line) }}
            <div v-if="referenceQuantity(line)" class="ref">{{ $t('quote.ref', { value: referenceQuantity(line) }) }}</div>
          </td>
          <td class="right num nowrap">
            {{ f.money(line.unitPrice, 2) }}
            <div v-if="referencePrice(line)" class="ref">{{ $t('quote.ref', { value: referencePrice(line) }) }}</div>
          </td>
          <td class="right num nowrap strong">{{ f.money(line.amount, 2) }}</td>
          <td v-if="analysed" class="right num nowrap secondary">{{ f.money(line.referenceAmount, 2) }}</td>
          <td v-if="analysed" class="right num nowrap" :class="{ over: line.overcharge > 0 }">
            {{ line.overcharge > 0 ? `+${f.money(line.overcharge, 2)}` : '-' }}
          </td>
          <td v-if="analysed" class="nowrap">
            <span v-if="line.finding" :title="line.findingDetail ?? ''"><FleetBadge type="Finding" :value="line.finding" /></span>
            <span v-else class="ok">✓</span>
          </td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td />
          <td colspan="3" class="strong">{{ $t('quote.total') }}</td>
          <td class="right num strong">{{ f.money(amount, 2) }}</td>
          <td v-if="analysed" class="right num secondary">{{ f.money(referenceAmount, 2) }}</td>
          <td v-if="analysed" class="right num" :class="{ over: overcharge > 0 }">
            {{ overcharge > 0 ? `+${f.money(overcharge, 2)}` : '-' }}
          </td>
          <td v-if="analysed" />
        </tr>
      </tfoot>
    </table>
  </div>
</template>

<style scoped>
.lines td {
  vertical-align: top;
}

.pos {
  width: 36px;
}

.label {
  font-weight: 520;
}

.meta {
  display: flex;
  white-space: nowrap;
  gap: 8px;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.ref {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.flagged {
  background: var(--danger-soft);
}

.over {
  color: var(--danger);
  font-weight: 600;
}

.ok {
  color: var(--success);
  font-weight: 700;
}

tfoot td {
  border-top: 1px solid var(--border-strong);
  background: var(--surface-muted);
}

.strong {
  font-weight: 620;
}
</style>
