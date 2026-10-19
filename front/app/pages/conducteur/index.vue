<script setup lang="ts">
import { ArrowUpRight, BellRing, CalendarClock, ChevronRight, ClipboardCheck, CloudOff, Fuel, Gauge, MapPin, ShieldAlert, Trash2 } from 'lucide-vue-next'
import { DM } from '~/graphql/driver'
import { Q, type DriverFile, type Vehicle } from '~/graphql/fleet'
import { newItem } from '~/utils/outbox'

definePageMeta({ layout: 'driver' })

const { t } = useI18n()
const f = useFormat()
const session = useSessionStore()
useHead({ title: () => `${t('nav.mySpace')} · FleetPilot` })

const { data, error, stale, refresh } = useGraphqlQuery<{ mySpace: { driver: DriverFile; vehicle: Vehicle | null } }>(
  Q.mySpace, {}, { cacheKey: 'my-space' })
const me = computed(() => data.value?.mySpace)
const v = computed(() => me.value?.vehicle ?? null)

const outbox = useOutbox()
const notifications = useDriverNotifications()
const latest = computed(() => notifications.list.value.slice(0, 3))

const mileageOpen = ref(false)
const mileage = ref(0)
const sending = ref(false)

function openMileage() {
  mileage.value = v.value?.mileage ?? 0
  mileageOpen.value = true
}

async function reportMileage() {
  sending.value = true
  try {
    const value = Number(mileage.value)
    const item = newItem('mileage', t('driverApp.outbox.mileage', { km: f.km(value) }), DM.reportMyMileage,
      { mileage: value })
    if (await outbox.send(item, t('vehicle.mileageRecorded'))) {
      mileageOpen.value = false
      refresh()
    }
  } finally {
    sending.value = false
  }
}

const actions = computed(() => [
  { to: '/conducteur/plein', label: t('driverApp.home.fuel'), hint: t('driverApp.home.fuelHint'), icon: Fuel, tone: 'amber' },
  { to: '/conducteur/etat-des-lieux', label: t('driverApp.home.inspection'), hint: t('driverApp.home.inspectionHint'), icon: ClipboardCheck, tone: 'teal' },
  { to: '/conducteur/sinistre', label: t('me.declareIncident'), hint: t('driverApp.home.incidentHint'), icon: ShieldAlert, tone: 'red' },
  { to: '/conducteur/garages', label: t('driverApp.home.garages'), hint: t('driverApp.home.garagesHint'), icon: MapPin, tone: 'blue' },
])

/** Lease contract: the kilometres driven since its start against the mileage it allows. */
const contract = computed(() => {
  const c = v.value?.activeContract
  if (!c || !v.value || !c.contractualMileage) return null
  const used = Math.max(0, v.value.mileage - (c.startMileage ?? 0))
  return { lessor: c.lessor, used, total: c.contractualMileage, percent: Math.min(100, (used / c.contractualMileage) * 100),
    risk: Boolean(c.projection?.overrunRisk) }
})

/** Kilometres of the last 30 days, from the readings of the vehicle. */
const monthKm = computed(() => {
  const since = Date.now() - 30 * 86_400_000
  const readings = (v.value?.mileageHistory ?? []).filter(r => new Date(r.readAt).getTime() >= since).map(r => r.mileage)
  return readings.length > 1 ? Math.max(...readings) - Math.min(...readings) : null
})

/** The deadline that comes first, by date or by mileage. */
const next = computed(() => [...(v.value?.openDeadlines ?? [])].sort((a, b) =>
  (a.daysRemaining ?? (a.kilometresRemaining ?? 99_999) / 50) - (b.daysRemaining ?? (b.kilometresRemaining ?? 99_999) / 50))[0] ?? null)

const levelClass = { CRITICAL: 'danger', WARNING: 'warning', INFO: 'info' } as const
</script>

