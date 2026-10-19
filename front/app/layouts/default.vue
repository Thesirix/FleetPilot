<script setup lang="ts">
import { Menu } from 'lucide-vue-next'

const menuOpen = ref(false)
</script>

<template>
  <div class="shell">
    <AppSidebar v-model:open="menuOpen" />
    <div class="main">
      <header class="mobile-bar">
        <button class="btn btn-ghost btn-icon" :aria-label="$t('nav.fleet')" @click="menuOpen = true">
          <Menu :size="20" />
        </button>
        <NuxtLink to="/"><AppLogo /></NuxtLink>
      </header>
      <main class="content">
        <slot />
      </main>
    </div>
    <AppToaster />
  </div>
</template>

<style scoped>
.main {
  margin-left: var(--sidebar-width);
  min-height: 100vh;
}

.content {
  max-width: var(--content-max);
  margin: 0 auto;
  padding: 28px 32px 48px;
}

.mobile-bar {
  display: none;
}

@media (max-width: 960px) {
  .main {
    margin-left: 0;
  }

  .mobile-bar {
    position: sticky;
    top: 0;
    z-index: 20;
    display: flex;
    align-items: center;
    gap: 8px;
    height: var(--topbar-height);
    padding: 0 12px;
    background: var(--surface);
    border-bottom: 1px solid var(--border);
  }

  .content {
    padding: 20px 16px 40px;
  }
}
</style>
