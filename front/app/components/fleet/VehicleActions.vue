<script setup lang="ts">
import { Gauge, Pencil, RefreshCw, ShieldAlert, Undo2, UserPlus } from 'lucide-vue-next'
import { M, Q, type DriverSummary, type Page, type Vehicle } from '~/graphql/fleet'
import { graphql } from '~/utils/graphql'

/** The actions of a vehicle file, each in its own dialog. Emits "changed" so the page reloads the file. */
const props = defineProps<{ vehicle: Vehicle }>()
const emit = defineEmits<{ changed: [] }>()
const { t } = useI18n()
const f = useFormat()
const { run, running, fieldErrors } = useMutation()

type Dialog = 'status' | 'mileage' | 'assign' | 'return' | 'incident' | null
const dialog = ref<Dialog>(null)
const today = new Date().toISOString().slice(0, 10)

// ---------------------------------------------------------------- status
const nextStatus = ref('')
function openStatus() {
  nextStatus.value = props.vehicle.allowedTransitions[0] ?? ''
  dialog.value = 'status'
}
async function changeStatus() {
  const done = await run(M.changeVehicleStatus, { id: props.vehicle.id, status: nextStatus.value },
    t('vehicle.statusChanged', { status: f.label('VehicleStatus', nextStatus.value) }))
  if (done) finish()
}

// ---------------------------------------------------------------- mileage
const mileage = ref(0)
function openMileage() {
  mileage.value = props.vehicle.mileage
  dialog.value = 'mileage'
}
async function recordMileage() {
  const done = await run(M.recordMileage, { id: props.vehicle.id, mileage: Number(mileage.value) }, t('vehicle.mileageRecorded'))
  if (done) finish()
}

// ---------------------------------------------------------------- assignment
const drivers = ref<DriverSummary[]>([])
const assignment = reactive({ driverId: '', kind: 'PERMANENT', startDate: today })
async function openAssign() {
  dialog.value = 'assign'
  const result = await graphql<{ drivers: Page<DriverSummary> }>(Q.drivers, {
    filter: { companyId: props.vehicle.company.id, active: true, assigned: false }, size: 100, sort: ['lastName,asc'],
  })
  drivers.value = result.data.drivers.content
  assignment.driverId = drivers.value[0]?.id ?? ''
}
async function assign() {
  const driver = drivers.value.find(d => d.id === assignment.driverId)
  const done = await run(M.assignVehicle, { vehicleId: props.vehicle.id, ...assignment },
    t('vehicle.assignedTo', { name: driver?.fullName ?? '' }))
  if (done) finish()
}

const endMileage = ref(0)
function openReturn() {
  endMileage.value = props.vehicle.mileage
  dialog.value = 'return'
}
async function endAssignment() {
  const done = await run(M.endAssignment, { id: props.vehicle.currentAssignment!.id, endMileage: Number(endMileage.value) },
    t('vehicle.returned'))
  if (done) finish()
}

// ---------------------------------------------------------------- incident
const incident = reactive({ type: 'COLLISION', occurredAt: '', location: '', description: '', thirdPartyInvolved: false,
  injuries: false })
function openIncident() {
  const now = new Date()
  now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
  incident.occurredAt = now.toISOString().slice(0, 16)
  dialog.value = 'incident'
}
async function declare() {
  const result = await run<{ declareIncident: { reference: string } }>(M.declareIncident, {
    vehicleId: props.vehicle.id,
    input: { ...incident, occurredAt: new Date(incident.occurredAt).toISOString() },
  })
  if (result) {
    useToast().success(t('incident.declared', { reference: result.declareIncident.reference }))
    finish()
  }
}

function finish() {
  dialog.value = null
  emit('changed')
}

const open = computed({ get: () => dialog.value !== null, set: (v: boolean) => { if (!v) dialog.value = null } })
const disposed = computed(() => props.vehicle.status === 'DISPOSED')
</script>

