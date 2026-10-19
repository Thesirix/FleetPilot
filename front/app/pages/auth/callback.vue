<script setup lang="ts">
import type { User } from 'oidc-client-ts'
import { currentUser, login, userManager } from '~/utils/auth'

definePageMeta({ layout: 'blank' })

const failed = ref(false)

function returnTo(user: User): string {
  const state = user.state as { returnTo?: string } | undefined
  return state?.returnTo && state.returnTo.startsWith('/') ? state.returnTo : '/'
}

onMounted(async () => {
  try {
    const user = await userManager().signinRedirectCallback()
    await navigateTo(returnTo(user), { replace: true })
  } catch {
    // The page can run twice for one sign-in: on a first visit the service worker of the PWA takes control and
    // reloads it, after the code was already exchanged. The user is then signed in: go on instead of failing.
    const user = await currentUser()
    if (user) {
      await navigateTo(returnTo(user), { replace: true })
    } else {
      failed.value = true
    }
  }
})
</script>

<template>
  <div class="callback">
    <div class="card callback-card">
      <AppLogo />
      <template v-if="!failed">
        <span class="spinner" aria-hidden="true" />
        <p class="muted">{{ $t('auth.redirecting') }}</p>
      </template>
      <template v-else>
        <p>{{ $t('auth.failed') }}</p>
        <button class="btn btn-primary" @click="login('/')">{{ $t('auth.tryAgain') }}</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.callback {
  min-height: 100vh;
  display: grid;
  place-items: center;
}

.callback-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 32px 48px;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
