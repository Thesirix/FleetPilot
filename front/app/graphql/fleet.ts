// GraphQL operations of the fleet pages and the TypeScript shape of what they return.
// The schema lives in back/graphql-gateway/src/main/resources/graphql.

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Ref { id: string; name?: string }
export interface SiteRef { id: string; name: string; city: string }
export interface DriverRef { id: string; fullName: string; employeeNumber: string }
export interface VehicleRef { id: string; registration: string; make: string; model: string }
export interface Count { key: string; count: number }

export interface Company { id: string; name: string; segment: string; city: string }
export interface Site { id: string; name: string; city: string }
export interface CostCenter { id: string; code: string; label: string }

export interface FleetSummary {
  asOf: string
  inFleet: number
  byStatus: Count[]
  byCategory: Count[]
  byEnergy: Count[]
  byCritAir: Count[]
  averageAgeMonths: number
  averageCo2GramsPerKm: number
  activeDrivers: number
  assignedVehicles: number
  overdueDeadlines: number
  dueSoonDeadlines: number
  contractsEndingSoon: number
  contractsOverrunRisk: number
  monthlyRentTotal: number
  openIncidents: number
  incidentsLast12Months: number
}

export interface VehicleSummary {
  id: string
  companyId: string
  registration: string
  category: string
  make: string
  model: string
  energy: string
  critAir: number
  mileage: number
  status: string
  site: SiteRef | null
  costCenter: CostCenter | null
  currentDriver: DriverRef | null
}

export interface Assignment {
  id: string
  vehicle: VehicleRef
  driver: DriverRef
  kind: string
  startDate: string
  endDate: string | null
  startMileage: number
  endMileage: number | null
  drivenKilometres: number
  current: boolean
}

export interface Projection { projectedMileage: number; projectedOverrunKm: number; dailyAverageKm: number; overrunRisk: boolean }

export interface Contract {
  id: string
  vehicle: VehicleRef
  lessor: string
  contractNumber: string
  startDate: string
  endDate: string
  durationMonths: number
  contractualMileage: number
  startMileage: number
  monthlyRent: number
  totalRent: number
  maintenanceIncluded: boolean
  tyresIncluded: boolean
  replacementVehicle: boolean
  status: string
  endedOn: string | null
  daysRemaining: number | null
  projection: Projection | null
}

export interface Deadline {
  id: string
  vehicle: VehicleRef
  vehicleSummary?: Pick<VehicleSummary, 'status' | 'site' | 'currentDriver'> | null
  type: string
  dueDate: string | null
  dueMileage: number | null
  status: string
  urgency: string | null
  daysRemaining: number | null
  kilometresRemaining: number | null
  completedOn?: string | null
}

export interface Incident {
  id: string
  reference: string
  vehicle: VehicleRef
  driver: DriverRef | null
  type: string
  occurredAt: string
  location: string
  description: string
  thirdPartyInvolved: boolean
  injuries: boolean
  responsibility: string
  estimatedCost: number | null
  status: string
  closedOn: string | null
}

export interface MileageReading { mileage: number; readAt: string; source: string }

export interface Vehicle {
  id: string
  company: Ref
  registration: string
  vin: string
  category: string
  make: string
  model: string
  trimLevel: string | null
  energy: string
  critAir: number
  co2GramsPerKm: number
  fiscalPower: number | null
  firstRegistrationDate: string
  acquisition: string
  mileage: number
  mileageReadAt: string
  status: string
  allowedTransitions: string[]
  site: SiteRef | null
  costCenter: CostCenter | null
  currentAssignment: Assignment | null
  activeContract: Contract | null
  openDeadlines: Deadline[]
  mileageHistory: MileageReading[]
  openIncidents: number
  version: number
  assignments?: Assignment[]
  incidents?: Incident[]
}

export interface DriverSummary {
  id: string
  companyId: string
  employeeNumber: string
  firstName: string
  lastName: string
  fullName: string
  email: string
  phone: string | null
  site: SiteRef | null
  licenseCategories: string[]
  licenseExpiresOn: string | null
  active: boolean
  currentVehicle: VehicleRef | null
}

