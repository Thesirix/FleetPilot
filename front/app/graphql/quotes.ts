// GraphQL operations of the savings module (garage quotes, garage network, reference tariff).
// The schema lives in back/graphql-gateway/src/main/resources/graphql/quotes.graphqls.

export interface GarageRef { id: string; name: string; city: string; network: string }

export interface QuoteVehicle {
  id: string
  companyId: string
  registration: string
  make: string | null
  model: string | null
  category: string
  energy: string | null
}

export interface QuoteSummary {
  id: string
  reference: string
  status: string
  garage: GarageRef
  registration: string
  vehicle: QuoteVehicle | null
  companyId: string | null
  channel: string
  submittedAt: string
  amount: number
  referenceAmount: number | null
  overcharge: number
  acceptedAmount: number | null
  savings: number | null
  findingCount: number
  autoApproved: boolean
  counterOffer: number | null
  revision: number
  invoiceDisputed: boolean
}

export interface QuoteLine {
  id: string
  position: number
  kind: string
  code: string | null
  label: string
  quantity: number
  unitPrice: number
  amount: number
  referenceQuantity: number | null
  referenceUnitPrice: number | null
  referenceAmount: number | null
  overcharge: number
  finding: string | null
  findingDetail: string | null
}

export interface QuoteHistory {
  at: string
  party: string
  author: string
  action: string
  amount: number | null
  message: string | null
}

export interface Quote extends QuoteSummary {
  garageReference: string | null
  mileage: number | null
  description: string
  analysedAt: string | null
  initialAmount: number
  decidedAt: string | null
  decidedBy: string | null
  decisionComment: string | null
  completedOn: string | null
  invoiceNumber: string | null
  invoiceAmount: number | null
  invoicedOn: string | null
  invoiceVariance: number | null
  lines: QuoteLine[]
  history: QuoteHistory[]
  actions: string[]
}

export interface MonthlySavings { month: string; quotes: number; quoted: number; accepted: number; savings: number }
export interface FindingCount { finding: string; lines: number; overcharge: number }
export interface GarageRanking { garageId: string; name: string; city: string; quotes: number; overchargeRate: number; savings: number }

export interface SavingsSummary {
  quotes: number
  openQuotes: number
  toNegotiate: number
  toReview: number
  received: number
  amountQuoted: number
  amountAccepted: number
  savings: number
  savingsRate: number
  pendingOvercharge: number
  autoApprovalRate: number
  disputedInvoices: number
  months: MonthlySavings[]
  findings: FindingCount[]
  garagesToWatch: GarageRanking[]
}

export interface GarageSummary {
  id: string
  code: string
  name: string
  network: string
  brand: string | null
  city: string
  postalCode: string
  region: string
  hourlyRate: number
  approved: boolean
  rating: number | null
  latitude: number | null
  longitude: number | null
}

export interface GarageStats {
  quotes: number
  openQuotes: number
  acceptedQuotes: number
  rejectedQuotes: number
  amountQuoted: number
  overchargeDetected: number
  overchargeRate: number
  savings: number
  averageDecisionHours: number | null
  disputedInvoices: number
  lastQuoteAt: string | null
}

export interface Garage extends GarageSummary {
  siret: string
  address: string
  regionLabel: string
  email: string
  phone: string | null
  approvedSince: string | null
  stats: GarageStats
  quotes: QuoteSummary[]
  version: number
}

export interface PartPrice { code: string; label: string; family: string; priceCar: number; priceVan: number; priceTruck: number }
export interface StandardTime { code: string; label: string; labourCategory: string; hoursCar: number; hoursVan: number; hoursTruck: number }
export interface LabourRate { region: string; regionLabel: string; labourCategory: string; hourlyRate: number; truckHourlyRate: number }
export interface Tariff { parts: PartPrice[]; operations: StandardTime[]; rates: LabourRate[] }

/** A line being typed in the quote form of the garage space. */
export interface LineDraft { kind: string; code: string; label: string; quantity: number | null; unitPrice: number | null }

const QUOTE_SUMMARY = `id reference status garage { id name city network } registration
  vehicle { id companyId registration make model category energy } companyId channel submittedAt amount
  referenceAmount overcharge acceptedAmount savings findingCount autoApproved counterOffer revision invoiceDisputed`

const QUOTE = `${QUOTE_SUMMARY} garageReference mileage description analysedAt initialAmount decidedAt decidedBy
  decisionComment completedOn invoiceNumber invoiceAmount invoicedOn invoiceVariance actions
  lines { id position kind code label quantity unitPrice amount referenceQuantity referenceUnitPrice referenceAmount
    overcharge finding findingDetail }
  history { at party author action amount message }`

const GARAGE_SUMMARY = 'id code name network brand city postalCode region hourlyRate approved rating latitude longitude'

