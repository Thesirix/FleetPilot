<script setup lang="ts">
import { Building2, Car, FileSignature, MapPin, Pencil, Plus, Power, Trash2, Users } from 'lucide-vue-next'
import { AM, AQ, type AdminCompany, type AdminCostCenter, type AdminSite } from '~/graphql/admin'
import type { CompanyFormValue } from '~/components/admin/CompanyForm.vue'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const id = computed(() => route.params.id as string)

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ adminCompany: AdminCompany | null }>(AQ.company,
  () => ({ id: id.value }), { cacheKey: () => `admin-company:${id.value}` })
const a = computed(() => data.value?.adminCompany ?? null)
// The accounts of the company (client managers and drivers), counted in the directory
const accounts = useGraphqlQuery<{ users: { totalElements: number } }>(
  'query($filter: UserFilter) { users(filter: $filter, size: 1) { totalElements } }',
  () => ({ filter: { companyId: id.value } }))
const c = computed(() => a.value?.company ?? null)
useHead({ title: () => (c.value ? `${c.value.name} · FleetPilot` : 'FleetPilot') })

const { run, running, fieldErrors } = useMutation()
type Dialog = 'company' | 'site' | 'costCenter' | 'activation' | 'deleteSite' | 'deleteCostCenter' | null
const dialog = ref<Dialog>(null)
const open = computed({ get: () => dialog.value !== null, set: (v: boolean) => { if (!v) dialog.value = null } })

async function done(result: unknown) {
  if (result) {
    dialog.value = null
    await refresh()
  }
}

// ---------------------------------------------------------------- company
const companyForm = ref<CompanyFormValue | null>(null)
function editCompany() {
  const x = c.value!
  companyForm.value = { name: x.name, legalName: x.legalName, siren: x.siren, segment: x.segment, address: x.address ?? '',
    postalCode: x.postalCode ?? '', city: x.city, contactName: x.contactName ?? '', contactEmail: x.contactEmail ?? '',
    contactPhone: x.contactPhone ?? '' }
  dialog.value = 'company'
}
async function saveCompany() {
  await done(await run(AM.updateCompany, { id: id.value, input: compact({ ...companyForm.value!, version: c.value!.version }) },
    t('companies.updated', { name: companyForm.value!.name })))
}
async function toggleActive() {
  const active = !c.value!.active
  await done(await run(AM.setCompanyActive, { id: id.value, active },
    active ? t('companies.activated', { name: c.value!.name }) : t('companies.deactivated', { name: c.value!.name })))
}

// ---------------------------------------------------------------- sites
const site = reactive({ id: '', name: '', address: '', postalCode: '', city: '', latitude: null as number | null,
  longitude: null as number | null, version: 0 })
function editSite(row?: { site: AdminSite; version: number }) {
  Object.assign(site, row
    ? { ...row.site, version: row.version }
    : { id: '', name: '', address: '', postalCode: '', city: '', latitude: null, longitude: null, version: 0 })
  dialog.value = 'site'
}
async function saveSite() {
  const input = { name: site.name, address: site.address, postalCode: site.postalCode, city: site.city,
    latitude: Number(site.latitude), longitude: Number(site.longitude), version: site.id ? site.version : null }
  const result = site.id
    ? await run(AM.updateSite, { id: site.id, input }, t('companies.siteSaved', { name: site.name }))
    : await run(AM.createSite, { companyId: id.value, input }, t('companies.siteSaved', { name: site.name }))
  await done(result)
}
function askDeleteSite(row: { site: AdminSite }) {
  Object.assign(site, row.site)
  dialog.value = 'deleteSite'
}
async function deleteSite() {
  await done(await run(AM.deleteSite, { id: site.id }, t('companies.siteDeleted', { name: site.name })))
}

