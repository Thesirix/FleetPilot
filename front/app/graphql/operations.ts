// GraphQL operations of the reports, the indicators and the journal of the e-mails.
// The schema lives in back/graphql-gateway/src/main/resources/graphql/operations.graphqls.

export interface Indicators {
  vehicles: number
  totalCost: number
  monthlyCostPerVehicle: number
  costPerKm: number | null
  rent: number
  maintenance: number
  claims: number
  claimsPer100Vehicles: number
  immobilisationRate: number
  leasedShare: number
  asOf: string
}

export interface ReportTypeInfo { type: string; title: string; format: string; periodic: boolean }

export interface Report {
  id: string
  type: string
  format: string
  title: string
  companyId: string | null
  periodFrom: string | null
  periodTo: string | null
  status: string
  requestedAt: string
  completedAt: string | null
  fileName: string | null
  sizeBytes: number | null
  rowCount: number | null
  error: string | null
}

export interface NotificationSummary {
  id: string
  template: string
  recipient: string
  recipientName: string | null
  subject: string | null
  status: string
  attempts: number
  lastError: string | null
  relatedType: string | null
  relatedId: string | null
  attachments: string | null
  createdAt: string
  sentAt: string | null
}

export interface NotificationDetail { summary: NotificationSummary; bodyHtml: string | null; messageId: string | null }
export interface NotificationStats { sent: number; queued: number; failed: number; byTemplate: { template: string; count: number }[] }

const REPORT = `id type format title companyId periodFrom periodTo status requestedAt completedAt fileName sizeBytes
  rowCount error`
const NOTIFICATION = `id template recipient recipientName subject status attempts lastError relatedType relatedId
  attachments createdAt sentAt`

export const OQ = {
  reportsPage: `query($companyId: ID) {
    indicators(companyId: $companyId) { vehicles totalCost monthlyCostPerVehicle costPerKm rent maintenance claims
      claimsPer100Vehicles immobilisationRate leasedShare asOf }
    reportTypes { type title format periodic } }`,
  reports: `{ reports(size: 30) { totalElements content { ${REPORT} } } }`,
  notifications: `query($filter: NotificationFilter, $page: Int, $size: Int) {
    notifications(filter: $filter, page: $page, size: $size) { page size totalElements totalPages
      content { ${NOTIFICATION} } }
    notificationStats { sent queued failed byTemplate { template count } } }`,
  notification: `query($id: ID!) { notification(id: $id) { summary { ${NOTIFICATION} } bodyHtml messageId } }`,
}

export const OM = {
  requestReport: `mutation($type: ReportType!, $companyId: ID, $from: Date, $to: Date) {
    requestReport(type: $type, companyId: $companyId, from: $from, to: $to) { ${REPORT} } }`,
  retryNotification: `mutation($id: ID!) { retryNotification(id: $id) { summary { id status attempts } } }`,
}

/** Downloads the file of a ready report with the access token (a plain link would not carry it). */
export async function downloadReport(report: Report, token: string | null): Promise<void> {
  const response = await fetch(`/api/v1/reports/${report.id}/file`, { headers: { Authorization: `Bearer ${token}` } })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = report.fileName ?? 'rapport'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