<template>
  <div class="home stack">
    <div class="hello">
      <h1>{{ $t('me.title', { name: session.viewer?.name?.split(' ')[0] ?? '' }) }}</h1>
      <p class="muted">{{ $t('me.subtitle') }}</p>
    </div>
    <UiQueryState :error="error" :stale="stale" @retry="refresh" />

    <div v-if="me" class="columns">
      <div class="col stack">
      <section v-if="v" class="vehicle-hero">
        <div class="row-between hero-top">
          <span class="eyebrow">{{ $t('me.myVehicle') }}</span>
          <CritAirBadge :value="v.critAir" labelled />
        </div>
        <p class="plate" :aria-label="v.registration"><span class="plate-eu">F</span><span class="plate-number mono">{{ v.registration }}</span></p>
        <p class="model">{{ v.make }} {{ v.model }}<template v-if="v.trimLevel"> · {{ v.trimLevel }}</template></p>
        <div class="facts">
          <div><span>{{ $t('vehicle.mileage') }}</span><strong class="num">{{ f.km(v.mileage) }}</strong></div>
          <div><span>{{ $t('vehicle.energy') }}</span><strong>{{ f.label('Energy', v.energy) }}</strong></div>
          <div><span>{{ $t('vehicle.site') }}</span><strong>{{ v.site?.name ?? '-' }}</strong></div>
        </div>
        <div v-if="contract" class="contract">
          <div class="row-between small">
            <span>{{ $t('driverApp.home.contract', { lessor: contract.lessor }) }}</span>
            <strong class="num">{{ $t('driverApp.home.contractUse', { used: f.km(contract.used), total: f.km(contract.total) }) }}</strong>
          </div>
          <div class="bar" :class="{ risk: contract.risk }"><span :style="{ width: `${contract.percent}%` }" /></div>
        </div>
        <p v-else-if="monthKm !== null" class="month small">{{ $t('driverApp.home.monthKm', { km: f.km(monthKm) }) }}</p>
        <button class="btn hero-btn" @click="openMileage"><Gauge :size="18" /> {{ $t('me.reportMileage') }}</button>
      </section>
      <section v-else class="card"><div class="card-body"><p class="muted">{{ $t('me.noVehicle') }}</p></div></section>

      <NuxtLink v-if="next" to="#deadlines" class="card next" :class="next.urgency?.toLowerCase()">
        <span class="next-icon"><CalendarClock :size="20" /></span>
        <span class="next-text">
          <span class="xs muted">{{ $t('driverApp.home.nextDeadline') }}</span>
          <strong>{{ f.label('DeadlineType', next.type) }}</strong>
          <DeadlineRemaining :days-remaining="next.daysRemaining" :kilometres-remaining="next.kilometresRemaining" />
        </span>
        <FleetBadge type="Urgency" :value="next.urgency" />
      </NuxtLink>

      <section v-if="outbox.items.value.length" id="outbox" class="card outbox">
        <header class="card-header">
          <h2><CloudOff :size="16" /> {{ $t('driverApp.outbox.title') }}</h2>
          <button class="btn btn-sm" :disabled="outbox.flushing.value" @click="outbox.flush()">{{ $t('driverApp.outbox.retry') }}</button>
        </header>
        <ul class="list">
          <li v-for="item in outbox.items.value" :key="item.id">
            <div class="row-between">
              <div class="grow">
                <p class="small">{{ item.summary }}</p>
                <p v-if="item.error" class="xs danger-text">{{ $te(`errors.${item.error.code}`) ? $t(`errors.${item.error.code}`) : item.error.message }}</p>
                <p v-else class="xs muted">
                  {{ $t('driverApp.outbox.waitingSince', { date: f.dateTime(item.createdAt) }) }}
                  <template v-if="item.photos.length"> · {{ $t('driverApp.outbox.photos', { sent: item.photosSent.length, total: item.photos.length }) }}</template>
                </p>
              </div>
              <button class="btn btn-ghost btn-icon btn-sm" :aria-label="$t('driverApp.outbox.discard')" @click="outbox.discard(item.id)">
                <Trash2 :size="15" />
              </button>
            </div>
          </li>
        </ul>
      </section>

      <nav v-if="v" class="actions" :aria-label="$t('driverApp.home.actions')">
        <NuxtLink v-for="a in actions" :key="a.to" :to="a.to" class="action card" :class="a.tone">
          <span class="action-icon"><component :is="a.icon" :size="20" /></span>
          <span class="action-text"><strong>{{ a.label }}</strong><span class="xs muted">{{ a.hint }}</span></span>
          <ArrowUpRight class="action-go" :size="16" />
        </NuxtLink>
      </nav>

      </div>

      <div class="col stack">
      <section class="card">
        <header class="card-header">
          <h2>{{ $t('driverApp.notifications.title') }}</h2>
          <NuxtLink to="/conducteur/notifications" class="small link">{{ $t('app.seeAll') }}</NuxtLink>
        </header>
        <ul v-if="latest.length" class="list">
          <li v-for="n in latest" :key="n.id" class="notification">
            <span class="level" :class="levelClass[n.level]" />
            <div class="grow">
              <p class="small strong">{{ notifications.title(n) }}</p>
              <p class="xs muted">{{ notifications.text(n) }}</p>
            </div>
          </li>
        </ul>
        <UiEmpty v-else :text="$t('driverApp.notifications.none')" />
        <div v-if="notifications.permission.value === 'default'" class="card-body enable">
          <button class="btn btn-sm" @click="notifications.enableSystemNotifications()">
            <BellRing :size="15" /> {{ $t('driverApp.notifications.enable') }}
          </button>
        </div>
      </section>

      <section v-if="v" id="deadlines" class="card">
        <header class="card-header"><h2>{{ $t('me.nextDeadlines') }}</h2></header>
        <ul v-if="v.openDeadlines.length" class="list">
          <li v-for="d in v.openDeadlines" :key="d.id" class="row-between">
            <span>{{ f.label('DeadlineType', d.type) }}<span class="muted small"> · {{ d.dueDate ? f.date(d.dueDate) : f.km(d.dueMileage) }}</span></span>
            <FleetBadge type="Urgency" :value="d.urgency" />
          </li>
        </ul>
        <UiEmpty v-else :text="$t('driverApp.home.noDeadline')" />
      </section>

      <section class="card">
        <header class="card-header">
          <h2>{{ $t('me.myIncidents') }}</h2>
          <NuxtLink to="/conducteur/sinistre" class="small link">{{ $t('me.declareIncident') }} <ChevronRight :size="14" /></NuxtLink>
        </header>
        <ul v-if="me.driver.incidents.length" class="list">
          <li v-for="i in me.driver.incidents" :key="i.id" class="row-between">
            <span><span class="mono small">{{ i.reference }}</span><span class="muted small"> · {{ f.label('IncidentType', i.type) }} · {{ f.date(i.occurredAt) }}</span></span>
            <FleetBadge type="IncidentStatus" :value="i.status" />
          </li>
        </ul>
        <UiEmpty v-else :text="$t('me.noIncidents')" />
      </section>

      <section class="card">
        <header class="card-header"><h2>{{ $t('me.license') }}</h2></header>
        <div class="card-body">
          <dl class="dl">
            <dt>{{ $t('driver.licenseCategories') }}</dt><dd>{{ me.driver.driver.licenseCategories.join(', ') }}</dd>
            <dt>{{ $t('driver.licenseExpiresOn') }}</dt><dd>{{ f.date(me.driver.driver.licenseExpiresOn) }}</dd>
          </dl>
        </div>
      </section>
      </div>
    </div>

    <UiModal v-model:open="mileageOpen" :title="$t('me.reportMileage')" width="420px">
      <div class="field">
        <label for="km">{{ $t('vehicle.newMileage') }}</label>
        <input id="km" v-model="mileage" type="number" inputmode="numeric" min="0" class="input">
      </div>
      <template #footer>
        <button class="btn" @click="mileageOpen = false">{{ $t('app.cancel') }}</button>
        <button class="btn btn-primary" :disabled="sending" @click="reportMileage">{{ $t('app.save') }}</button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.home {
  gap: 14px;
}