export interface DriverFile {
  driver: DriverSummary
  licenseNumber: string
  licenseIssuedOn: string
  assignments: Assignment[]
  incidents: Incident[]
  drivenKilometres: number
  version: number
}

const VEHICLE_REF = 'id registration make model'
const DRIVER_REF = 'id fullName employeeNumber'
const SITE_REF = 'id name city'
const CONTRACT = `id vehicle { ${VEHICLE_REF} } lessor contractNumber startDate endDate durationMonths contractualMileage
  startMileage monthlyRent totalRent maintenanceIncluded tyresIncluded replacementVehicle status endedOn daysRemaining
  projection { projectedMileage projectedOverrunKm dailyAverageKm overrunRisk }`
const DEADLINE = `id vehicle { ${VEHICLE_REF} } type dueDate dueMileage status urgency daysRemaining kilometresRemaining`
const ASSIGNMENT = `id vehicle { ${VEHICLE_REF} } driver { ${DRIVER_REF} } kind startDate endDate startMileage endMileage
  drivenKilometres current`
const INCIDENT = `id reference vehicle { ${VEHICLE_REF} } driver { ${DRIVER_REF} } type occurredAt location description
  thirdPartyInvolved injuries responsibility estimatedCost status closedOn`
const VEHICLE_SUMMARY = `id companyId registration category make model energy critAir mileage status
  site { ${SITE_REF} } costCenter { id code label } currentDriver { ${DRIVER_REF} }`
const VEHICLE = `id company { id name } registration vin category make model trimLevel energy critAir co2GramsPerKm
  fiscalPower firstRegistrationDate acquisition mileage mileageReadAt status allowedTransitions
  site { ${SITE_REF} } costCenter { id code label } currentAssignment { ${ASSIGNMENT} } activeContract { ${CONTRACT} }
  openDeadlines { ${DEADLINE} } mileageHistory { mileage readAt source } openIncidents version`
const DRIVER_SUMMARY = `id companyId employeeNumber firstName lastName fullName email phone site { ${SITE_REF} }
  licenseCategories licenseExpiresOn active currentVehicle { ${VEHICLE_REF} }`

export const Q = {
  companies: '{ companies { id name segment city } }',
  companyOptions: `query($id: ID!) { company(id: $id) { id sites { id name city } costCenters { id code label } } }`,
  dashboard: `query($companyId: ID) {
    fleetSummary(companyId: $companyId) { asOf inFleet byStatus { key count } byCategory { key count }
      byEnergy { key count } byCritAir { key count } averageAgeMonths averageCo2GramsPerKm activeDrivers
      assignedVehicles overdueDeadlines dueSoonDeadlines contractsEndingSoon contractsOverrunRisk monthlyRentTotal
      openIncidents incidentsLast12Months }
    overdue: deadlines(filter: { companyId: $companyId, urgency: OVERDUE }, size: 6, sort: ["dueDate,asc"]) {
      totalElements content { ${DEADLINE} vehicleSummary { status currentDriver { ${DRIVER_REF} } } } }
    ending: contracts(filter: { companyId: $companyId, status: ACTIVE }, size: 5, sort: ["endDate,asc"]) {
      content { ${CONTRACT} } }
  }`,
  vehicles: `query($filter: VehicleFilter, $page: Int, $size: Int, $sort: [String!]) {
    vehicles(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${VEHICLE_SUMMARY} } } }`,
  vehicle: `query($id: ID!) { vehicle(id: $id) { ${VEHICLE}
    assignments { ${ASSIGNMENT} } incidents { ${INCIDENT} } } }`,
  drivers: `query($filter: DriverFilter, $page: Int, $size: Int, $sort: [String!]) {
    drivers(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${DRIVER_SUMMARY} } } }`,
  driver: `query($id: ID!) { driver(id: $id) { driver { ${DRIVER_SUMMARY} } licenseNumber licenseIssuedOn
    assignments { ${ASSIGNMENT} } incidents { ${INCIDENT} } drivenKilometres version } }`,
  contracts: `query($filter: ContractFilter, $page: Int, $size: Int, $sort: [String!]) {
    contracts(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${CONTRACT} } } }`,
  deadlines: `query($filter: DeadlineFilter, $page: Int, $size: Int, $sort: [String!]) {
    deadlines(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages
      content { ${DEADLINE} vehicleSummary { status site { ${SITE_REF} } currentDriver { ${DRIVER_REF} } } } } }`,
  incidents: `query($filter: IncidentFilter, $page: Int, $size: Int, $sort: [String!]) {
    incidents(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${INCIDENT} } } }`,
  incident: `query($id: ID!) { incident(id: $id) { ${INCIDENT} latitude longitude createdAt } }`,
  contract: `query($id: ID!) { contract(id: $id) { ${CONTRACT} } }`,
  contractVehicle: `query($id: ID!) { vehicle(id: $id) { id company { id name } registration make model energy status
    mileage mileageReadAt site { ${SITE_REF} } currentAssignment { ${ASSIGNMENT} } mileageHistory { mileage readAt source } } }`,
  mySpace: `{ mySpace { driver { driver { ${DRIVER_SUMMARY} } licenseNumber licenseIssuedOn incidents { ${INCIDENT} }
    assignments { ${ASSIGNMENT} } drivenKilometres version } vehicle { ${VEHICLE} } } }`,
}

