// GraphQL operations of the energy transition.
// The schema lives in back/graphql-gateway/src/main/resources/graphql/energy.graphqls.

export type Eligibility = 'ZERO_EMISSION' | 'ELIGIBLE' | 'ELIGIBLE_WITH_CHARGING' | 'NOT_YET' | 'TO_CONFIRM'
  | 'NOT_APPLICABLE'

export const ELIGIBILITIES: Eligibility[] = ['ELIGIBLE', 'ELIGIBLE_WITH_CHARGING', 'TO_CONFIRM', 'NOT_YET',
  'ZERO_EMISSION', 'NOT_APPLICABLE']

export interface EvModel {
  code: string
  name: string
  segment: string
  batteryKwh: number
  wltpRangeKm: number
  realisticRangeKm: number
  consumptionKwh100km: number
  fastChargeKw: number
  monthlyRent: number
}

export interface EnergyAssessment {
  vehicleId: string
  companyName: string | null
  registration: string
  make: string | null
  model: string | null
  category: string
  energy: string
  critAir: number | null
  co2GramsPerKm: number | null
  siteCity: string | null
  status: Eligibility
  dataSource: 'TELEMETRY' | 'REGISTER'
  reason: string | null
  annualKm: number
  activeDays: number | null
  p95DailyKm: number | null
  longestDayKm: number | null
  daysBeyondRange: number | null
  requiredRangeKm: number | null
  evModel: EvModel | null
  currentAnnualCost: number | null
  evAnnualCost: number | null
  annualSaving: number | null
  co2CurrentKg: number | null
  co2EvKg: number | null
  renewalDate: string | null
  zfeZone: string | null
  zfeBannedFrom: string | null
}

export interface EnergySummary {
  vehicles: number
  zeroEmission: number
  eligible: number
  eligibleWithCharging: number
  notYet: number
  toConfirm: number
  notApplicable: number
  zeroEmissionShare: number
  potentialShare: number
  annualSavingPotential: number
  co2CurrentTonnes: number
  co2AvoidableTonnes: number
  zfeExposed: number
  zfeBannedNow: number
  computedAt: string | null
}

export interface PlanQuarter {
  quarter: string
  vehicles: number
  annualSaving: number
  co2AvoidedTonnes: number
  zeroEmissionShareAfter: number
}

export interface QuotaYear { year: number; renewals: number; lowEmissionRenewals: number; share: number; objective: number; met: boolean }

const ASSESSMENT = `vehicleId companyName registration make model category energy critAir co2GramsPerKm siteCity status
  dataSource reason annualKm activeDays p95DailyKm longestDayKm daysBeyondRange requiredRangeKm
  evModel { code name segment batteryKwh wltpRangeKm realisticRangeKm consumptionKwh100km fastChargeKw monthlyRent }
  currentAnnualCost evAnnualCost annualSaving co2CurrentKg co2EvKg renewalDate zfeZone zfeBannedFrom`

export const EQ = {
  overview: `query EnergyOverview($companyId: ID) {
    energySummary(companyId: $companyId) { vehicles zeroEmission eligible eligibleWithCharging notYet toConfirm
      notApplicable zeroEmissionShare potentialShare annualSavingPotential co2CurrentTonnes co2AvoidableTonnes
      zfeExposed zfeBannedNow computedAt }
    energyPlan(companyId: $companyId) {
      quarters { quarter vehicles annualSaving co2AvoidedTonnes zeroEmissionShareAfter }
      quotas { year renewals lowEmissionRenewals share objective met } } }`,
  assessments: `query EnergyAssessments($filter: EnergyFilter, $sort: String, $page: Int, $size: Int) {
    energyAssessments(filter: $filter, sort: $sort, page: $page, size: $size) {
      content { ${ASSESSMENT} } page size totalElements totalPages } }`,
  vehicle: `query EnergyAssessment($vehicleId: ID!) { energyAssessment(vehicleId: $vehicleId) { ${ASSESSMENT} } }`,
}
