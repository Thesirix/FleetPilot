/**
 * Filters, sort and page of a list, kept in the URL: a filtered list can be bookmarked, shared or reopened with the
 * back button. Changing any filter brings the list back to the first page.
 */
export function useUrlState<T extends Record<string, string | number>>(defaults: T) {
  const route = useRoute()
  const router = useRouter()

  const initial = { ...defaults }
  for (const key of Object.keys(defaults) as (keyof T)[]) {
    const raw = route.query[key as string]
    if (typeof raw === 'string') {
      initial[key] = (typeof defaults[key] === 'number' ? Number(raw) || 0 : raw) as T[keyof T]
    }
  }
  const state = reactive(initial) as T

  watch(() => Object.entries(state).filter(([key]) => key !== 'page').map(([, v]) => v).join('|'), () => {
    if ('page' in state) (state as Record<string, unknown>).page = 0
  })

  watch(state, () => {
    const query: Record<string, string> = {}
    for (const [key, value] of Object.entries(state)) {
      if (value !== '' && value !== defaults[key]) query[key] = String(value)
    }
    router.replace({ query })
  }, { deep: true })

  return state
}

/** Removes empty strings, so that unset filters are not sent to the API. */
export function compact(values: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(values).filter(([, v]) => v !== '' && v !== undefined && v !== null))
}
