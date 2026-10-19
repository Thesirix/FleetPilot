<script setup lang="ts">
import { AM, type User } from '~/graphql/admin'
import type { UserFormValue } from '~/components/admin/UserForm.vue'

const route = useRoute()
const { t } = useI18n()
useHead({ title: () => `${t('users.new')} · FleetPilot` })

const form = ref<UserFormValue>({
  email: '', firstName: '', lastName: '', role: 'client-manager',
  companyId: typeof route.query.companyId === 'string' ? route.query.companyId : '', driverId: '', garageId: '',
  lessor: '', enabled: true,
})
const created = ref<{ user: User; temporaryPassword: string } | null>(null)
const { run, running, fieldErrors } = useMutation()
async function submit() {
  const result = await run<{ createUser: { user: User; temporaryPassword: string } }>(AM.createUser,
    { input: compact({ ...form.value }) })
  if (result) {
    created.value = result.createUser
    useToast().success(t('users.created', { name: result.createUser.user.fullName }))
  }
}
const showPassword = computed({
  get: () => created.value !== null,
  set: (v: boolean) => {
    if (!v && created.value) navigateTo(`/administration/utilisateurs/${created.value.user.id}`)
  },
})
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('users.new')" :subtitle="$t('users.newSubtitle')" back="/administration/utilisateurs" />
    <section class="card">
      <div class="card-body">
        <UserForm v-model="form" form-id="user-form" :field-errors="fieldErrors" @submit="submit" />
      </div>
    </section>
    <div class="row actions">
      <NuxtLink to="/administration/utilisateurs" class="btn">{{ $t('app.cancel') }}</NuxtLink>
      <button type="submit" form="user-form" class="btn btn-primary" :disabled="running">{{ $t('users.createAccount') }}</button>
    </div>

    <UiModal v-if="created" v-model:open="showPassword" :title="$t('users.createdTitle')" width="480px">
      <TemporaryPassword :password="created.temporaryPassword" :username="created.user.username" />
      <template #footer>
        <button class="btn btn-primary" @click="showPassword = false">{{ $t('users.done') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}

.actions {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
