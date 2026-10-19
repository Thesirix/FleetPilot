import { describe, expect, it } from 'vitest'
import { dailyReadings } from '~/utils/mileage'

describe('dailyReadings', () => {
  it('keeps the latest reading of each day, oldest day first', () => {
    const readings = [
      { mileage: 33540, readAt: '2026-10-16T09:38:51Z' },
      { mileage: 33512, readAt: '2026-10-16T09:37:47Z' },
      { mileage: 33470, readAt: '2026-10-16T08:31:00Z' },
      { mileage: 33470, readAt: '2026-09-26T18:00:00Z' },
      { mileage: 28913, readAt: '2026-07-15T10:00:00Z' },
    ]
    expect(dailyReadings(readings).map(r => r.mileage)).toEqual([28913, 33470, 33540])
  })

  it('returns nothing for a vehicle without reading', () => {
    expect(dailyReadings([])).toEqual([])
  })
})
