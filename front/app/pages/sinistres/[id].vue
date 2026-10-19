<script setup lang="ts">
import { M, Q, type Incident } from '~/graphql/fleet'

const route = useRoute()
const { t } = useI18n()
const f = useFormat()
const id = computed(() => route.params.id as string)
const { data, pending, error, stale, refresh } = useGraphqlQuery<{ incident: (Incident & { createdAt: string }) | null }>(
  Q.incident, () => ({ id: id.value }), { cacheKey: () => `incident:${id.value}` })
const i = computed(() => data.value?.incident ?? null)
useHead({ title: () => (i.value ? `${i.value.reference} · FleetPilot` : 'FleetPilot') })

const NEXT: Record<string, string[]> = {
  DECLARED: ['UNDER_REVIEW', 'REJECTED'],
  UNDER_REVIEW: ['IN_REPAIR', 'CLOSED', 'REJECTED'],
  IN_REPAIR: ['CLOSED'],
}
const steps = ['DECLARED', 'UNDER_REVIEW', 'IN_REPAIR', 'CLOSED']
const stepIndex = computed(() => steps.indexOf(i.value?.status ?? ''))

const { run, running } = useMutation()
const assessment = reactive({ responsibility: 'UNKNOWN', estimatedCost: null as number | null })
watch(i, (value) => {
  if (value) {
    assessment.responsibility = value.responsibility
    assessment.estimatedCost = value.estimatedCost
  }
}, { immediate: true })

async function assess() {
  const done = await run(M.assessIncident, { id: id.value, responsibility: assessment.responsibility,
    estimatedCost: assessment.estimatedCost === null || assessment.estimatedCost === undefined ? null : Number(assessment.estimatedCost) },
  t('incident.assessed'))
  if (done) refresh()
}
async function move(status: string) {
  const done = await run(M.changeIncidentStatus, { id: id.value, status },
    t('incident.moved', { status: f.label('IncidentStatus', status) }))
  if (done) refresh()
}
const editable = computed(() => i.value && !['CLOSED', 'REJECTED'].includes(i.value.status))
</script>

<template>
  <div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />
    <UiEmpty v-if="!pending && !error && !i" :title="$t('app.notFound')" :text="$t('app.notFoundText')" />
    <template v-if="i">
      <UiPageHeader :title="i.reference" :subtitle="`${f.label('IncidentType', i.type)} · ${f.dateTime(i.occurredAt)}`" back="/sinistres">
        <template #badges><FleetBadge type="IncidentStatus" :value="i.status" /></template>
        <template #actions>
          <button v-for="(s, index) in NEXT[i.status] ?? []" :key="s" class="btn" :class="{ 'btn-primary': index === 0 }"
            :disabled="running" @click="move(s)">{{ f.label('IncidentStatus', s) }}</button>
        </template>
      </UiPageHeader>

      <ol class="progress card">
        <li v-for="(s, index) in steps" :key="s" :class="{ done: index <= stepIndex, current: index === stepIndex }">
          <span class="dot">{{ index + 1 }}</span>{{ f.label('IncidentStatus', s) }}
        </li>
      </ol>

      <div class="layout">
        <section class="card">
          <header class="card-header"><h2>{{ $t('incident.description') }}</h2></header>
          <div class="card-body stack">
            <p class="description">{{ i.description }}</p>
            <dl class="dl">
              <dt>{{ $t('vehicle.vehicle') }}</dt>
              <dd><NuxtLink :to="`/vehicules/${i.vehicle.id}`"><VehicleCell :vehicle="i.vehicle" /></NuxtLink></dd>
              <dt>{{ $t('vehicle.driver') }}</dt>
              <dd><NuxtLink v-if="i.driver" :to="`/conducteurs/${i.driver.id}`">{{ i.driver.fullName }}</NuxtLink><span v-else>-</span></dd>
              <dt>{{ $t('incident.location') }}</dt><dd>{{ i.location }}</dd>
              <dt>{{ $t('incident.thirdParty') }}</dt><dd>{{ i.thirdPartyInvolved ? $t('app.yes') : $t('app.no') }}</dd>
              <dt>{{ $t('incident.injuries') }}</dt>
              <dd><span :class="{ 'badge danger': i.injuries }">{{ i.injuries ? $t('app.yes') : $t('app.no') }}</span></dd>
              <template v-if="i.closedOn"><dt>{{ $t('incident.status') }}</dt><dd>{{ $t('incident.closedOn', { date: f.date(i.closedOn) }) }}</dd></template>
            </dl>
          </div>
        </section>

        <IncidentPhotos :incident-id="i.id" class="photos" />

        <section class="card assess-card">
          <header class="card-header"><h2>{{ $t('incident.assess') }}</h2></header>
          <div class="card-body stack">
            <div class="field">
              <label for="resp">{{ $t('incident.responsibility') }}</label>
              <select id="resp" v-model="assessment.responsibility" class="select" :disabled="!editable">
                <option v-for="r in ['UNKNOWN', 'NONE', 'PARTIAL', 'FULL']" :key="r" :value="r">{{ f.label('Responsibility', r) }}</option>
              </select>
            </div>
            <div class="field">
              <label for="cost">{{ $t('incident.estimatedCost') }} (€)</label>
              <input id="cost" v-model="assessment.estimatedCost" type="number" min="0" step="0.01" class="input" :disabled="!editable">
            </div>
            <button v-if="editable" class="btn btn-primary" :disabled="running" @click="assess">{{ $t('app.save') }}</button>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.progress {
  display: flex;
  gap: 8px;
  margin: 0 0 16px;
  padding: 14px 18px;
  list-style: none;
  overflow-x: auto;
}

.progress li {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 8px;
  color: var(--text-faint);
  font-size: var(--text-sm);
  font-weight: 550;
  white-space: nowrap;
}

.progress li + li::before {
  content: "";
  flex: 0 0 24px;
  height: 1px;
  margin-right: 4px;
  background: var(--border-strong);
}

.dot {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  font-size: 11px;
}

.progress li.done {
  color: var(--text-secondary);
}

.progress li.done .dot {
  border-color: var(--primary);
  background: var(--primary);
  color: #fff;
}

.progress li.current {
  color: var(--text);
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

/* The photos go under the description; the assessment stays on the right */
.photos {
  grid-column: 1;
}

.assess-card {
  grid-row: 1 / span 2;
  grid-column: 2;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .assess-card {
    grid-row: auto;
    grid-column: 1;
  }
}

.description {
  padding: 12px 14px;
  border-left: 3px solid var(--primary);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}
</style>