// ---------------------------------------------------------------- cost centers
const costCenter = reactive({ id: '', code: '', label: '', version: 0 })
function editCostCenter(row?: { costCenter: AdminCostCenter; version: number }) {
  Object.assign(costCenter, row ? { ...row.costCenter, version: row.version } : { id: '', code: '', label: '', version: 0 })
  dialog.value = 'costCenter'
}
async function saveCostCenter() {
  const input = { code: costCenter.code, label: costCenter.label, version: costCenter.id ? costCenter.version : null }
  const result = costCenter.id
    ? await run(AM.updateCostCenter, { id: costCenter.id, input }, t('companies.costCenterSaved', { code: costCenter.code }))
    : await run(AM.createCostCenter, { companyId: id.value, input }, t('companies.costCenterSaved', { code: costCenter.code }))
  await done(result)
}
function askDeleteCostCenter(row: { costCenter: AdminCostCenter }) {
  Object.assign(costCenter, row.costCenter)
  dialog.value = 'deleteCostCenter'
}
async function deleteCostCenter() {
  await done(await run(AM.deleteCostCenter, { id: costCenter.id }, t('companies.costCenterDeleted', { code: costCenter.code })))
}
const err = (field: string) => fieldErrors.value[field]
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !a" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />

    <template v-if="a && c">
      <UiPageHeader :title="c.name" :subtitle="`${c.legalName} · SIREN ${c.siren}`" back="/administration/entreprises">
        <template #badges>
          <span class="badge" :class="c.active ? 'success' : ''">{{ c.active ? $t('companies.active') : $t('companies.inactive') }}</span>
          <span class="badge plain">{{ f.label('CompanySegment', c.segment) }}</span>
        </template>
        <template #actions>
          <button class="btn" @click="editCompany"><Pencil /> {{ $t('vehicle.edit') }}</button>
          <button class="btn" :class="{ 'btn-primary': !c.active }" @click="dialog = 'activation'">
            <Power /> {{ c.active ? $t('companies.deactivate') : $t('companies.activate') }}
          </button>
        </template>
      </UiPageHeader>

      <div class="grid grid-4 kpis">
        <UiKpi :label="$t('companies.vehicles')" :value="f.number(a.activeVehicles)" :icon="Car"
          :hint="$t('companies.vehiclesHint', { all: f.number(a.vehicles) })" :to="`/vehicules?companyId=${c.id}`" />
        <UiKpi :label="$t('companies.activeDrivers')" :value="f.number(a.drivers)" :icon="Users" :to="`/conducteurs?companyId=${c.id}`" />
        <UiKpi :label="$t('companies.contracts')" :value="f.number(a.activeContracts)" :icon="FileSignature"
          :to="`/contrats?companyId=${c.id}`" />
        <UiKpi :label="$t('companies.users')" :value="accounts.data.value ? f.number(accounts.data.value.users.totalElements) : '-'"
          :hint="$t('companies.seeUsers')" :icon="Building2" :to="`/administration/utilisateurs?companyId=${c.id}`" />
      </div>

      <div class="layout">
        <div class="stack">
          <section class="card">
            <header class="card-header">
              <h2>{{ $t('companies.sites') }}</h2>
              <button class="btn btn-sm" @click="editSite()"><Plus /> {{ $t('companies.addSite') }}</button>
            </header>
            <div class="card-body flush">
              <UiDataTable :rows="a.siteUsage ?? []" row-key="site.id" :empty-text="$t('companies.noSite')" :columns="[
                { key: 'name', label: $t('companies.siteName') },
                { key: 'address', label: $t('companies.address') },
                { key: 'vehicles', label: $t('companies.vehicles'), align: 'right' },
                { key: 'drivers', label: $t('companies.drivers'), align: 'right' },
                { key: 'actions', label: '', align: 'right' },
              ]">
                <template #cell-name="{ row }"><span class="strong"><MapPin :size="14" class="muted" /> {{ row.site.name }}</span></template>
                <template #cell-address="{ row }">
                  <span class="secondary small">{{ row.site.address }}, {{ row.site.postalCode }} {{ row.site.city }}</span>
                </template>
                <template #cell-vehicles="{ row }"><span class="num">{{ f.number(row.vehicles) }}</span></template>
                <template #cell-drivers="{ row }"><span class="num">{{ f.number(row.drivers) }}</span></template>
                <template #cell-actions="{ row }">
                  <div class="row-actions">
                    <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('vehicle.edit')" @click="editSite(row)"><Pencil /></button>
                    <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('companies.delete')"
                      :disabled="row.vehicles + row.drivers > 0" :title="row.vehicles + row.drivers > 0 ? $t('companies.siteInUse') : ''"
                      @click="askDeleteSite(row)"><Trash2 /></button>
                  </div>
                </template>
              </UiDataTable>
            </div>
          </section>

          <section class="card">
            <header class="card-header">
              <h2>{{ $t('companies.costCenters') }}</h2>
              <button class="btn btn-sm" @click="editCostCenter()"><Plus /> {{ $t('companies.addCostCenter') }}</button>
            </header>
            <div class="card-body flush">
              <UiDataTable :rows="a.costCenterUsage ?? []" row-key="costCenter.id" :empty-text="$t('companies.noCostCenter')"
                :columns="[
                  { key: 'code', label: $t('companies.code') },
                  { key: 'label', label: $t('companies.label') },
                  { key: 'vehicles', label: $t('companies.vehicles'), align: 'right' },
                  { key: 'actions', label: '', align: 'right' },
                ]">
                <template #cell-code="{ row }"><span class="mono">{{ row.costCenter.code }}</span></template>
                <template #cell-label="{ row }">{{ row.costCenter.label }}</template>
                <template #cell-vehicles="{ row }"><span class="num">{{ f.number(row.vehicles) }}</span></template>
                <template #cell-actions="{ row }">
                  <div class="row-actions">
                    <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('vehicle.edit')" @click="editCostCenter(row)"><Pencil /></button>
                    <button class="btn btn-ghost btn-sm btn-icon" :aria-label="$t('companies.delete')" :disabled="row.vehicles > 0"
                      :title="row.vehicles > 0 ? $t('companies.costCenterInUse') : ''" @click="askDeleteCostCenter(row)"><Trash2 /></button>
                  </div>
                </template>
              </UiDataTable>
            </div>
          </section>
        </div>

        <section class="card">
          <header class="card-header"><h2>{{ $t('companies.details') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('companies.legalName') }}</dt><dd>{{ c.legalName }}</dd>
              <dt>{{ $t('companies.siren') }}</dt><dd class="mono">{{ c.siren }}</dd>
              <dt>{{ $t('companies.segment') }}</dt><dd>{{ f.label('CompanySegment', c.segment) }}</dd>
              <dt>{{ $t('companies.headOffice') }}</dt>
              <dd>
                <template v-if="c.address">{{ c.address }}<br></template>{{ c.postalCode }} {{ c.city }}
              </dd>
              <dt>{{ $t('companies.contact') }}</dt>
              <dd>
                <template v-if="c.contactName">
                  {{ c.contactName }}<br>
                  <a v-if="c.contactEmail" :href="`mailto:${c.contactEmail}`">{{ c.contactEmail }}</a>
                  <template v-if="c.contactPhone"><br>{{ c.contactPhone }}</template>
                </template>
                <span v-else class="muted">-</span>
              </dd>
            </dl>
          </div>
        </section>
      </div>

      <UiModal v-if="dialog === 'company' && companyForm" v-model:open="open" :title="$t('companies.editTitle')" width="760px">
        <CompanyForm v-model="companyForm" form-id="company-edit" :field-errors="fieldErrors" @submit="saveCompany" />
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button type="submit" form="company-edit" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'activation'" v-model:open="open" :title="c.active ? $t('companies.deactivate') : $t('companies.activate')"
        width="480px">
        <p class="small">{{ c.active ? $t('companies.deactivateText', { name: c.name }) : $t('companies.activateText', { name: c.name }) }}</p>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button class="btn" :class="c.active ? 'btn-danger' : 'btn-primary'" :disabled="running" @click="toggleActive">
            {{ c.active ? $t('companies.deactivate') : $t('companies.activate') }}
          </button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'site'" v-model:open="open" :title="site.id ? $t('companies.editSite') : $t('companies.addSite')" width="620px">
        <form id="site-form" class="grid grid-2" @submit.prevent="saveSite">
          <div class="field wide">
            <label for="s-name">{{ $t('companies.siteName') }}</label>
            <input id="s-name" v-model="site.name" class="input" required maxlength="120" :placeholder="$t('companies.siteNameHint')"
              :aria-invalid="!!err('name')">
            <span v-if="err('name')" class="field-error">{{ err('name') }}</span>
          </div>
          <div class="field wide">
            <label for="s-address">{{ $t('companies.address') }}</label>
            <input id="s-address" v-model="site.address" class="input" required maxlength="200" :aria-invalid="!!err('address')">
            <span v-if="err('address')" class="field-error">{{ err('address') }}</span>
          </div>
          <div class="field">
            <label for="s-postal">{{ $t('companies.postalCode') }}</label>
            <input id="s-postal" v-model="site.postalCode" class="input mono" required maxlength="5" :aria-invalid="!!err('postalCode')">
            <span v-if="err('postalCode')" class="field-error">{{ err('postalCode') }}</span>
          </div>
          <div class="field">
            <label for="s-city">{{ $t('companies.city') }}</label>
            <input id="s-city" v-model="site.city" class="input" required maxlength="80" :aria-invalid="!!err('city')">
            <span v-if="err('city')" class="field-error">{{ err('city') }}</span>
          </div>
          <div class="field">
            <label for="s-lat">{{ $t('garage.latitude') }}</label>
            <input id="s-lat" v-model.number="site.latitude" type="number" step="0.000001" min="-90" max="90" class="input" required
              :aria-invalid="!!err('latitude')">
            <span v-if="err('latitude')" class="field-error">{{ err('latitude') }}</span>
          </div>
          <div class="field">
            <label for="s-lon">{{ $t('garage.longitude') }}</label>
            <input id="s-lon" v-model.number="site.longitude" type="number" step="0.000001" min="-180" max="180" class="input" required
              :aria-invalid="!!err('longitude')">
            <span v-if="err('longitude')" class="field-error">{{ err('longitude') }}</span>
            <span v-else class="field-hint">{{ $t('companies.positionHint') }}</span>
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button type="submit" form="site-form" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'costCenter'" v-model:open="open"
        :title="costCenter.id ? $t('companies.editCostCenter') : $t('companies.addCostCenter')" width="480px">
        <form id="cc-form" class="stack" @submit.prevent="saveCostCenter">
          <div class="field">
            <label for="cc-code">{{ $t('companies.code') }}</label>
            <input id="cc-code" v-model="costCenter.code" class="input mono" required maxlength="20" placeholder="NEO-DIR"
              :aria-invalid="!!err('code')">
            <span v-if="err('code')" class="field-error">{{ err('code') }}</span>
          </div>
          <div class="field">
            <label for="cc-label">{{ $t('companies.label') }}</label>
            <input id="cc-label" v-model="costCenter.label" class="input" required maxlength="120" :aria-invalid="!!err('label')">
            <span v-if="err('label')" class="field-error">{{ err('label') }}</span>
          </div>
        </form>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button type="submit" form="cc-form" class="btn btn-primary" :disabled="running">{{ $t('app.save') }}</button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'deleteSite'" v-model:open="open" :title="$t('companies.deleteSite')" width="440px">
        <p class="small">{{ $t('companies.deleteSiteText', { name: site.name }) }}</p>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button class="btn btn-danger" :disabled="running" @click="deleteSite">{{ $t('companies.delete') }}</button>
        </template>
      </UiModal>

      <UiModal v-if="dialog === 'deleteCostCenter'" v-model:open="open" :title="$t('companies.deleteCostCenter')" width="440px">
        <p class="small">{{ $t('companies.deleteCostCenterText', { code: costCenter.code, label: costCenter.label }) }}</p>
        <template #footer>
          <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
          <button class="btn btn-danger" :disabled="running" @click="deleteCostCenter">{{ $t('companies.delete') }}</button>
        </template>
      </UiModal>
    </template>
  </div>
</template>

<style scoped>
.kpis {
  margin-bottom: 16px;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.row-actions {
  display: inline-flex;
  gap: 2px;
}

.strong {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 540;
}

.wide {
  grid-column: 1 / -1;
}
</style>
