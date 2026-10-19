<script setup lang="ts">
import { DQ, type FuelEntry, type Inspection } from '~/graphql/driver'

/** What the drivers of a vehicle sent from their phone: fill-ups or inspections (with their photos). */
const props = defineProps<{ vehicleId: string; mode: 'fuel' | 'inspections' }>()
const { t } = useI18n()
const f = useFormat()

const { data, pending, error, stale, refresh } = useGraphqlQuery<{ vehicle: { fuelEntries: FuelEntry[]; inspections: Inspection[] } | null }>(
  DQ.vehicleDriverRecords, () => ({ id: props.vehicleId }), { cacheKey: () => `vehicle-driver-records:${props.vehicleId}` })
const fuel = computed(() => data.value?.vehicle?.fuelEntries ?? [])
const inspections = computed(() => data.value?.vehicle?.inspections ?? [])

const opened = ref<string | null>(null)
const viewing = ref<string | null>(null)
const viewerOpen = computed({ get: () => viewing.value !== null, set: (o: boolean) => { if (!o) viewing.value = null } })

defineExpose({ refresh })
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <section v-if="mode === 'fuel'" class="card">
      <UiDataTable :rows="fuel" :loading="pending && !data" :empty-text="$t('driverRecords.noFuel')" :columns="[
        { key: 'filledAt', label: t('driverRecords.date') },
        { key: 'driver', label: t('driverRecords.driver') },
        { key: 'quantity', label: t('driverRecords.quantity'), align: 'right' },
        { key: 'amount', label: t('driverRecords.amount'), align: 'right' },
        { key: 'unitPrice', label: t('driverRecords.unitPrice'), align: 'right' },
        { key: 'mileage', label: t('vehicle.mileage'), align: 'right' },
        { key: 'station', label: t('driverRecords.station') },
        { key: 'payment', label: t('driverRecords.payment') },
      ]">
        <template #cell-filledAt="{ row }"><span class="nowrap">{{ f.dateTime(row.filledAt) }}</span></template>
        <template #cell-driver="{ row }">{{ row.driver?.fullName ?? '-' }}</template>
        <template #cell-quantity="{ row }"><span class="num">{{ f.number(row.quantity, 2) }} {{ row.kind === 'CHARGE' ? 'kWh' : 'L' }}</span></template>
        <template #cell-amount="{ row }"><span class="num">{{ f.money(row.amount, 2) }}</span></template>
        <template #cell-unitPrice="{ row }"><span class="num">{{ f.number(row.unitPrice, 3) }} €</span></template>
        <template #cell-mileage="{ row }"><span class="num">{{ f.km(row.mileage) }}</span></template>
        <template #cell-station="{ row }"><span class="secondary">{{ row.station ?? '-' }}</span></template>
        <template #cell-payment="{ row }">
          <span class="badge" :class="{ warning: row.payment === 'PERSONAL' }">{{ $t(`driverApp.fuel.payments.${row.payment}`) }}</span>
        </template>
      </UiDataTable>
    </section>

    <section v-else class="card">
      <UiDataTable :rows="inspections" :loading="pending && !data" clickable :empty-text="$t('driverRecords.noInspection')"
        :columns="[
          { key: 'performedAt', label: t('driverRecords.date') },
          { key: 'kind', label: t('driverRecords.kind') },
          { key: 'driver', label: t('driverRecords.driver') },
          { key: 'mileage', label: t('vehicle.mileage'), align: 'right' },
          { key: 'fuelLevel', label: t('driverRecords.level'), align: 'right' },
          { key: 'defects', label: t('driverRecords.defects') },
          { key: 'photos', label: t('driverApp.photos.title'), align: 'right' },
        ]" @row-click="opened = opened === $event.id ? null : $event.id">
        <template #cell-performedAt="{ row }"><span class="nowrap">{{ f.dateTime(row.performedAt) }}</span></template>
        <template #cell-kind="{ row }">{{ $t(`driverApp.inspection.kinds.${row.kind}`) }}</template>
        <template #cell-driver="{ row }">{{ row.driver?.fullName ?? '-' }}</template>
        <template #cell-mileage="{ row }"><span class="num">{{ f.km(row.mileage) }}</span></template>
        <template #cell-fuelLevel="{ row }"><span class="num">{{ row.fuelLevel }} %</span></template>
        <template #cell-defects="{ row }">
          <span class="badge" :class="row.defects ? 'warning' : 'success'">{{ $t('driverApp.inspection.defects', row.defects) }}</span>
        </template>
        <template #cell-photos="{ row }"><span class="num">{{ row.photos.length }}</span></template>
      </UiDataTable>
      <template v-for="i in inspections" :key="i.id">
        <div v-if="opened === i.id" class="detail">
          <ul class="checks">
            <li v-for="c in i.checks" :key="c.item" :class="c.condition.toLowerCase()">
              <strong>{{ $t(`driverApp.inspection.items.${c.item}`) }}</strong>
              <span>{{ $t(`driverApp.inspection.conditions.${c.condition}`) }}</span>
              <span v-if="c.comment" class="muted">{{ c.comment }}</span>
            </li>
          </ul>
          <p v-if="i.remarks" class="small secondary">{{ i.remarks }}</p>
          <div v-if="i.photos.length" class="thumbs">
            <PhotoThumb v-for="p in i.photos" :key="p.id" :photo-id="p.id" :size="96" @open="viewing = $event" />
          </div>
        </div>
      </template>
    </section>

    <UiModal v-model:open="viewerOpen" :title="$t('driverApp.photos.photo')" width="820px">
      <img v-if="viewing" :src="viewing" class="full" alt="">
    </UiModal>
  </div>
</template>

<style scoped>
.badge.warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.badge.success {
  background: var(--success-soft);
  color: var(--success);
}

.detail {
  padding: 16px 18px;
  border-top: 1px solid var(--border);
  background: var(--surface-muted);
}

.checks {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 6px 16px;
  margin: 0 0 12px;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.checks li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-left: 10px;
  border-left: 3px solid var(--success);
}

.checks li.damaged {
  border-left-color: var(--warning);
}

.checks li.missing {
  border-left-color: var(--danger);
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.full {
  display: block;
  width: 100%;
  border-radius: var(--radius);
}
</style>
