<script setup lang="ts">
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([BarChart, GridComponent, TooltipComponent, CanvasRenderer])

/** Vertical bars over a few periods (savings per month...). The formatter gives the tooltip and axis labels. */
const props = withDefaults(defineProps<{
  bars: { x: string; y: number }[]
  format?: (value: number) => string
  height?: string
  color?: string
  /** Sparkline mode: no value axis, for small cards. */
  compact?: boolean
}>(), { format: (v: number) => v.toLocaleString(), height: '240px', color: '#0f766e', compact: false })

const option = computed(() => ({
  grid: { left: 8, right: 8, top: props.compact ? 4 : 12, bottom: 4, containLabel: true },
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v: number) => props.format(v),
    textStyle: { fontFamily: 'Inter Variable, sans-serif' } },
  xAxis: { type: 'category', data: props.bars.map(b => b.x), axisLine: { lineStyle: { color: '#e3e6eb' } },
    axisLabel: { color: '#667085', fontSize: 11 }, axisTick: { show: false } },
  yAxis: { type: 'value', show: !props.compact, splitLine: { lineStyle: { color: '#f2f4f7' } },
    axisLabel: { color: '#667085', fontSize: 11, formatter: (v: number) => props.format(v) } },
  series: [{ type: 'bar', data: props.bars.map(b => b.y), barMaxWidth: 28,
    itemStyle: { color: props.color, borderRadius: [4, 4, 0, 0] } }],
}))
</script>

<template>
  <VChart :option="option" autoresize :style="{ height }" />
</template>