export const M = {
  createVehicle: `mutation($input: CreateVehicleInput!) { createVehicle(input: $input) { id registration } }`,
  changeVehicleStatus: `mutation($id: ID!, $status: VehicleStatus!) { changeVehicleStatus(id: $id, status: $status) { id status } }`,
  recordMileage: `mutation($id: ID!, $mileage: Int!) { recordMileage(id: $id, mileage: $mileage) { id mileage } }`,
  assignVehicle: `mutation($vehicleId: ID!, $driverId: ID!, $kind: AssignmentKind, $startDate: Date) {
    assignVehicle(vehicleId: $vehicleId, driverId: $driverId, kind: $kind, startDate: $startDate) { id driver { fullName } } }`,
  endAssignment: `mutation($id: ID!, $endMileage: Int) { endAssignment(id: $id, endMileage: $endMileage) { id drivenKilometres } }`,
  createDriver: `mutation($input: CreateDriverInput!) { createDriver(input: $input) { driver { id fullName } } }`,
  updateVehicle: `mutation($id: ID!, $input: UpdateVehicleInput!) { updateVehicle(id: $id, input: $input) { id registration } }`,
  updateDriver: `mutation($id: ID!, $input: UpdateDriverInput!) { updateDriver(id: $id, input: $input) { driver { id fullName } } }`,
  createContract: `mutation($vehicleId: ID!, $input: CreateContractInput!) {
    createContract(vehicleId: $vehicleId, input: $input) { id contractNumber } }`,
  completeDeadline: `mutation($id: ID!, $mileage: Int) { completeDeadline(id: $id, mileage: $mileage) {
    completed { id } next { dueDate dueMileage } } }`,
  endContract: `mutation($id: ID!) { endContract(id: $id) { id status } }`,
  declareIncident: `mutation($vehicleId: ID!, $input: DeclareIncidentInput!) {
    declareIncident(vehicleId: $vehicleId, input: $input) { id reference } }`,
  assessIncident: `mutation($id: ID!, $responsibility: Responsibility!, $estimatedCost: Float) {
    assessIncident(id: $id, responsibility: $responsibility, estimatedCost: $estimatedCost) { id } }`,
  changeIncidentStatus: `mutation($id: ID!, $status: IncidentStatus!) { changeIncidentStatus(id: $id, status: $status) { id status } }`,
  reportMyMileage: `mutation($mileage: Int!) { reportMyMileage(mileage: $mileage) { id mileage } }`,
  declareMyIncident: `mutation($input: DeclareIncidentInput!) { declareMyIncident(input: $input) { id reference } }`,
}