<template>
  <div class="row wrap">
    <NuxtLink v-if="!disposed" :to="`/vehicules/${vehicle.id}/modifier`" class="btn"><Pencil /> {{ $t('vehicle.edit') }}</NuxtLink>
    <button class="btn" :disabled="disposed" @click="openMileage"><Gauge /> {{ $t('vehicle.recordMileage') }}</button>
    <button v-if="vehicle.currentAssignment" class="btn" @click="openReturn"><Undo2 /> {{ $t('vehicle.returnVehicle') }}</button>
    <button v-else class="btn" :disabled="vehicle.status !== 'ACTIVE'" @click="openAssign"><UserPlus /> {{ $t('vehicle.assign') }}</button>
    <button class="btn" :disabled="disposed" @click="openIncident"><ShieldAlert /> {{ $t('vehicle.declareIncident') }}</button>
    <button class="btn" :disabled="vehicle.allowedTransitions.length === 0" @click="openStatus">
      <RefreshCw /> {{ $t('vehicle.changeStatus') }}
    </button>

    <UiModal v-if="dialog === 'status'" v-model:open="open" :title="$t('vehicle.changeStatus')">
      <div class="field">
        <label for="status">{{ $t('vehicle.status') }}</label>
        <select id="status" v-model="nextStatus" class="select">
          <option v-for="s in vehicle.allowedTransitions" :key="s" :value="s">{{ f.label('VehicleStatus', s) }}</option>
        </select>
      </div>
      <template #footer>
        <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running" @click="changeStatus">{{ $t('app.confirm') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="dialog === 'mileage'" v-model:open="open" :title="$t('vehicle.recordMileage')" width="420px">
      <div class="field">
        <label for="mileage">{{ $t('vehicle.newMileage') }}</label>
        <input id="mileage" v-model="mileage" type="number" min="0" class="input" :aria-invalid="!!fieldErrors.mileage">
        <span class="field-hint">{{ $t('vehicle.mileageReadAt', { date: f.dateTime(vehicle.mileageReadAt) }) }} :
          {{ f.km(vehicle.mileage) }}</span>
      </div>
      <template #footer>
        <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running" @click="recordMileage">{{ $t('app.save') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="dialog === 'assign'" v-model:open="open" :title="$t('vehicle.assign')">
      <div class="field">
        <label for="driver">{{ $t('vehicle.pickDriver') }}</label>
        <select id="driver" v-model="assignment.driverId" class="select">
          <option v-for="d in drivers" :key="d.id" :value="d.id">
            {{ d.lastName }} {{ d.firstName }} · {{ d.employeeNumber }} · {{ d.licenseCategories.join(', ') }}
          </option>
        </select>
        <span class="field-hint">{{ $t('vehicle.pickDriverHint') }}</span>
      </div>
      <div class="grid grid-2">
        <div class="field">
          <label for="kind">{{ $t('vehicle.assignmentKind') }}</label>
          <select id="kind" v-model="assignment.kind" class="select">
            <option value="PERMANENT">{{ f.label('AssignmentKind', 'PERMANENT') }}</option>
            <option value="TEMPORARY">{{ f.label('AssignmentKind', 'TEMPORARY') }}</option>
          </select>
        </div>
        <div class="field">
          <label for="start">{{ $t('vehicle.startDate') }}</label>
          <input id="start" v-model="assignment.startDate" type="date" class="input">
        </div>
      </div>
      <template #footer>
        <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running || !assignment.driverId" @click="assign">{{ $t('vehicle.assign') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="dialog === 'return'" v-model:open="open" :title="$t('vehicle.returnVehicle')" width="420px">
      <p class="small secondary">{{ vehicle.currentAssignment?.driver.fullName }}</p>
      <div class="field">
        <label for="endMileage">{{ $t('vehicle.endMileage') }}</label>
        <input id="endMileage" v-model="endMileage" type="number" min="0" class="input">
      </div>
      <template #footer>
        <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running" @click="endAssignment">{{ $t('vehicle.returnVehicle') }}</button>
      </template>
    </UiModal>

    <UiModal v-if="dialog === 'incident'" v-model:open="open" :title="$t('incident.declare')" width="560px">
      <div class="grid grid-2">
        <div class="field">
          <label for="itype">{{ $t('incident.type') }}</label>
          <select id="itype" v-model="incident.type" class="select">
            <option v-for="k in ['COLLISION', 'GLASS_BREAKAGE', 'MECHANICAL_FAILURE', 'VANDALISM', 'WEATHER', 'THEFT', 'FIRE']"
              :key="k" :value="k">{{ f.label('IncidentType', k) }}</option>
          </select>
        </div>
        <div class="field">
          <label for="when">{{ $t('incident.occurredAt') }}</label>
          <input id="when" v-model="incident.occurredAt" type="datetime-local" class="input">
        </div>
      </div>
      <div class="field">
        <label for="where">{{ $t('incident.location') }}</label>
        <input id="where" v-model="incident.location" class="input" :aria-invalid="!!fieldErrors.location">
      </div>
      <div class="field">
        <label for="what">{{ $t('incident.description') }}</label>
        <textarea id="what" v-model="incident.description" class="textarea" :aria-invalid="!!fieldErrors.description" />
      </div>
      <div class="row">
        <label class="checkbox"><input v-model="incident.thirdPartyInvolved" type="checkbox"> {{ $t('incident.thirdParty') }}</label>
        <label class="checkbox"><input v-model="incident.injuries" type="checkbox"> {{ $t('incident.injuries') }}</label>
      </div>
      <template #footer>
        <button class="btn" @click="dialog = null">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="running || !incident.location || !incident.description" @click="declare">
          {{ $t('incident.declare') }}
        </button>
      </template>
    </UiModal>
  </div>
</template>
