<script setup lang="ts">
import { Pencil } from 'lucide-vue-next'
import { Q, type DriverFile } from '~/graphql/fleet'

const route = useRoute()
const f = useFormat()
const id = computed(() => route.params.id as string)
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ driver: DriverFile | null }>(Q.driver,
  () => ({ id: id.value }), { cacheKey: () => `driver:${id.value}` })
const file = computed(() => data.value?.driver ?? null)
const d = computed(() => file.value?.driver ?? null)
useHead({ title: () => (d.value ? `${d.value.fullName} · FleetPilot` : 'FleetPilot') })
const today = new Date().toISOString().slice(0, 10)
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !file" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="file && d">
      <UiPageHeader :title="d.fullName" :subtitle="`${d.employeeNumber} · ${d.email}`" back="/conducteurs">
        <template #badges>
          <span class="badge" :class="d.active ? 'success' : ''">{{ d.active ? $t('driver.active') : $t('driver.inactive') }}</span>
          <span v-if="d.licenseExpiresOn && d.licenseExpiresOn < today" class="badge danger">{{ $t('driver.licenseExpired') }}</span>
        </template>
        <template #actions>
          <NuxtLink :to="`/conducteurs/${d.id}/modifier`" class="btn"><Pencil /> {{ $t('vehicle.edit') }}</NuxtLink>
        </template>
      </UiPageHeader>

      <div class="grid grid-3">
        <section class="card">
          <header class="card-header"><h2>{{ $t('driver.vehicle') }}</h2></header>
          <div class="card-body">
            <NuxtLink v-if="d.currentVehicle" :to="`/vehicules/${d.currentVehicle.id}`"><VehicleCell :vehicle="d.currentVehicle" /></NuxtLink>
            <p v-else class="muted small">{{ $t('driver.noVehicle') }}</p>
          </div>
        </section>
        <section class="card">
          <header class="card-header"><h2>{{ $t('driver.license') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('driver.licenseNumber') }}</dt><dd class="mono">{{ file.licenseNumber }}</dd>
              <dt>{{ $t('driver.licenseCategories') }}</dt><dd>{{ d.licenseCategories.join(', ') }}</dd>
              <dt>{{ $t('driver.licenseIssuedOn') }}</dt><dd>{{ f.date(file.licenseIssuedOn) }}</dd>
              <dt>{{ $t('driver.licenseExpiresOn') }}</dt><dd>{{ f.date(d.licenseExpiresOn) }}</dd>
            </dl>
          </div>
        </section>
        <section class="card">
          <header class="card-header"><h2>{{ $t('vehicle.fleet') }}</h2></header>
          <div class="card-body">
            <dl class="dl">
              <dt>{{ $t('vehicle.site') }}</dt><dd>{{ d.site?.name ?? '-' }}</dd>
              <dt>{{ $t('driver.phone') }}</dt><dd>{{ d.phone ?? '-' }}</dd>
              <dt>{{ $t('driver.drivenKm') }}</dt><dd class="num">{{ f.km(file.drivenKilometres) }}</dd>
            </dl>
          </div>
        </section>
      </div>

      <section class="card block">
        <header class="card-header"><h2>{{ $t('vehicle.assignments') }}</h2></header>
        <UiDataTable :rows="file.assignments" :empty-text="$t('driver.noVehicle')" clickable :columns="[
          { key: 'vehicle', label: $t('vehicle.vehicle') },
          { key: 'kind', label: $t('vehicle.assignmentKind') },
          { key: 'period', label: $t('contract.duration') },
          { key: 'km', label: $t('driver.drivenKm'), align: 'right' },
        ]" @row-click="row => navigateTo(`/vehicules/${row.vehicle.id}`)">
          <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
          <template #cell-kind="{ row }">{{ f.label('AssignmentKind', row.kind) }}</template>
          <template #cell-period="{ row }">{{ f.date(row.startDate) }} → {{ row.endDate ? f.date(row.endDate) : '…' }}</template>
          <template #cell-km="{ row }"><span class="num">{{ f.km(row.drivenKilometres) }}</span></template>
        </UiDataTable>
      </section>

      <section class="card block">
        <header class="card-header"><h2>{{ $t('vehicle.incidents') }}</h2></header>
        <UiDataTable :rows="file.incidents" :empty-text="$t('incident.empty')" clickable :columns="[
          { key: 'reference', label: $t('incident.reference') },
          { key: 'vehicle', label: $t('vehicle.vehicle') },
          { key: 'type', label: $t('incident.type') },
          { key: 'occurredAt', label: $t('incident.occurredAt') },
          { key: 'status', label: $t('incident.status') },
        ]" @row-click="row => navigateTo(`/sinistres/${row.id}`)">
          <template #cell-reference="{ row }"><span class="mono">{{ row.reference }}</span></template>
          <template #cell-vehicle="{ row }"><VehicleCell :vehicle="row.vehicle" /></template>
          <template #cell-type="{ row }">{{ f.label('IncidentType', row.type) }}</template>
          <template #cell-occurredAt="{ row }">{{ f.date(row.occurredAt) }}</template>
          <template #cell-status="{ row }"><FleetBadge type="IncidentStatus" :value="row.status" /></template>
        </UiDataTable>
      </section>
    </template>
  </div>
</template>

<style scoped>
.block {
  margin-top: 16px;
}
</style>
