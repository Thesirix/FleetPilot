/** A mileage reading as the API returns it. */
export interface Reading {
  mileage: number
  readAt: string
}

/**
 * Keeps the latest reading of each day, oldest day first, for the mileage curve: a driver who saves a fill-up and an
 * inspection on the same day gives several readings that would otherwise draw a flat run of points with one date.
 * The readings come newest first, as the API sorts them.
 */
export function dailyReadings<T extends Reading>(newestFirst: readonly T[]): T[] {
  const days = new Set<string>()
  const kept: T[] = []
  for (const reading of newestFirst) {
    const day = reading.readAt.slice(0, 10)
    if (days.has(day)) continue
    days.add(day)
    kept.push(reading)
  }
  return kept.reverse()
}
