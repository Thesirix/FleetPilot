<script setup lang="ts">
import { Download, FileUp } from 'lucide-vue-next'
import { Q, type Company } from '~/graphql/fleet'
import { accessToken } from '~/utils/auth'

interface ImportReport {
  total: number
  created: number
  rejected: { line: number; registration: string | null; errors: string[] }[]
}

const { t } = useI18n()
const session = useSessionStore()
useHead({ title: () => `${t('import.title')} · FleetPilot` })

const { data: companies } = useGraphqlQuery<{ companies: Company[] }>(Q.companies, {}, { cacheKey: 'companies' })
const companyId = ref('')
watch(() => companies.value?.companies, (list) => {
  if (list?.length && !companyId.value) companyId.value = session.viewer?.companyId ?? list[0]!.id
}, { immediate: true })

const COLUMNS = 'registration;vin;category;make;model;trim;energy;crit_air;co2;fiscal_power;first_registration;acquisition;mileage;site'
const TEMPLATE = `${COLUMNS}
GH-482-KM;VF1RJA00X68123456;VL;Renault;Megane E-Tech;EV60 Techno;Électrique;E;0;4;15/03/2025;LLD;18250;
GH-517-PR;VR3UPHNSKS1234567;VU;Peugeot;e-Expert;Standard;Électrique;E;0;5;02/06/2025;LLD;9400;
`
function downloadTemplate() {
  const url = URL.createObjectURL(new Blob(['﻿' + TEMPLATE], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'fleetpilot-vehicules.csv'
  link.click()
  URL.revokeObjectURL(url)
}

const file = ref<File | null>(null)
const report = ref<ImportReport | null>(null)
const running = ref(false)
const toast = useToast()

async function upload() {
  if (!file.value) return
  running.value = true
  report.value = null
  try {
    const body = new FormData()
    body.append('file', file.value)
    const response = await fetch(`/api/v1/vehicles/import?companyId=${companyId.value}`, {
      method: 'POST', body, headers: { Authorization: `Bearer ${await accessToken()}` },
    })
    const json = await response.json()
    if (!response.ok) {
      toast.error(json.detail ?? t('app.unknownError'), json.correlationId)
      return
    }
    report.value = json as ImportReport
    toast.success(t('import.report', { created: report.value.created, total: report.value.total }, report.value.created))
  } catch {
    toast.error(t('app.unknownError'))
  } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="narrow">
    <UiPageHeader :title="$t('import.title')" :subtitle="$t('import.subtitle')">
      <template #actions>
        <button class="btn" @click="downloadTemplate"><Download /> {{ $t('import.template') }}</button>
      </template>
    </UiPageHeader>

    <section class="card">
      <div class="card-body stack">
        <div class="grid grid-2">
          <div class="field">
            <label for="company">{{ $t('import.company') }}</label>
            <select id="company" v-model="companyId" class="select" :disabled="!session.isStaff">
              <option v-for="c in companies?.companies ?? []" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="field">
            <label for="file">{{ $t('import.file') }}</label>
            <input id="file" type="file" accept=".csv,text/csv" class="input file"
              @change="file = ($event.target as HTMLInputElement).files?.[0] ?? null">
          </div>
        </div>
        <p class="field-hint">{{ $t('import.fileHint') }}</p>
        <div class="field">
          <span class="field-label">{{ $t('import.columns') }}</span>
          <code class="columns">{{ COLUMNS }}</code>
        </div>
        <div class="row actions">
          <button class="btn btn-primary" :disabled="!file || running" @click="upload"><FileUp /> {{ $t('import.run') }}</button>
        </div>
      </div>
    </section>

    <section v-if="report" class="card result">
      <header class="card-header">
        <h2>{{ $t('import.report', { created: report.created, total: report.total }, report.created) }}</h2>
        <span class="badge" :class="report.rejected.length ? 'warning' : 'success'">
          {{ report.rejected.length }} {{ $t('import.rejected').toLowerCase() }}
        </span>
      </header>
      <UiDataTable v-if="report.rejected.length" row-key="line" :rows="report.rejected" :columns="[
        { key: 'line', label: $t('import.line'), width: '80px' },
        { key: 'registration', label: $t('vehicle.registration') },
        { key: 'errors', label: $t('import.errors') },
      ]">
        <template #cell-registration="{ row }"><span class="mono">{{ row.registration ?? '-' }}</span></template>
        <template #cell-errors="{ row }">
          <ul class="errors"><li v-for="e in row.errors" :key="e">{{ e }}</li></ul>
        </template>
      </UiDataTable>
    </section>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 980px;
}

.file {
  padding: 5px 8px;
  height: auto;
}

.columns {
  display: block;
  padding: 10px 12px;
  border-radius: var(--radius);
  background: var(--surface-muted);
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  overflow-x: auto;
  white-space: nowrap;
}

.actions {
  justify-content: flex-end;
}

.result {
  margin-top: 16px;
}

.errors {
  margin: 0;
  padding-left: 16px;
  color: var(--danger);
}
</style>
