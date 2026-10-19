<script setup lang="ts">
import { ChevronsUpDown, Languages, LogOut, UserRound } from 'lucide-vue-next'
import { logout } from '~/utils/auth'

const session = useSessionStore()
const { locale, locales, setLocale, t } = useI18n()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function onClickOutside(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))

const roleLabel = computed(() => (session.mainRole ? t(`roles.${session.mainRole}`) : ''))
</script>

<template>
  <div ref="root" class="user-menu">
    <button class="trigger" :aria-expanded="open" aria-haspopup="menu" @click="open = !open">
      <span class="avatar">{{ session.initials }}</span>
      <span class="who">
        <span class="name">{{ session.displayName }}</span>
        <span class="role">{{ roleLabel }}</span>
      </span>
      <ChevronsUpDown :size="15" class="muted" />
    </button>
    <div v-if="open" class="menu card" role="menu">
      <NuxtLink to="/profil" class="menu-item" role="menuitem" @click="open = false"><UserRound :size="15" /> {{ $t('profile.title') }}</NuxtLink>
      <hr class="divider">
      <p class="menu-label"><Languages :size="14" /> {{ $t('app.language') }}</p>
      <button v-for="l in locales" :key="l.code" class="menu-item" role="menuitemradio"
        :aria-checked="locale === l.code" @click="setLocale(l.code); open = false">
        {{ l.name }}<span v-if="locale === l.code" class="check">✓</span>
      </button>
      <hr class="divider">
      <button class="menu-item" role="menuitem" @click="logout()">
        <LogOut :size="15" /> {{ $t('app.logout') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.user-menu {
  position: relative;
  padding: 10px;
}

.trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px;
  border: none;
  border-radius: var(--radius);
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.trigger:hover {
  background: var(--surface-hover);
}

.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--primary-soft);
  color: var(--primary-text);
  font-size: 12px;
  font-weight: 650;
}

.who {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.name,
.role {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.name {
  font-size: var(--text-sm);
  font-weight: 550;
}

.role {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.menu {
  position: absolute;
  bottom: calc(100% - 4px);
  left: 10px;
  right: 10px;
  padding: 6px;
  box-shadow: var(--shadow-lg);
}

.menu-label {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 8px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font: inherit;
  font-size: var(--text-sm);
  text-align: left;
  cursor: pointer;
}

.menu-item:hover {
  background: var(--surface-hover);
}

.check {
  margin-left: auto;
  color: var(--primary);
}

.divider {
  margin: 6px 0;
}
</style>
