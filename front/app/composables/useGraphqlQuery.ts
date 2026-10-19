import { type FieldError, GraphqlError, graphql } from '~/utils/graphql'

/**
 * Reactive GraphQL query: runs again whenever the variables change (filters, page, sort) and exposes the loading,
 * error and offline states that every page displays the same way.
 */
export function useGraphqlQuery<T>(query: string, variables: MaybeRefOrGetter<Record<string, unknown>> = {},
  options: { cacheKey?: MaybeRefOrGetter<string | undefined> } = {}) {
  const data = shallowRef<T | null>(null)
  const pending = ref(true)
  const error = shallowRef<GraphqlError | null>(null)
  const stale = ref(false)
  let generation = 0

  async function refresh() {
    const current = ++generation
    pending.value = true
    try {
      const result = await graphql<T>(query, toValue(variables), { cacheKey: toValue(options.cacheKey) })
      if (current !== generation) {
        return
      }
      data.value = result.data
      stale.value = result.stale
      error.value = null
    } catch (e) {
      if (current === generation) {
        error.value = e instanceof GraphqlError ? e : new GraphqlError(String(e), 'ERROR')
      }
    } finally {
      if (current === generation) {
        pending.value = false
      }
    }
  }

  watch(() => JSON.stringify(toValue(variables)), refresh, { immediate: true })

  return { data, pending, error, stale, refresh }
}

/** Runs a mutation, shows a confirmation toast, and turns errors into a toast with the translated message. */
export function useMutation() {
  const toast = useToast()
  const { t, te } = useI18n()
  const running = ref(false)
  const fieldErrors = ref<Record<string, string>>({})

  async function run<T>(mutation: string, variables: Record<string, unknown>, success?: string): Promise<T | null> {
    running.value = true
    fieldErrors.value = {}
    try {
      const result = await graphql<T>(mutation, variables)
      if (success) {
        toast.success(success)
      }
      return result.data
    } catch (e) {
      const err = e instanceof GraphqlError ? e : new GraphqlError(String(e), 'ERROR')
      fieldErrors.value = Object.fromEntries(err.fieldErrors.map(f => [f.field, fieldMessage(f)]))
      const key = `errors.${err.code}`
      toast.error(te(key) ? t(key) : err.message, err.correlationId)
      return null
    } finally {
      running.value = false
    }
  }

  /** The message of a refused field in the language of the portal, from the rule and its limits. */
  function fieldMessage(error: FieldError): string {
    const p = error.params ?? {}
    switch (error.constraint) {
      case 'NotBlank':
      case 'NotNull':
        return t('validation.required')
      case 'NotEmpty':
        return t('validation.notEmpty')
      case 'Email':
        return t('validation.email')
      case 'Size': {
        const min = Number(p.min ?? 0)
        const max = Number(p.max ?? Number.MAX_SAFE_INTEGER)
        if (min > 0 && max < 2_147_483_647) return t('validation.sizeBetween', { min, max })
        return min > 0 ? t('validation.sizeMin', { min }) : t('validation.sizeMax', { max })
      }
      case 'Min':
      case 'DecimalMin':
        return t('validation.min', { value: p.value })
      case 'Max':
      case 'DecimalMax':
        return t('validation.max', { value: p.value })
      case 'Positive':
        return t('validation.positive')
      case 'PositiveOrZero':
        return t('validation.positiveOrZero')
      case 'Past':
      case 'PastOrPresent':
        return t('validation.past')
      case 'Future':
      case 'FutureOrPresent':
        return t('validation.future')
      case 'Digits':
        return t('validation.digits', { integer: p.integer, fraction: p.fraction })
      case 'Pattern':
        return te(`validation.pattern.${error.field}`) ? t(`validation.pattern.${error.field}`) : t('validation.pattern.default')
      case 'RegistrationNumber':
        return t('validation.registration')
      case 'Vin':
        return t('validation.vin')
      default:
        return te(`validation.${error.constraint}`) ? t(`validation.${error.constraint}`) : error.message
    }
  }

  return { run, running, fieldErrors }
}