.columns {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.col {
  gap: 14px;
  min-width: 0;
}

/* On a computer: the vehicle and the shortcuts on the left, what to follow on the right */
@media (min-width: 1024px) {
  .columns {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
    gap: 24px;
  }
}

.hello h1 {
  margin: 4px 0 2px;
  font-size: var(--text-2xl);
  letter-spacing: -0.01em;
}

.hello p {
  margin: 0;
}

/* My vehicle: the brand gradient, a real number plate and a vehicle driving its route */
.vehicle-hero {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 20px;
  overflow: hidden;
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, #0b4f4a 0%, #0f766e 55%, #14a394 100%);
  box-shadow: 0 12px 28px rgba(15, 118, 110, 0.28);
  color: #fff;
  isolation: isolate;
  animation: rise 0.45s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.vehicle-hero::before {
  content: "";
  position: absolute;
  inset: -40%;
  z-index: -1;
  background:
    radial-gradient(260px 180px at 15% 15%, rgba(255, 255, 255, 0.16), transparent 70%),
    radial-gradient(300px 220px at 85% 90%, rgba(94, 234, 212, 0.32), transparent 70%);
  animation: hero-drift 16s ease-in-out infinite alternate;
}

.hero-top {
  margin-bottom: 8px;
}

.eyebrow {
  color: rgba(255, 255, 255, 0.8);
  font-size: var(--text-xs);
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

/* French number plate: white, with the blue European band */
.plate {
  display: inline-flex;
  align-self: flex-start;
  margin: 0;
  overflow: hidden;
  border: 2px solid #101828;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.18);
  color: #101828;
}

.plate-eu {
  display: grid;
  place-items: end center;
  width: 22px;
  padding-bottom: 3px;
  background: #1d4ed8;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.plate-number {
  padding: 4px 12px;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.model {
  margin: 8px 0 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: var(--text-sm);
}

.facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 14px 0 4px;
}

.facts div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(6px);
}

.facts span {
  color: rgba(255, 255, 255, 0.75);
  font-size: 11px;
}

.facts strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-sm);
}

