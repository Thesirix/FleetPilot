<script setup lang="ts">
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([PieChart, TooltipComponent, CanvasRenderer])

/** Donut with its legend on the side, for a breakdown of a few values (energies, statuses). */
const props = defineProps<{ items: { label: string; value: number; color: string }[]; centerLabel?: string;
  centerValue?: string }>()

const total = computed(() => props.items.reduce((sum, item) => sum + item.value, 0))
const option = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b} : {c} ({d} %)', textStyle: { fontFamily: 'Inter Variable, sans-serif' } },
  series: [{
    type: 'pie',
    radius: ['62%', '88%'],
    avoidLabelOverlap: false,
    label: { show: false },
    itemStyle: { borderColor: '#fff', borderWidth: 2 },
    data: props.items.map(item => ({ name: item.label, value: item.value, itemStyle: { color: item.color } })),
  }],
}))
</script>

<template>
  <div class="donut">
    <div class="chart-box">
      <VChart :option="option" autoresize class="chart" />
      <div v-if="centerValue" class="center">
        <span class="center-value num">{{ centerValue }}</span>
        <span class="center-label">{{ centerLabel }}</span>
      </div>
    </div>
    <ul class="legend">
      <li v-for="item in items" :key="item.label">
        <span class="dot" :style="{ background: item.color }" />
        <span class="grow name">{{ item.label }}</span>
        <span class="num secondary">{{ item.value.toLocaleString() }}</span>
        <span class="num muted pct">{{ total ? Math.round((item.value / total) * 100) : 0 }} %</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.donut {
  display: flex;
  align-items: center;
  gap: 20px;
}

.chart-box {
  position: relative;
  flex: none;
  width: 132px;
  height: 132px;
}

.chart {
  width: 100%;
  height: 100%;
}

.center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.center-value {
  font-size: 20px;
  font-weight: 650;
}

.center-label {
  color: var(--text-muted);
  font-size: 11px;
}

.legend {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: var(--text-sm);
}

.legend li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 3px;
  flex: none;
}

.name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pct {
  flex: none;
  width: 40px;
  text-align: right;
}

@media (max-width: 520px) {
  .donut {
    flex-direction: column;
    align-items: stretch;
  }

  .chart-box {
    align-self: center;
  }
}
</style>
