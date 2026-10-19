/** Number, date and money formatting in the language of the user (French by default). */
export function useFormat() {
  const { locale, t } = useI18n()
  const tag = computed(() => (locale.value === 'fr' ? 'fr-FR' : 'en-GB'))

  function number(value: number | null | undefined, digits = 0): string {
    if (value === null || value === undefined) return '-'
    return new Intl.NumberFormat(tag.value, { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value)
  }

  function km(value: number | null | undefined): string {
    return value === null || value === undefined ? '-' : t('app.km', { value: number(value) })
  }

  function money(value: number | null | undefined, digits = 0): string {
    if (value === null || value === undefined) return '-'
    return new Intl.NumberFormat(tag.value, { style: 'currency', currency: 'EUR', maximumFractionDigits: digits,
      minimumFractionDigits: digits }).format(value)
  }

  function date(value: string | null | undefined, style: 'short' | 'medium' | 'long' = 'medium'): string {
    if (!value) return '-'
    const parsed = new Date(value.length === 10 ? `${value}T12:00:00` : value)
    return new Intl.DateTimeFormat(tag.value, { dateStyle: style }).format(parsed)
  }

  function dateTime(value: string | null | undefined): string {
    if (!value) return '-'
    return new Intl.DateTimeFormat(tag.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
  }

  function percent(part: number, total: number): string {
    return total === 0 ? '0' : number((part / total) * 100, 0)
  }

  /** Label of an enum value: enumLabel('VehicleStatus', 'IN_REPAIR') gives "Au garage". */
  function label(enumName: string, value: string | null | undefined): string {
    return value ? t(`enum.${enumName}.${value}`) : '-'
  }

  return { number, km, money, date, dateTime, percent, label }
}
