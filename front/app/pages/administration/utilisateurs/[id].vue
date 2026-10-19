<script setup lang="ts">
import { KeyRound, Lock, Pencil, Power } from 'lucide-vue-next'
import { AM, AQ, type User } from '~/graphql/admin'
import type { UserFormValue } from '~/components/admin/UserForm.vue'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
const id = computed(() => route.params.id as string)

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ user: User | null }>(AQ.user, () => ({ id: id.value }))
const u = computed(() => data.value?.user ?? null)
useHead({ title: () => (u.value ? `${u.value.fullName} · FleetPilot` : 'FleetPilot') })
const editable = computed(() => session.isAdmin && u.value && !u.value.federated
  && u.value.username.toLowerCase() !== session.viewer?.username?.toLowerCase())

const { run, running, fieldErrors } = useMutation()
type Dialog = 'edit' | 'password' | 'toggle' | null
const dialog = ref<Dialog>(null)
const open = computed({ get: () => dialog.value !== null, set: (v: boolean) => { if (!v) { dialog.value = null; password.value = null } } })

const form = ref<UserFormValue | null>(null)
function toForm(x: User, enabled = x.enabled): UserFormValue {
  return { email: x.email ?? '', firstName: x.firstName ?? '', lastName: x.lastName ?? '', role: x.role ?? 'client-manager',
    companyId: x.companyId ?? '', driverId: x.driverId ?? '', garageId: x.garageId ?? '', lessor: x.lessor ?? '', enabled }
}
function edit() {
  form.value = toForm(u.value!)
  dialog.value = 'edit'
}
async function save() {
  const result = await run(AM.updateUser, { id: id.value, input: compact({ ...form.value! }) },
    t('users.updated', { name: `${form.value!.firstName} ${form.value!.lastName}` }))
  if (result) {
    dialog.value = null
    await refresh()
  }
}
async function toggle() {
  const enabled = !u.value!.enabled
  const result = await run(AM.updateUser, { id: id.value, input: compact({ ...toForm(u.value!, enabled) }) },
    enabled ? t('users.enabledToast', { name: u.value!.fullName }) : t('users.disabledToast', { name: u.value!.fullName }))
  if (result) {
    dialog.value = null
    await refresh()
  }
}
const password = ref<string | null>(null)
async function resetPassword() {
  const result = await run<{ resetUserPassword: string }>(AM.resetUserPassword, { id: id.value })
  if (result) password.value = result.resetUserPassword
}
const attachment = computed(() => {
  const x = u.value
  if (!x) return []
  const items: { label: string; value: string; to?: string }[] = []
  if (x.companyName) items.push({ label: t('users.company'), value: x.companyName, to: `/administration/entreprises/${x.companyId}` })
  if (x.driverId) items.push({ label: t('users.driver'), value: x.driverName ?? x.driverId, to: `/conducteurs/${x.driverId}` })
  if (x.garage) items.push({ label: t('users.garage'), value: `${x.garage.name} · ${x.garage.city}`, to: `/garages/${x.garage.id}` })
  if (x.lessor) items.push({ label: t('users.lessor'), value: x.lessor })
  return items
})
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !u" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />

    <template v-if="u">
      <UiPageHeader :title="u.fullName" :subtitle="u.email ?? u.username" back="/administration/utilisateurs">
        <template #badges>
          <span class="badge" :class="u.enabled ? 'success' : 'danger'">{{ u.enabled ? $t('users.enabled') : $t('users.disabled') }}</span>
          <span v-if="u.role" class="badge plain">{{ $t(`roles.${u.role}`) }}</span>
        </template>
        <template v-if="editable" #actions>
          <button class="btn" @click="edit"><Pencil /> {{ $t('vehicle.edit') }}</button>
          <button class="btn" @click="dialog = 'password'"><KeyRound /> {{ $t('users.resetPassword') }}</button>
          <button class="btn" @click="dialog = 'toggle'"><Power /> {{ u.enabled ? $t('users.disable') : $t('users.enable') }}</button>
        </template>
      </UiPageHeader>

      <p v-if="u.federated" class="banner small"><Lock :size="14" /> {{ $t('users.ldapText') }}</p>

      <div class="grid grid-2">
        <section class="card">
          <header class="card-header"><h2>{{ $t('users.account') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('users.login') }}</dt><dd class="mono">{{ u.username }}</dd>
              <dt>{{ $t('users.email') }}</dt><dd>{{ u.email ?? '-' }}</dd>
              <dt>{{ $t('users.role') }}</dt><dd>{{ u.role ? $t(`roles.${u.role}`) : '-' }}</dd>
              <dt>{{ $t('users.source') }}</dt><dd>{{ u.federated ? $t('users.fromLdap') : $t('users.fromFleetPilot') }}</dd>
              <dt>{{ $t('users.createdAt') }}</dt><dd>{{ f.dateTime(u.createdAt) }}</dd>
            </dl>
          </div>
        </section>
        <section class="card">
          <header class="card-header"><h2>{{ $t('users.attachment') }}</h2></header>
          <div class="card-body">
            <dl v-if="attachment.length" class="dl">
              <template v-for="item in attachment" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd><NuxtLink v-if="item.to" :to="item.to">{{ item.value }}</NuxtLink><span v-else>{{ item.value }}</span></dd>
              </template>
            </dl>
            <p v-else class="muted small">{{ u.role?.startsWith('platform-') || u.role === 'fleet-manager' ? $t('users.staffAttachment') : '-' }}</p>
            <p class="xs muted hint">{{ u.role ? $t(`users.roleHint.${u.role}`) : '' }}</p>
          </div>
        </section>
      </div>

      <UiModal v-if="dialog === 'edit' && form" v-model:open="open" :title="$t('users.editTitle')" width="680px">
        <UserForm v-model="form" form-id="user-edit" edit :field-errors="fieldErrors" @submit="save" />
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button type="submit" form="user-edit" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'password'" v-model:open="open" :title="$t('users.resetPassword')" width="480px">
        <TemporaryPassword v-if="password" :password="password" :username="u.username" />
        <p v-else class="small">{{ $t('users.resetText', { name: u.fullName }) }}</p>
        <template #footer>
          <template v-if="password"><button class="btn btn-primary" @click="open = false">{{ $t('users.done') }}</button></template>
          <template v-else>
            <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
            <button class="btn btn-primary" :disabled="running" @click="resetPassword">{{ $t('users.resetPassword') }}</button>
          </template>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'toggle'" v-model:open="open" :title="u.enabled ? $t('users.disable') : $t('users.enable')" width="460px">
        <p class="small">{{ u.enabled ? $t('users.disableText', { name: u.fullName }) : $t('users.enableText', { name: u.fullName }) }}</p>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button class="btn" :class="u.enabled ? 'btn-danger' : 'btn-primary'" :disabled="running" @click="toggle">
            {{ u.enabled ? $t('users.disable') : $t('users.enable') }}
          </button>
        </template>
      </UiModal>
    </template>
  </div>
</template>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: var(--radius);
  background: var(--info-soft);
  color: var(--text-secondary);
}

.hint {
  margin-top: 12px;
}
</style>
