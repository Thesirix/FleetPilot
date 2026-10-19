<script setup lang="ts">
import { Bot, Building2, UserRound } from 'lucide-vue-next'
import type { QuoteHistory } from '~/graphql/quotes'

/** Timeline of a quote: who did what, when, for which amount. Most recent first. */
const props = defineProps<{ history: QuoteHistory[] }>()
const f = useFormat()

const entries = computed(() => [...props.history].reverse())
const ICONS = { GARAGE: Building2, MANAGER: UserRound, SYSTEM: Bot }
const TONES: Record<string, string> = {
  AUTO_APPROVED: 'success', ACCEPTED: 'success', COUNTER_ACCEPTED: 'success', REJECTED: 'danger', COUNTER_REFUSED: 'warning',
  COUNTER_OFFER: 'warning', INVOICED: 'primary', COMPLETED: 'primary',
}
</script>

<template>
  <ol class="timeline">
    <li v-for="(entry, index) in entries" :key="index" :class="TONES[entry.action] ?? ''">
      <span class="icon"><component :is="ICONS[entry.party as keyof typeof ICONS] ?? Bot" :size="14" /></span>
      <div class="content">
        <div class="row-between">
          <span class="action">{{ f.label('QuoteHistoryAction', entry.action) }}</span>
          <span v-if="entry.amount !== null && entry.action !== 'ANALYSED'" class="num amount">{{ f.money(entry.amount, 2) }}</span>
        </div>
        <div class="meta">{{ entry.author }} · {{ f.dateTime(entry.at) }}</div>
        <p v-if="entry.message && entry.action !== 'ANALYSED'" class="message">{{ entry.message }}</p>
      </div>
    </li>
  </ol>
</template>

<style scoped>
.timeline {
  margin: 0;
  padding: 0;
  list-style: none;
}

.timeline li {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
}

.timeline li:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 28px;
  bottom: 2px;
  left: 13px;
  width: 1px;
  background: var(--border);
}

.icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 27px;
  height: 27px;
  border: 1px solid var(--border);
  border-radius: 50%;
  background: var(--surface);
  color: var(--text-muted);
}

.success .icon { border-color: #abefc6; background: var(--success-soft); color: var(--success); }
.danger .icon { border-color: #fecdca; background: var(--danger-soft); color: var(--danger); }
.warning .icon { border-color: #fedf89; background: var(--warning-soft); color: var(--warning); }
.primary .icon { border-color: #99d6cf; background: var(--primary-soft); color: var(--primary); }

.content {
  flex: 1;
  min-width: 0;
  padding-top: 3px;
}

.action {
  font-size: var(--text-sm);
  font-weight: 560;
}

.amount {
  font-size: var(--text-sm);
  font-weight: 560;
}

.meta {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.message {
  margin: 6px 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  color: var(--text-secondary);
  font-size: var(--text-sm);
}
</style>
