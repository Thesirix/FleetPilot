/** Administration: client companies, user accounts, analysis rules, electric models, low emission zones, journal. */

export interface AdminCompanyDetails {
  id: string
  name: string
  legalName: string
  siren: string
  segment: string
  city: string
  address: string | null
  postalCode: string | null
  contactName: string | null
  contactEmail: string | null
  contactPhone: string | null
  active: boolean
  version: number
}

export interface AdminSite { id: string; name: string; address: string; postalCode: string; city: string; latitude: number; longitude: number }
export interface AdminCostCenter { id: string; code: string; label: string }

export interface AdminCompany {
  company: AdminCompanyDetails
  vehicles: number
  activeVehicles: number
  drivers: number
  sites: number
  costCenters: number
  activeContracts: number
  siteUsage?: { site: AdminSite; vehicles: number; drivers: number; version: number }[]
  costCenterUsage?: { costCenter: AdminCostCenter; vehicles: number; version: number }[]
}

export interface AuditEntry {
  id: string
  occurredAt: string
  actor: string
  actorRole: string | null
  companyId: string | null
  action: string
  targetType: string
  targetId: string | null
  targetLabel: string | null
  details: string | null
}

export interface User {
  id: string
  username: string
  email: string | null
  firstName: string | null
  lastName: string | null
  fullName: string
  enabled: boolean
  role: string | null
  companyId: string | null
  companyName: string | null
  driverId: string | null
  driverName: string | null
  garageId: string | null
  garage: { id: string; name: string; city: string } | null
  lessor: string | null
  federated: boolean
  createdAt: string | null
}

export interface AnalysisRules {
  partTolerance: number
  timeTolerance: number
  rateTolerance: number
  suppliesShare: number
  suppliesMinimum: number
  negotiationShare: number
  negotiationMinimum: number
  autoApprovalLimit: number
  managerApprovalLimit: number
  updatedAt: string
  updatedBy: string
  version: number
}

export interface EvModelRow {
  code: string
  name: string
  make: string | null
  model: string | null
  category: string
  segment: string
  batteryKwh: number
  wltpRangeKm: number
  realisticRangeKm: number
  consumptionKwh100km: number
  fastChargeKw: number
  monthlyRent: number
}

export interface LowEmissionZone { city: string; zone: string; maxCritAir: number; effectiveFrom: string; status: string }

const COMPANY = `id name legalName siren segment city address postalCode contactName contactEmail contactPhone active version`
const ADMIN_COMPANY = `company { ${COMPANY} } vehicles activeVehicles drivers sites costCenters activeContracts`
const USER = `id username email firstName lastName fullName enabled role companyId companyName driverId driverName garageId
  garage { id name city } lessor federated createdAt`
const RULES = `partTolerance timeTolerance rateTolerance suppliesShare suppliesMinimum negotiationShare negotiationMinimum
  autoApprovalLimit managerApprovalLimit updatedAt updatedBy version`

export const AQ = {
  companies: `{ adminCompanies { ${ADMIN_COMPANY} } }`,
  company: `query($id: ID!) { adminCompany(id: $id) { ${ADMIN_COMPANY}
    siteUsage { site { id name address postalCode city latitude longitude } vehicles drivers version }
    costCenterUsage { costCenter { id code label } vehicles version } } }`,
  audit: `query($filter: AuditFilter, $page: Int, $size: Int) { auditTrail(filter: $filter, page: $page, size: $size) {
    page size totalElements totalPages content { id occurredAt actor actorRole companyId action targetType targetId
      targetLabel details } } }`,
  users: `query($filter: UserFilter, $page: Int, $size: Int) { users(filter: $filter, page: $page, size: $size) {
    page size totalElements totalPages content { ${USER} } } }`,
  user: `query($id: ID!) { user(id: $id) { ${USER} } }`,
  lessors: '{ lessors }',
  rules: `{ analysisRules { ${RULES} } }`,
  references: `{ evCatalog { code name make model category segment batteryKwh wltpRangeKm realisticRangeKm
    consumptionKwh100km fastChargeKw monthlyRent } lowEmissionZones { city zone maxCritAir effectiveFrom status } }`,
}

export const AM = {
  createCompany: `mutation($input: CompanyInput!) { createCompany(input: $input) { id name } }`,
  updateCompany: `mutation($id: ID!, $input: CompanyInput!) { updateCompany(id: $id, input: $input) { id name } }`,
  setCompanyActive: `mutation($id: ID!, $active: Boolean!) { setCompanyActive(id: $id, active: $active) { id active } }`,
  createSite: `mutation($companyId: ID!, $input: SiteInput!) { createSite(companyId: $companyId, input: $input) { id name } }`,
  updateSite: `mutation($id: ID!, $input: SiteInput!) { updateSite(id: $id, input: $input) { id name } }`,
  deleteSite: `mutation($id: ID!) { deleteSite(id: $id) }`,
  createCostCenter: `mutation($companyId: ID!, $input: CostCenterInput!) {
    createCostCenter(companyId: $companyId, input: $input) { id code } }`,
  updateCostCenter: `mutation($id: ID!, $input: CostCenterInput!) { updateCostCenter(id: $id, input: $input) { id code } }`,
  deleteCostCenter: `mutation($id: ID!) { deleteCostCenter(id: $id) }`,
  createUser: `mutation($input: UserInput!) { createUser(input: $input) { user { ${USER} } temporaryPassword } }`,
  updateUser: `mutation($id: ID!, $input: UserInput!) { updateUser(id: $id, input: $input) { ${USER} } }`,
  resetUserPassword: `mutation($id: ID!) { resetUserPassword(id: $id) }`,
  updateRules: `mutation($input: AnalysisRulesInput!) { updateAnalysisRules(input: $input) { ${RULES} } }`,
  saveEvModel: `mutation($code: String!, $input: EvModelInput!) { saveEvModel(code: $code, input: $input) }`,
  deleteEvModel: `mutation($code: String!) { deleteEvModel(code: $code) }`,
  saveZone: `mutation($city: String!, $input: LowEmissionZoneInput!) { saveLowEmissionZone(city: $city, input: $input) }`,
  deleteZone: `mutation($city: String!) { deleteLowEmissionZone(city: $city) }`,
}

/** Roles an administrator gives in FleetPilot; the staff roles come from the LDAP groups. */
export const MANAGED_ROLES = ['client-manager', 'driver', 'garage', 'api-supplier'] as const
export const ALL_ROLES = ['platform-admin', 'fleet-manager', ...MANAGED_ROLES] as const
