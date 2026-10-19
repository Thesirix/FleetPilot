<script setup lang="ts">
import { ArrowRight, Building2, LogOut } from 'lucide-vue-next'
import { logout } from '~/utils/auth'

/**
 * A signed-in account that has nothing to do in the portal: a lessor (role api-supplier) works in the lessor extranet.
 * Instead of a dashboard the services would refuse, the account is told where to go.
 */
definePageMeta({ layout: 'blank' })

const { t } = useI18n()
const session = useSessionStore()
const config = useRuntimeConfig()
useHead({ title: () => `${t('access.title')} · FleetPilot` })

const firstName = computed(() => session.displayName.split(/[\s.@]+/)[0] ?? '')
</script>

<template>
  <div class="access">
    <section class="panel">
      <AppLogo />
      <p class="hello">{{ $t('access.hello', { name: firstName }) }}</p>
      <h1>{{ session.isLessor ? $t('access.lessorTitle') : $t('access.title') }}</h1>
      <p class="text">{{ session.isLessor ? $t('access.lessorText') : $t('access.text') }}</p>
      <div class="actions">
        <a v-if="session.isLessor" :href="config.public.extranetUrl" class="btn btn-primary">
          <Building2 :size="16" /> {{ $t('access.openExtranet') }} <ArrowRight :size="16" />
        </a>
        <button class="btn" @click="logout()"><LogOut :size="16" /> {{ $t('access.otherAccount') }}</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.access {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px 16px;
  background:
    radial-gradient(600px 400px at 15% 10%, rgba(15, 118, 110, 0.12), transparent 70%),
    radial-gradient(500px 360px at 90% 90%, rgba(94, 234, 212, 0.18), transparent 70%),
    var(--bg);
}

.panel {
  width: 100%;
  max-width: 480px;
  padding: 32px;
  border: 1px solid var(--border);
  border-top: 3px solid var(--primary);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-lg);
  animation: rise 0.45s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.hello {
  margin: 24px 0 2px;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

h1 {
  margin: 0 0 10px;
  font-size: var(--text-xl);
  letter-spacing: -0.01em;
}

.text {
  margin: 0 0 24px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
