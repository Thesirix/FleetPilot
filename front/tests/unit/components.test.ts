import { mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeAll, describe, expect, it } from 'vitest'
import CritAirBadge from '~/components/fleet/CritAirBadge.vue'
import DeadlineRemaining from '~/components/fleet/DeadlineRemaining.vue'
import FleetBadge from '~/components/fleet/FleetBadge.vue'
import UiPagination from '~/components/ui/UiPagination.vue'

// The portal speaks French by default; the test browser would otherwise pick English.
beforeAll(async () => {
  await useNuxtApp().$i18n.setLocale('fr')
})

describe('DeadlineRemaining', () => {
  const render = (daysRemaining: number | null, kilometresRemaining: number | null) =>
    mountSuspended(DeadlineRemaining, { props: { daysRemaining, kilometresRemaining } })

  it('shows the limit already passed first', async () => {
    expect((await render(-3, 5000)).text()).toBe('3 jours de retard')
    expect((await render(40, -250)).text()).toBe('Dépassée de 250 km')
  })

  it('otherwise shows the date before the mileage', async () => {
    expect((await render(12, 900)).text()).toBe('Dans 12 jours')
    expect((await render(0, null)).text()).toBe("Aujourd'hui")
    expect((await render(null, 1200)).text()).toMatch(/^Dans 1\s200 km$/)
  })

  it('is red when late', async () => {
    expect((await render(-1, null)).find('.late').exists()).toBe(true)
    expect((await render(5, null)).find('.late').exists()).toBe(false)
  })
})

describe('badges', () => {
  it('translate and color the vehicle status', async () => {
    const badge = await mountSuspended(FleetBadge, { props: { type: 'VehicleStatus', value: 'IN_REPAIR' } })
    expect(badge.text()).toBe('Au garage')
    expect(badge.classes()).toContain('warning')
  })

  it('show the Crit\'Air E sticker of zero-emission vehicles', async () => {
    const sticker = await mountSuspended(CritAirBadge, { props: { value: 0 } })
    expect(sticker.text()).toBe('E')
    expect(sticker.classes()).toContain('critair-e')
  })
})

describe('UiPagination', () => {
  it('disables previous on the first page and moves forward', async () => {
    const pagination = await mountSuspended(UiPagination, {
      props: { page: 0, totalPages: 3, totalElements: 61, 'onUpdate:page': (p: number) => pagination.setProps({ page: p }) },
    })
    const [previous, next] = pagination.findAll('button')
    expect(previous!.attributes('disabled')).toBeDefined()
    expect(pagination.text()).toContain('61 résultats')
    await next!.trigger('click')
    expect(pagination.emitted('update:page')?.[0]).toEqual([1])
  })
})
