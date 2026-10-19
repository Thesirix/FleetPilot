// GraphQL operations of the driver app (PWA) and of the driver records shown to the fleet managers.
// The schema lives in back/graphql-gateway/src/main/resources/graphql/driver.graphqls.

import type { DriverRef } from './fleet'

export type FuelKind = 'FUEL' | 'CHARGE'
export type FuelPayment = 'FUEL_CARD' | 'PERSONAL'
export type InspectionKind = 'PICKUP' | 'RETURN' | 'PERIODIC'
export type InspectionCondition = 'OK' | 'DAMAGED' | 'MISSING'
export const INSPECTION_ITEMS = ['BODYWORK', 'WINDSCREEN', 'TYRES', 'LIGHTS', 'INTERIOR', 'CLEANLINESS', 'SAFETY_KIT',
  'DOCUMENTS'] as const
export type InspectionItem = typeof INSPECTION_ITEMS[number]

export interface Photo { id: string; contentType: string; sizeBytes: number; createdAt: string }

export interface FuelEntry {
  id: string
  vehicleId: string
  driver: DriverRef | null
  filledAt: string
  kind: FuelKind
  quantity: number
  amount: number
  unitPrice: number
  mileage: number
  station: string | null
  payment: FuelPayment
  createdAt: string
}

export interface InspectionCheck { item: InspectionItem; condition: InspectionCondition; comment: string | null }

export interface Inspection {
  id: string
  vehicleId: string
  driver: DriverRef | null
  kind: InspectionKind
  performedAt: string
  mileage: number
  fuelLevel: number
  remarks: string | null
  checks: InspectionCheck[]
  defects: number
  photos: Photo[]
  createdAt: string
}

export interface DriverNotification {
  id: string
  kind: 'DEADLINE' | 'VEHICLE_ALERT' | 'INCIDENT'
  level: 'INFO' | 'WARNING' | 'CRITICAL'
  code: string
  detail: string | null
  dueDate: string | null
  dueMileage: number | null
  at: string
}

export interface NearbyGarage {
  id: string
  name: string
  network: string
  brand: string | null
  address: string | null
  postalCode: string | null
  city: string
  phone: string | null
  hourlyRate: number | null
  rating: number | null
  latitude: number | null
  longitude: number | null
  distanceKm: number
}

const FUEL_ENTRY = `id vehicleId driver { id fullName employeeNumber } filledAt kind quantity amount unitPrice mileage
  station payment createdAt`
const INSPECTION = `id vehicleId driver { id fullName employeeNumber } kind performedAt mileage fuelLevel remarks
  checks { item condition comment } defects photos { id contentType sizeBytes createdAt } createdAt`
const NOTIFICATION = 'id kind level code detail dueDate dueMileage at'

export const DQ = {
  myFuelEntries: `{ myFuelEntries { ${FUEL_ENTRY} } }`,
  myInspections: `{ myInspections { ${INSPECTION} } }`,
  myNotifications: `{ myNotifications { ${NOTIFICATION} } }`,
  nearbyGarages: `query($latitude: Float!, $longitude: Float!, $radiusKm: Int) {
    nearbyGarages(latitude: $latitude, longitude: $longitude, radiusKm: $radiusKm, limit: 20) {
      id name network brand address postalCode city phone hourlyRate rating latitude longitude distanceKm } }`,
  vehicleDriverRecords: `query($id: ID!) { vehicle(id: $id) { id fuelEntries { ${FUEL_ENTRY} }
    inspections { ${INSPECTION} } } }`,
  incidentPhotos: `query($id: ID!) { incident(id: $id) { id photos { id contentType sizeBytes createdAt } } }`,
}

export const DM = {
  recordMyFuel: `mutation($id: ID!, $input: RecordFuelInput!) { recordMyFuel(id: $id, input: $input) { id } }`,
  recordMyInspection: `mutation($id: ID!, $input: RecordInspectionInput!) {
    recordMyInspection(id: $id, input: $input) { id } }`,
  declareMyIncidentWithId: `mutation($id: ID!, $input: DeclareIncidentInput!) {
    declareMyIncidentWithId(id: $id, input: $input) { id reference } }`,
  reportMyMileage: `mutation($mileage: Int!) { reportMyMileage(mileage: $mileage) { id mileage } }`,
}
