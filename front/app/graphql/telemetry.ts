// GraphQL operations of the connected vehicles and the alert centre.
// The schema lives in back/graphql-gateway/src/main/resources/graphql/telemetry.graphqls; the live positions arrive
// through the Server-Sent Events of /api/v1/telemetry/stream (useLiveStream).

import { accessToken } from '~/utils/auth'

export type Activity = 'MOVING' | 'IDLING' | 'PARKED' | 'OFFLINE'
export type AlertType = 'SPEEDING' | 'LONG_IDLE' | 'LOW_BATTERY' | 'ENGINE_FAULT' | 'ENGINE_WARNING' | 'SERVICE_DUE'
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'

export const ACTIVITIES: Activity[] = ['MOVING', 'IDLING', 'PARKED', 'OFFLINE']
export const ALERT_TYPES: AlertType[] = ['ENGINE_FAULT', 'ENGINE_WARNING', 'SPEEDING', 'LOW_BATTERY', 'LONG_IDLE', 'SERVICE_DUE']

export interface LiveState {
  vehicleId: string
  companyId: string
  registration: string
  latitude: number
  longitude: number
  speedKmh: number
  heading: number
  ignition: boolean
  batteryPercent: number | null
  odometerKm: number | null
  lastPositionAt: string
  activity: Activity
  openAlerts: number
}

export interface TelemetrySummary {
  connected: number
  moving: number
  idling: number
  parked: number
  offline: number
  kilometresToday: number
  openAlerts: number
  openAlertsByType: { type: AlertType; count: number }[]
}

export interface Trip {
  id: string
  startedAt: string
  endedAt: string
  distanceKm: number
  maxSpeed: number
  averageSpeed: number
  idleMinutes: number
}

export interface Fault { id: string; at: string; code: string; description: string | null; severity: string }

export interface UsageDay {
  day: string
  distanceKm: number
  trips: number
  longestTripKm: number
  drivingMinutes: number
  maxSpeed: number
}

export interface Alert {
  id: string
  vehicleId: string
  companyId: string
  registration: string | null
  type: AlertType
  severity: AlertSeverity
  message: string
  value: number | null
  latitude: number | null
  longitude: number | null
  status: AlertStatus
  raisedAt: string
  lastSeenAt: string
  occurrences: number
  handledBy: string | null
  acknowledgedAt: string | null
  resolvedAt: string | null
  comment: string | null
}

export interface VehicleTelemetry {
  state: LiveState | null
  trips: Trip[]
  faults: Fault[]
  usage: UsageDay[]
  alerts: Alert[]
}

export interface TrackPoint { at: string; latitude: number; longitude: number; speedKmh: number; ignition: boolean }

const LIVE_STATE = `vehicleId companyId registration latitude longitude speedKmh heading ignition batteryPercent
  odometerKm lastPositionAt activity openAlerts`
const ALERT = `id vehicleId companyId registration type severity message value latitude longitude status raisedAt
  lastSeenAt occurrences handledBy acknowledgedAt resolvedAt comment`

export const TQ = {
  summary: `query TelemetrySummary($companyId: ID) { telemetrySummary(companyId: $companyId) { connected moving idling parked offline kilometresToday openAlerts
    openAlertsByType { type count } } }`,
  vehicle: `query VehicleTelemetry($vehicleId: ID!) { vehicleTelemetry(vehicleId: $vehicleId) {
    state { ${LIVE_STATE} }
    trips { id startedAt endedAt distanceKm maxSpeed averageSpeed idleMinutes }
    faults { id at code description severity }
    usage { day distanceKm trips longestTripKm drivingMinutes maxSpeed }
    alerts { ${ALERT} } } }`,
  alerts: `query Alerts($filter: AlertFilter, $page: Int, $size: Int) {
    alerts(filter: $filter, page: $page, size: $size) { content { ${ALERT} } page size totalElements totalPages }
    telemetrySummary { openAlerts openAlertsByType { type count } } }`,
}

export const TM = {
  acknowledge: `mutation AcknowledgeAlert($id: ID!) { acknowledgeAlert(id: $id) { ${ALERT} } }`,
  resolve: `mutation ResolveAlert($id: ID!, $comment: String) { resolveAlert(id: $id, comment: $comment) { ${ALERT} } }`,
}

/** Positions of a vehicle over the last minutes (REST: the track is not part of the GraphQL schema). */
export async function fetchTrack(vehicleId: string, minutes = 60): Promise<TrackPoint[]> {
  const response = await fetch(`/api/v1/telemetry/vehicles/${vehicleId}/track?minutes=${minutes}`, {
    headers: { Authorization: `Bearer ${await accessToken()}`, Accept: 'application/json' },
  })
  return response.ok ? response.json() : []
}
