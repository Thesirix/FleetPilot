<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

/** A single series over time (mileage history...). */
const props = withDefaults(defineProps<{ points: { x: string; y: number }[]; unit?: string; height?: string }>(),
  { unit: '', height: '220px' })

const option = computed(() => ({
  grid: { left: 8, right: 12, top: 12, bottom: 4, containLabel: true },
  tooltip: { trigger: 'axis', valueFormatter: (v: number) => `${v.toLocaleString()} ${props.unit}` },
  xAxis: { type: 'category', data: props.points.map(p => p.x), boundaryGap: false,
    axisLine: { lineStyle: { color: '#e3e6eb' } }, axisLabel: { color: '#667085', fontSize: 11 }, axisTick: { show: false } },
  yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: '#f2f4f7' } },
    axisLabel: { color: '#667085', fontSize: 11, formatter: (v: number) => v.toLocaleString() } },
  series: [{ type: 'line', data: props.points.map(p => p.y), smooth: true, showSymbol: true, symbolSize: 5,
    lineStyle: { color: '#0f766e', width: 2 }, itemStyle: { color: '#0f766e' },
    areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
      colorStops: [{ offset: 0, color: 'rgba(15,118,110,0.16)' }, { offset: 1, color: 'rgba(15,118,110,0)' }] } } }],
}))
</script>

<template>
  <VChart :option="option" autoresize :style="{ height }" />
</template>
