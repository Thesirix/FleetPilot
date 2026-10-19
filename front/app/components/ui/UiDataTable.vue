<script setup lang="ts" generic="T extends Record<string, any>">
import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-vue-next'

export interface Column {
  key: string
  label: string
  /** Property sent to the API to sort on this column, when the column is sortable. */
  sort?: string
  align?: 'left' | 'right'
  width?: string
}

const props = withDefaults(defineProps<{
  columns: Column[]
  rows: T[]
  rowKey?: string
  loading?: boolean
  clickable?: boolean
  emptyText?: string
  skeletonRows?: number
}>(), { rowKey: 'id', loading: false, clickable: false, emptyText: undefined, skeletonRows: 8 })

const sort = defineModel<string | undefined>('sort')
const emit = defineEmits<{ rowClick: [row: T] }>()
/** The key of a row; a dotted path such as "site.id" reaches into a nested object. */
const keyOf = (row: T) => props.rowKey.split('.').reduce<unknown>((value, part) =>
  (value as Record<string, unknown> | undefined)?.[part], row) as string

function direction(column: Column): 'asc' | 'desc' | null {
  if (!column.sort || !sort.value) return null
  const [field, dir] = sort.value.split(',')
  return field === column.sort ? (dir === 'desc' ? 'desc' : 'asc') : null
}

function toggle(column: Column) {
  if (!column.sort) return
  const current = direction(column)
  sort.value = `${column.sort},${current === 'asc' ? 'desc' : 'asc'}`
}

function ariaSort(column: Column) {
  const dir = direction(column)
  return dir === 'asc' ? 'ascending' : dir === 'desc' ? 'descending' : undefined
}

const showSkeleton = computed(() => props.loading && props.rows.length === 0)
</script>

<template>
  <div class="table-wrap" :class="{ busy: loading && rows.length > 0 }">
    <table class="table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" :style="{ width: column.width }"
            :class="{ right: column.align === 'right' }" :aria-sort="ariaSort(column)">
            <button v-if="column.sort" class="sort" @click="toggle(column)">
              {{ column.label }}
              <ArrowUp v-if="direction(column) === 'asc'" :size="13" />
              <ArrowDown v-else-if="direction(column) === 'desc'" :size="13" />
              <ArrowUpDown v-else :size="13" class="idle" />
            </button>
            <template v-else>{{ column.label }}</template>
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-if="showSkeleton">
          <tr v-for="n in skeletonRows" :key="`skeleton-${n}`">
            <td v-for="column in columns" :key="column.key"><span class="skeleton" style="height: 14px; width: 70%" /></td>
          </tr>
        </template>
        <template v-else>
          <tr v-for="row in rows" :key="keyOf(row)" :class="{ clickable }" :tabindex="clickable ? 0 : undefined"
            @click="clickable && emit('rowClick', row)" @keydown.enter="clickable && emit('rowClick', row)">
            <td v-for="column in columns" :key="column.key" :class="{ right: column.align === 'right' }">
              <slot :name="`cell-${column.key}`" :row="row">{{ row[column.key] ?? '-' }}</slot>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
    <UiEmpty v-if="!loading && rows.length === 0" :text="emptyText" />
  </div>
</template>

<style scoped>
.busy {
  opacity: 0.6;
  transition: opacity 0.15s;
}

.sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.sort:hover {
  color: var(--text);
}

.idle {
  opacity: 0.4;
}
</style>