const GARAGE = `${GARAGE_SUMMARY} siret address regionLabel email phone approvedSince version
  stats { quotes openQuotes acceptedQuotes rejectedQuotes amountQuoted overchargeDetected overchargeRate savings
    averageDecisionHours disputedInvoices lastQuoteAt }`

const SAVINGS = `quotes openQuotes toNegotiate toReview received amountQuoted amountAccepted savings savingsRate
  pendingOvercharge autoApprovalRate disputedInvoices months { month quotes quoted accepted savings }
  findings { finding lines overcharge } garagesToWatch { garageId name city quotes overchargeRate savings }`

export const QQ = {
  quotes: `query($filter: QuoteFilter, $page: Int, $size: Int, $sort: [String!]) {
    quotes(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${QUOTE_SUMMARY} } } }`,
  quote: `query($id: ID!) { quote(id: $id) { ${QUOTE} } }`,
  savings: `query($companyId: ID) { savings(companyId: $companyId) { ${SAVINGS} } }`,
  garages: `query($filter: GarageFilter, $page: Int, $size: Int, $sort: [String!]) {
    garages(filter: $filter, page: $page, size: $size, sort: $sort) {
      page size totalElements totalPages content { ${GARAGE_SUMMARY} } } }`,
  garage: `query($id: ID!) { garage(id: $id) { ${GARAGE} quotes(size: 15) { ${QUOTE_SUMMARY} } } }`,
  garageSpace: `{ myGarage { ${GARAGE} } open: quotes(filter: { open: true }, size: 50, sort: ["submittedAt,desc"]) {
      totalElements content { ${QUOTE_SUMMARY} } }
    toInvoice: quotes(filter: { status: ACCEPTED }, size: 50, sort: ["submittedAt,desc"]) {
      totalElements content { ${QUOTE_SUMMARY} } }
    recent: quotes(filter: { open: false }, size: 10, sort: ["submittedAt,desc"]) { content { ${QUOTE_SUMMARY} } } }`,
  tariff: `{ tariff { parts { code label family priceCar priceVan priceTruck }
    operations { code label labourCategory hoursCar hoursVan hoursTruck }
    rates { region regionLabel labourCategory hourlyRate truckHourlyRate } } }`,
  vehicleQuotes: `query($id: ID!) { vehicle(id: $id) { id quotes { ${QUOTE_SUMMARY} } } }`,
}

export const QM = {
  submitQuote: `mutation($input: SubmitQuoteInput!) { submitQuote(input: $input) { id reference status } }`,
  reviseQuote: `mutation($id: ID!, $lines: [QuoteLineInput!]!, $message: String) {
    reviseQuote(id: $id, lines: $lines, message: $message) { id status revision } }`,
  answerCounterOffer: `mutation($id: ID!, $accept: Boolean!, $message: String) {
    answerCounterOffer(id: $id, accept: $accept, message: $message) { id status } }`,
  completeQuote: `mutation($id: ID!) { completeQuote(id: $id) { id status } }`,
  invoiceQuote: `mutation($id: ID!, $number: String!, $amount: Float!) {
    invoiceQuote(id: $id, number: $number, amount: $amount) { id status invoiceDisputed } }`,
  identifyQuoteVehicle: `mutation($id: ID!, $registration: String!) {
    identifyQuoteVehicle(id: $id, registration: $registration) { id status } }`,
  counterOffer: `mutation($id: ID!, $amount: Float!, $message: String) {
    counterOffer(id: $id, amount: $amount, message: $message) { id status counterOffer } }`,
  acceptQuote: `mutation($id: ID!, $comment: String) { acceptQuote(id: $id, comment: $comment) { id status } }`,
  rejectQuote: `mutation($id: ID!, $reason: String!) { rejectQuote(id: $id, reason: $reason) { id status } }`,
  createGarage: `mutation($input: GarageInput!) { createGarage(input: $input) { id name } }`,
  updateGarage: `mutation($id: ID!, $input: GarageInput!) { updateGarage(id: $id, input: $input) { id name } }`,
  approveGarage: `mutation($id: ID!, $approved: Boolean!, $rating: Float) {
    approveGarage(id: $id, approved: $approved, rating: $rating) { id approved rating approvedSince } }`,
  updateLabourRate: `mutation($region: Region!, $category: LabourCategory!, $hourlyRate: Float!, $truckHourlyRate: Float!) {
    updateLabourRate(region: $region, category: $category, hourlyRate: $hourlyRate, truckHourlyRate: $truckHourlyRate) {
      region labourCategory hourlyRate truckHourlyRate } }`,
}

/** Open statuses: the quote still waits for a decision. */
export const OPEN_STATUSES = ['RECEIVED', 'IN_REVIEW', 'TO_NEGOTIATE']
