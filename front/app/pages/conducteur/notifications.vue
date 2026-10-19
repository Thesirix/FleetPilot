<script setup lang="ts">
import { BellRing, CalendarClock, Cpu, ShieldAlert } from 'lucide-vue-next'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
useHead({ title: () => `${t('driverApp.notifications.title')} · FleetPilot` })

const notifications = useDriverNotifications()
const icons = { DEADLINE: CalendarClock, VEHICLE_ALERT: Cpu, INCIDENT: ShieldAlert }
const levelClass = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info' } as const

// Opening the list marks everything as seen: the badge of the bell goes back to zero
onMounted(async () => {
  await notifications.refresh()
  notifications.markAllSeen()
})
</script>

<template>
  <div class="stack page narrow">
    <div class="intro">
      <h1>{{ $t('driverApp.notifications.title') }}</h1>
      <p class="muted small">{{ $t('driverApp.notifications.subtitle') }}</p>
    </div>

    <div v-if="notifications.permission.value === 'default'" class="card">
      <div class="card-body row-between enable">
        <p class="small">{{ $t('driverApp.notifications.enableHint') }}</p>
        <button class="btn btn-primary btn-sm" @click="notifications.enableSystemNotifications()">
          <BellRing :size="15" /> {{ $t('driverApp.notifications.enable') }}
        </button>
      </div>
    </div>
    <p v-else-if="notifications.permission.value === 'denied'" class="small muted">{{ $t('driverApp.notifications.denied') }}</p>

    <ul v-if="notifications.list.value.length" class="feed">
      <li v-for="n in notifications.list.value" :key="n.id" class="card item" :class="levelClass[n.level]">
        <span class="icon"><component :is="icons[n.kind]" :size="18" /></span>
        <div class="grow">
          <div class="row-between">
            <p class="title">{{ notifications.title(n) }}</p>
            <span class="badge" :class="levelClass[n.level]">{{ $t(`driverApp.notifications.levels.${n.level}`) }}</span>
          </div>
          <p class="small secondary">{{ notifications.text(n) }}</p>
          <p class="xs muted">{{ n.kind === 'DEADLINE' ? $t(`driverApp.notifications.kinds.${n.kind}`) : f.dateTime(n.at) }}</p>
        </div>
      </li>
    </ul>
    <UiEmpty v-else :text="$t('driverApp.notifications.none')" />
  </div>
</template>

<style scoped>
.page {
  gap: 14px;
}

.intro h1 {
  margin: 4px 0 2px;
  font-size: var(--text-xl);
}

.intro p,
.enable p {
  margin: 0;
}

.enable {
  gap: 12px;
}

.feed {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.item {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-left: 3px solid var(--info);
}

.item.danger {
  border-left-color: var(--danger);
}

.item.warning {
  border-left-color: var(--warning);
}

.item p {
  margin: 0;
}

.icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: var(--radius);
  background: var(--neutral-soft);
  color: var(--text-secondary);
}

.title {
  font-weight: 600;
  font-size: var(--text-sm);
}

.secondary {
  margin-top: 2px !important;
}

.badge.danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.badge.warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.badge.info {
  background: var(--info-soft);
  color: var(--info);
}
</style>
