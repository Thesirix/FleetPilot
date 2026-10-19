// GraphQL operations of the integration bus (partner flows, journal of the exchanges) and of the fuel cards.
// The schema lives in back/graphql-gateway/src/main/resources/graphql/integration.graphqls.

import { accessToken } from '~/utils/auth'

export type Flow = 'GARAGE_QUOTES' | 'LESSOR_INVOICES' | 'FUEL_CARDS' | 'INSURER_CLAIMS'
export type ExchangeStatus = 'RUNNING' | 'PROCESSED' | 'PARTIAL' | 'REJECTED' | 'DUPLICATE' | 'FAILED'

export const FLOWS: Flow[] = ['GARAGE_QUOTES', 'LESSOR_INVOICES', 'FUEL_CARDS', 'INSURER_CLAIMS']
export const EXCHANGE_STATUSES: ExchangeStatus[] = ['PROCESSED', 'PARTIAL', 'REJECTED', 'DUPLICATE', 'FAILED']
export const FUEL_ANOMALIES = ['WRONG_FUEL', 'OVER_CAPACITY', 'TOO_FREQUENT', 'ODOMETER', 'UNKNOWN_VEHICLE']

export interface CamelRoute { id: string; description: string | null; status: string; endpoint: string | null }

export interface IntegrationFlow {
  flow: Flow
  folder: string | null
  routes: CamelRoute[]
  activity: {
    exchanges: number
    processed: number
    partial: number
    rejected: number
    failed: number
    duplicates: number
    records: number
    lastReceivedAt: string | null
  }
}

export interface Exchange {
  id: string
  flow: Flow
  partner: string | null
  source: string
  receivedAt: string
  completedAt: string | null
  status: ExchangeStatus
  records: number
  accepted: number
  rejected: number
  summary: string | null
  resultRef: string | null
  replayOf: string | null
}

export interface ExchangeIssue { line: number | null; reference: string | null; severity: 'ERROR' | 'WARNING'; code: string; message: string }

export interface ExchangeDetail { summary: Exchange; contentType: string | null; payload: string | null; issues: ExchangeIssue[] }

export interface FuelTransaction {
  id: string
  provider: string
  reference: string
  card: string
  occurredAt: string
  station: string
  city: string | null
  product: string
  litres: number
  unitPrice: number
  amount: number
  odometer: number | null
  registration: string
  vehicleId: string | null
  energy: string | null
  anomalies: string[]
}

export interface FuelSummary {
  from: string
  to: string
  transactions: number
  amount: number
  litres: number
  averagePrice: number
  withAnomaly: number
  amountWithAnomaly: number
  anomaliesByType: { key: string; count: number }[]
  months: { month: string; amount: number; litres: number }[]
}

const EXCHANGE = 'id flow partner source receivedAt completedAt status records accepted rejected summary resultRef replayOf'

export const IQ = {
  console: `query IntegrationConsole($flow: Flow, $status: ExchangeStatus, $page: Int, $size: Int) {
    integrationFlows { flow folder routes { id description status endpoint }
      activity { exchanges processed partial rejected failed duplicates records lastReceivedAt } }
    exchanges(flow: $flow, status: $status, page: $page, size: $size) {
      content { ${EXCHANGE} } page size totalElements totalPages } }`,
  exchange: `query Exchange($id: ID!) { exchange(id: $id) { summary { ${EXCHANGE} } contentType payload
    issues { line reference severity code message } } }`,
  fuel: `query Fuel($filter: FuelFilter, $page: Int, $size: Int) {
    fuelTransactions(filter: $filter, page: $page, size: $size) {
      content { id provider reference card occurredAt station city product litres unitPrice amount odometer
        registration vehicleId energy anomalies }
      page size totalElements totalPages }
    fuelSummary { from to transactions amount litres averagePrice withAnomaly amountWithAnomaly
      anomaliesByType { key count } months { month amount litres } } }`,
}

export const IM = {
  replay: `mutation Replay($id: ID!) { replayExchange(id: $id) { fileName message } }`,
}

/** Drops a file in the inbox of a flow, as a partner would on the SFTP server. */
export async function dropFile(flow: Flow, file: File): Promise<{ fileName: string; message: string }> {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch(`/api/v1/integration/inbox/${flow}`, {
    method: 'POST', body, headers: { Authorization: `Bearer ${await accessToken()}` },
  })
  const json = await response.json()
  if (!response.ok) {
    throw new Error(json.detail ?? json.title ?? response.statusText)
  }
  return json
}

/** Downloads the file of an exchange as the partner sent it. */
export async function downloadExchange(exchange: Exchange): Promise<void> {
  const response = await fetch(`/api/v1/integration/exchanges/${exchange.id}/content`, {
    headers: { Authorization: `Bearer ${await accessToken()}` },
  })
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = exchange.source
  link.click()
  URL.revokeObjectURL(url)
}
