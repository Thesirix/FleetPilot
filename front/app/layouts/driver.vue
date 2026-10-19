<script setup lang="ts">
import { Bell, ClipboardCheck, CloudOff, Fuel, House, LogOut, MapPin, ShieldAlert } from 'lucide-vue-next'
import { logout } from '~/utils/auth'

const { t } = useI18n()
const session = useSessionStore()
const { pending, rejected } = useOutbox()
const { unread } = useDriverNotifications()
const online = ref(true)

const tabs = computed(() => [
  { to: '/conducteur', label: t('driverApp.tabs.home'), icon: House },
  { to: '/conducteur/plein', label: t('driverApp.tabs.fuel'), icon: Fuel },
  { to: '/conducteur/etat-des-lieux', label: t('driverApp.tabs.inspection'), icon: ClipboardCheck },
  { to: '/conducteur/sinistre', label: t('driverApp.tabs.incident'), icon: ShieldAlert },
  { to: '/conducteur/garages', label: t('driverApp.tabs.garages'), icon: MapPin },
])

const route = useRoute()
function active(to: string) {
  return to === '/conducteur' ? route.path === to : route.path.startsWith(to)
}

function updateOnline() {
  online.value = navigator.onLine
}
onMounted(() => {
  updateOnline()
  window.addEventListener('online', updateOnline)
  window.addEventListener('offline', updateOnline)
})
onBeforeUnmount(() => {
  window.removeEventListener('online', updateOnline)
  window.removeEventListener('offline', updateOnline)
})
</script>

<template>
  <div class="driver-shell">
    <header class="topbar">
      <NuxtLink to="/conducteur" class="brand"><AppLogo /></NuxtLink>
      <!-- On a computer the tabs sit in the top bar; on a phone they stay at the bottom, under the thumb -->
      <nav class="topnav" :aria-label="$t('driverApp.tabs.label')">
        <NuxtLink v-for="tab in tabs" :key="tab.to" :to="tab.to" class="topnav-link" :class="{ active: active(tab.to) }"
          :aria-current="active(tab.to) ? 'page' : undefined">
          <component :is="tab.icon" :size="17" />
          <span>{{ tab.label }}</span>
        </NuxtLink>
      </nav>
      <div class="top-actions">
        <span class="who small muted">{{ session.displayName }}</span>
        <NuxtLink v-if="pending.length || rejected.length" to="/conducteur#outbox" class="outbox-pill"
          :class="{ error: rejected.length }">
          <CloudOff :size="14" /> {{ pending.length + rejected.length }}
        </NuxtLink>
        <NuxtLink to="/conducteur/notifications" class="btn btn-ghost btn-icon bell" :aria-label="$t('driverApp.notifications.title')">
          <Bell :size="20" />
          <span v-if="unread" class="dot num">{{ unread > 9 ? '9+' : unread }}</span>
        </NuxtLink>
        <button class="btn btn-ghost btn-icon" :aria-label="$t('app.logout')" :title="session.displayName" @click="logout()">
          <LogOut :size="18" />
        </button>
      </div>
    </header>

    <p v-if="!online" class="offline-bar" role="status">
      <CloudOff :size="14" /> {{ $t('driverApp.offline') }}
    </p>

    <main class="driver-content">
      <slot />
    </main>

    <nav class="tabbar" :aria-label="$t('driverApp.tabs.label')">
      <NuxtLink v-for="tab in tabs" :key="tab.to" :to="tab.to" class="tab" :class="{ active: active(tab.to) }"
        :aria-current="active(tab.to) ? 'page' : undefined">
        <component :is="tab.icon" :size="21" :stroke-width="active(tab.to) ? 2.2 : 1.8" />
        <span>{{ tab.label }}</span>
      </NuxtLink>
    </nav>
    <AppToaster />
  </div>
</template>

<style scoped>
.driver-shell {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: calc(var(--topbar-height) + env(safe-area-inset-top));
  padding: env(safe-area-inset-top) 12px 0 16px;
  background: var(--surface);
  border-bottom: 1px solid var(--border);
}

.brand {
  display: flex;
  color: inherit;
  text-decoration: none;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.bell {
  position: relative;
}

.dot {
  position: absolute;
  top: 2px;
  right: 1px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 9px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 17px;
  text-align: center;
}

.outbox-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 26px;
  padding: 0 9px;
  border-radius: 13px;
  background: var(--warning-soft);
  color: var(--warning);
  font-size: var(--text-xs);
  font-weight: 600;
  text-decoration: none;
}

.outbox-pill.error {
  background: var(--danger-soft);
  color: var(--danger);
}

.offline-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0;
  padding: 6px 12px;
  background: var(--text);
  color: #fff;
  font-size: var(--text-xs);
}

.driver-content {
  max-width: 560px;
  margin: 0 auto;
  padding: 16px 16px calc(84px + env(safe-area-inset-bottom));
}

.tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  padding: 4px 4px env(safe-area-inset-bottom);
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  min-width: 0;
  padding: 7px 2px 6px;
  border-radius: var(--radius);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 500;
  text-decoration: none;
}

.tab span {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab.active {
  color: var(--primary);
}

.topnav,
.who {
  display: none;
}

@media (min-width: 720px) and (max-width: 1023px) {
  .tabbar {
    right: 50%;
    left: auto;
    width: 560px;
    margin-right: -280px;
    border: 1px solid var(--border);
    border-bottom: none;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  }
}

@media (min-width: 1024px) {
  .topbar {
    gap: 24px;
    padding: 0 20px 0 24px;
  }

  .topnav {
    display: flex;
    flex: 1;
    gap: 2px;
    height: 100%;
  }

  .topnav-link {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 0 12px;
    border-bottom: 2px solid transparent;
    color: var(--text-muted);
    font-size: var(--text-sm);
    font-weight: 500;
    text-decoration: none;
  }

  .topnav-link:hover {
    color: var(--text);
  }

  .topnav-link.active {
    border-bottom-color: var(--primary);
    color: var(--primary);
  }

  .who {
    display: inline;
    margin-right: 6px;
  }

  .tabbar {
    display: none;
  }

  .driver-content {
    max-width: 1120px;
    padding: 24px 24px 40px;
  }
}
</style>

<style>
/* Pages of the driver space on a computer: the form on the left, what was already sent on the right */
@media (min-width: 1024px) {
  .driver-content .page.split {
    display: grid;
    grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
    align-items: start;
    gap: 16px 24px;
  }

  .driver-content .page.split > :not(form):not(section) {
    grid-column: 1 / -1;
  }

  .driver-content .page.narrow {
    max-width: 760px;
    margin: 0 auto;
  }
}
</style>
