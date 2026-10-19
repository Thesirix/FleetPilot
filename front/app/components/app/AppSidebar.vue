<script setup lang="ts">
import {
  ArrowUpRight, Bell, BookOpenCheck, Building2, CalendarClock, Car, ChartColumnBig, ChartNoAxesCombined, FilePlus2,
  FileSignature, FileUp, Fuel, History, LayoutDashboard, Leaf, Mails, MapPinned, PiggyBank, ReceiptText, Route,
  ShieldAlert, SlidersHorizontal, Smartphone, UserCog, Users, Wrench,
} from 'lucide-vue-next'
import type { Component } from 'vue'

interface NavItem {
  to: string
  label: string
  icon: Component
  /** Another application (Kibana), opened in a new tab. */
  external?: boolean
}

const session = useSessionStore()
const kibanaUrl = useRuntimeConfig().public.kibanaUrl as string
const { t } = useI18n()
const open = defineModel<boolean>('open', { default: false })

const sections = computed(() => {
  const result: { title: string; items: NavItem[] }[] = []
  if (session.isManager) {
    result.push({
      title: t('nav.fleet'),
      items: [
        { to: '/', label: t('nav.dashboard'), icon: LayoutDashboard },
        { to: '/vehicules', label: t('nav.vehicles'), icon: Car },
        { to: '/conducteurs', label: t('nav.drivers'), icon: Users },
        { to: '/contrats', label: t('nav.contracts'), icon: FileSignature },
        { to: '/echeances', label: t('nav.deadlines'), icon: CalendarClock },
        { to: '/sinistres', label: t('nav.incidents'), icon: ShieldAlert },
      ],
    })
    result.push({
      title: t('nav.connected'),
      items: [
        { to: '/telemetrie', label: t('nav.liveMap'), icon: MapPinned },
        { to: '/alertes', label: t('nav.alerts'), icon: Bell },
      ],
    })
    result.push({
      title: t('nav.savings'),
      items: [
        { to: '/devis', label: t('nav.quotes'), icon: ReceiptText },
        { to: '/economies', label: t('nav.savingsDashboard'), icon: PiggyBank },
        { to: '/garages', label: t('nav.garages'), icon: Wrench },
        { to: '/tarif', label: t('nav.tariff'), icon: BookOpenCheck },
      ],
    })
    result.push({
      title: t('nav.steering'),
      items: [
        { to: '/rapports', label: t('nav.reports'), icon: ChartColumnBig },
        { to: '/transition', label: t('nav.transition'), icon: Leaf },
        { to: '/carburant', label: t('nav.fuel'), icon: Fuel },
      ],
    })
    result.push({ title: t('nav.tools'), items: [{ to: '/vehicules/import', label: t('nav.import'), icon: FileUp }] })
  }
  if (session.isStaff) {
    result.push({
      title: t('nav.administration'),
      items: [
        { to: '/administration/entreprises', label: t('nav.companies'), icon: Building2 },
        { to: '/administration/utilisateurs', label: t('nav.users'), icon: UserCog },
        { to: '/administration/parametres', label: t('nav.settings'), icon: SlidersHorizontal },
        { to: '/administration/journal', label: t('nav.audit'), icon: History },
        { to: '/administration/flux', label: t('nav.flows'), icon: Route },
        { to: '/administration/envois', label: t('nav.mails'), icon: Mails },
        { to: kibanaUrl, label: t('nav.analytics'), icon: ChartNoAxesCombined, external: true },
      ],
    })
  }
  if (session.isGarage) {
    result.push({
      title: t('nav.garageSpace'),
      items: [
        { to: '/espace-garage', label: t('nav.myQuotes'), icon: ReceiptText },
        { to: '/espace-garage/nouveau', label: t('nav.newQuote'), icon: FilePlus2 },
        { to: '/tarif', label: t('nav.tariff'), icon: BookOpenCheck },
      ],
    })
  }
  if (session.isDriver) {
    result.push({ title: t('nav.mySpace'), items: [{ to: '/conducteur', label: t('nav.mySpace'), icon: Smartphone }] })
  }
  return result
})

const route = useRoute()
function active(to: string) {
  if (to === '/') return route.path === '/'
  if (to === '/vehicules') return route.path.startsWith('/vehicules') && !route.path.startsWith('/vehicules/import')
  if (to === '/espace-garage') return route.path === '/espace-garage' || (session.isGarage && route.path.startsWith('/devis/'))
  return route.path.startsWith(to)
}

watch(() => route.fullPath, () => { open.value = false })
</script>

<template>
  <aside class="sidebar" :class="{ open }">
    <div class="brand">
      <NuxtLink to="/" aria-label="FleetPilot"><AppLogo /></NuxtLink>
    </div>
    <nav class="nav" :aria-label="$t('app.name')">
      <div v-for="section in sections" :key="section.title" class="section">
        <p class="section-title">{{ section.title }}</p>
        <NuxtLink v-for="item in section.items" :key="item.to" :to="item.to" class="nav-item"
          :class="{ active: !item.external && active(item.to) }" :external="item.external"
          :target="item.external ? '_blank' : undefined" :rel="item.external ? 'noopener' : undefined">
          <component :is="item.icon" :size="17" :stroke-width="1.8" />
          <span>{{ item.label }}</span>
          <ArrowUpRight v-if="item.external" :size="13" class="external" />
        </NuxtLink>
      </div>
    </nav>
    <AppUserMenu class="user" />
  </aside>
  <div v-if="open" class="backdrop" @click="open = false" />
</template>

<style scoped>
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 30;
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  background: var(--surface);
  border-right: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  height: var(--topbar-height);
  padding: 0 18px;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
}

.section + .section {
  margin-top: 18px;
}

.section-title {
  padding: 0 10px 6px;
  color: var(--text-faint);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 34px;
  padding: 0 10px;
  border-radius: var(--radius);
  color: var(--text-secondary);
  font-size: var(--text-sm);
  font-weight: 500;
  transition: background-color 0.15s, color 0.15s;
}

.nav-item :deep(svg) {
  transition: transform 0.18s ease;
}

.nav-item:hover {
  background: var(--surface-hover);
  color: var(--text);
}

.nav-item:hover :deep(svg) {
  transform: translateX(2px);
}

.nav-item.active {
  background: var(--primary-soft);
  color: var(--primary-text);
}

/* A short bar marks the page shown */
.nav-item.active::before {
  content: "";
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: -10px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--primary);
  animation: nav-mark 0.25s ease both;
}

@keyframes nav-mark {
  from { transform: scaleY(0); }
  to { transform: scaleY(1); }
}

.external {
  margin-left: auto;
  color: var(--text-faint);
}

.user {
  border-top: 1px solid var(--border);
}

.backdrop {
  display: none;
}

@media (max-width: 960px) {
  .sidebar {
    transform: translateX(-100%);
    transition: transform 0.2s ease;
    box-shadow: var(--shadow-lg);
  }

  .sidebar.open {
    transform: none;
  }

  .backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 25;
    background: rgba(16, 24, 40, 0.35);
  }
}
</style>