.contract {
  margin-top: 8px;
}

.contract .row-between {
  color: rgba(255, 255, 255, 0.85);
}

.bar {
  height: 6px;
  margin-top: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.2);
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: #5eead4;
  transform-origin: left;
  animation: grow 0.9s 0.2s cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.bar.risk span {
  background: #fdb022;
}

.month {
  margin: 8px 0 0;
  color: rgba(255, 255, 255, 0.85);
}

.hero-btn {
  width: 100%;
  height: 44px;
  margin-top: 14px;
  border: none;
  background: #fff;
  color: var(--primary-text);
  font-size: var(--text-md);
  font-weight: 600;
}

.hero-btn:hover {
  background: #f0fdfa;
}

/* The next deadline, put forward */
.next {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  color: inherit;
  text-decoration: none;
  border-left: 4px solid var(--info);
}

.next.overdue { border-left-color: var(--danger); }
.next.due_soon { border-left-color: var(--warning); }

.next-icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--info-soft);
  color: var(--info);
}

.next.overdue .next-icon { background: var(--danger-soft); color: var(--danger); }
.next.due_soon .next-icon { background: var(--warning-soft); color: var(--warning); }

.next-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  font-size: var(--text-sm);
}

.actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.action {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  overflow: hidden;
  color: inherit;
  text-decoration: none;
  --tone: var(--primary);
  --tone-soft: var(--primary-soft);
}

.action.amber { --tone: #b54708; --tone-soft: #fef0c7; }
.action.teal { --tone: #0f766e; --tone-soft: #ccfbf1; }
.action.red { --tone: #b42318; --tone-soft: #fee4e2; }
.action.blue { --tone: #175cd3; --tone-soft: #dbeafe; }

/* A wash of the action colour in the corner */
.action::after {
  content: "";
  position: absolute;
  top: -30px;
  right: -30px;
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: var(--tone-soft);
  opacity: 0.55;
  transition: transform 0.25s ease;
}

.action:hover::after {
  transform: scale(1.35);
}

.action-icon {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--tone-soft);
  color: var(--tone);
}

.action-text {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--text-sm);
}

.action-go {
  position: absolute;
  top: 14px;
  right: 14px;
  z-index: 1;
  color: var(--tone);
  opacity: 0;
  transform: translate(-4px, 4px);
  transition: opacity 0.2s, transform 0.2s;
}

.action:hover .action-go {
  opacity: 1;
  transform: none;
}

@keyframes hero-drift {
  from { transform: translate3d(-3%, -2%, 0); }
  to { transform: translate3d(3%, 3%, 0); }
}

@keyframes grow {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

.card-header h2 {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.link {
  display: inline-flex;
  align-items: center;
  color: var(--primary);
  text-decoration: none;
}

.list {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
}

.list li {
  padding: 12px 18px;
  border-bottom: 1px solid var(--border);
  font-size: var(--text-sm);
}

.list li:last-child {
  border-bottom: none;
}

.list p {
  margin: 0;
}

.notification {
  display: flex;
  gap: 10px;
}

.level {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--info);
}

.level.danger {
  background: var(--danger);
}

.level.warning {
  background: var(--warning);
}

.strong {
  font-weight: 600;
}

.danger-text {
  color: var(--danger);
}

.outbox {
  border-color: var(--warning);
}

.enable {
  border-top: 1px solid var(--border);
}
</style>
