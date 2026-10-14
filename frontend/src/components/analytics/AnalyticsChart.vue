<template>
  <div ref="element" class="analytics-chart" :style="{ height: `${height}px` }" role="img" :aria-label="label" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { init, use } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'

use([BarChart, PieChart, GridComponent, TooltipComponent, SVGRenderer])

const props = defineProps({
  option: { type: Object, required: true },
  label: { type: String, required: true },
  height: { type: Number, default: 320 },
})
const element = ref(null)
let chart
let observer
let resizeFrame

function render() {
  chart?.setOption({
    animation: false,
    textStyle: { fontFamily: getComputedStyle(element.value).fontFamily },
    ...props.option,
  }, { notMerge: true })
}

onMounted(() => {
  chart = init(element.value, null, { renderer: 'svg' })
  render()
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame)
    resizeFrame = requestAnimationFrame(() => chart?.resize())
  })
  observer.observe(element.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(resizeFrame)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.analytics-chart { position: relative; isolation: isolate; width: 100%; min-width: 0; }
</style>
