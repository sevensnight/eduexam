<template>
  <div class="category-distribution">
    <section class="distribution-panel ranking-panel" :aria-label="title">
      <header class="panel-heading">
        <div><h2>{{ title }}</h2><p>按题量排序，比较各分类的内容覆盖</p></div>
        <div class="view-switch" role="group" :aria-label="`${title}展示方式`">
          <button type="button" :aria-pressed="mode === 'chart'" @click="mode = 'chart'">图表</button>
          <button type="button" :aria-pressed="mode === 'table'" @click="mode = 'table'">明细</button>
        </div>
      </header>

      <template v-if="summary.total">
        <template v-if="mode === 'chart'">
          <div class="chart-unit"><span>分类名称</span><span>题目数量 / 题</span></div>
          <AnalyticsChart class="ranking-chart" :option="rankingOption" :height="336" :label="rankingDescription" />
          <footer class="panel-footer">
            <span>共 {{ summary.rows.length }} 个分类<span v-if="summary.rows.length > 8">，图表展示前 8 个</span></span>
            <button type="button" class="text-action" @click="mode = 'table'">查看全部明细 <el-icon><ArrowRight /></el-icon></button>
          </footer>
        </template>
        <template v-else>
          <div class="table-search">
            <label :for="searchId" class="sr-only">搜索分类名称</label>
            <el-input :id="searchId" v-model="query" :prefix-icon="Search" placeholder="搜索分类名称" clearable />
            <span>{{ filteredRows.length }} 个分类</span>
          </div>
          <el-table :data="pageRows" :empty-text="query.trim() ? '没有匹配的分类' : '暂无分类数据'" class="category-table">
            <el-table-column label="分类名称" min-width="130">
              <template #default="{ row }"><span class="table-category" :title="row.category_name"><span class="row-rank">{{ row.rank }}</span>{{ row.category_name }}</span></template>
            </el-table-column>
            <el-table-column label="题量" width="76" align="right"><template #default="{ row }"><strong>{{ formatNumber(row.question_count) }}</strong></template></el-table-column>
            <el-table-column label="占比" width="86" align="right"><template #default="{ row }">{{ formatPercent(row.share) }}</template></el-table-column>
          </el-table>
          <footer class="panel-footer table-footer">
            <span>占比以全部已分类题目为基数</span>
            <el-pagination v-if="filteredRows.length > pageSize" v-model:current-page="page" :page-size="pageSize" :total="filteredRows.length" :pager-count="5" layout="prev, pager, next" small />
          </footer>
        </template>
      </template>
      <div v-else class="distribution-empty">
        <el-icon><DataAnalysis /></el-icon><h3>暂无分类数据</h3><p>题目设置分类后，即可在这里查看分布。</p>
      </div>
    </section>

    <section class="distribution-panel composition-panel" :aria-label="compositionTitle">
      <header class="panel-heading"><div><h2>{{ compositionTitle }}</h2><p>各分类题量占全部已分类题目的比例</p></div></header>
      <template v-if="summary.total">
        <div class="donut-wrap">
          <AnalyticsChart :option="compositionOption" :height="204" :label="compositionDescription" />
          <div class="donut-label" aria-hidden="true"><strong>{{ formatNumber(summary.total) }}</strong><span>已分类题目</span></div>
        </div>
        <ul class="composition-legend" aria-label="分类占比明细">
          <li v-for="(segment, index) in summary.segments" :key="segment.key">
            <span class="legend-name" :title="segment.name"><i :style="{ background: colors[index] }" aria-hidden="true" />{{ segment.name }}</span>
            <strong>{{ formatNumber(segment.value) }}<small> 题</small></strong>
            <span class="legend-share">{{ formatPercent(segment.share) }}</span>
          </li>
        </ul>
        <p class="composition-note">{{ summary.rows.length > 5 ? '前 5 个分类单独展示，其余分类合并计入。' : `当前共覆盖 ${summary.rows.length} 个有题分类。` }}</p>
      </template>
      <div v-else class="distribution-empty"><el-icon><PieChart /></el-icon><h3>暂无占比数据</h3><p>有分类题目后，自动生成构成分析。</p></div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, useId, watch } from 'vue'
import { ArrowRight, DataAnalysis, PieChart, Search } from '@element-plus/icons-vue'
import AnalyticsChart from './AnalyticsChart.vue'
import { createChartTooltip, formatChartTooltip, formatNumber, formatPercent, summarizeCategories } from '@/utils/analytics'

const props = defineProps({
  items: { type: Array, default: () => [] },
  title: { type: String, default: '分类题量分布' },
  compositionTitle: { type: String, default: '题库构成' },
})
const mode = ref('chart')
const query = ref('')
const page = ref(1)
const pageSize = 8
const searchId = useId()
const colors = ['#0066cc', '#4b8bd0', '#80abd9', '#abc6e3', '#c7d7e9', '#e5eaf1']
const summary = computed(() => summarizeCategories(props.items))
const rankedRows = computed(() => summary.value.rows.slice(0, 8))
const filteredRows = computed(() => summary.value.rows.filter((row) => row.category_name.toLocaleLowerCase().includes(query.value.trim().toLocaleLowerCase())))
const pageRows = computed(() => filteredRows.value.slice((page.value - 1) * pageSize, page.value * pageSize))
watch([query, () => props.items], () => { page.value = 1 })

const rankingDescription = computed(() => `${props.title}。${rankedRows.value.map((row) => `${row.category_name} ${row.question_count} 题，占比 ${formatPercent(row.share)}`).join('；')}。可切换明细查看全部分类。`)
const compositionDescription = computed(() => `${props.compositionTitle}，共 ${summary.value.total} 题。${summary.value.segments.map((item) => `${item.name} ${formatPercent(item.share)}`).join('；')}。`)

const rankingOption = computed(() => ({
  grid: { left: 0, right: 40, top: 10, bottom: 8, containLabel: true },
  tooltip: createChartTooltip({
    trigger: 'axis',
    // Keep the row highlight behind the bars and their value labels.
    axisPointer: { type: 'shadow', z: 0, shadowStyle: { color: 'rgba(0, 102, 204, 0.05)' } },
    formatter: (params) => {
      const row = rankedRows.value[params[0]?.dataIndex]
      return row ? formatChartTooltip([row.category_name, `${formatNumber(row.question_count)} 题   占比 ${formatPercent(row.share)}`]) : ''
    },
  }),
  xAxis: {
    type: 'value', min: 0, minInterval: 1, splitNumber: 4,
    axisLabel: { color: '#6e6e73', fontSize: 11 }, axisTick: { show: false }, axisLine: { show: false },
    splitLine: { lineStyle: { color: '#edf0f4', type: 'dashed' } },
  },
  yAxis: {
    type: 'category', inverse: true, data: rankedRows.value.map((row) => row.category_name),
    axisTick: { show: false }, axisLine: { show: false },
    axisLabel: { color: '#454549', fontSize: 12, width: 100, overflow: 'truncate', margin: 16 },
  },
  series: [{
    type: 'bar', barMaxWidth: 16,
    data: rankedRows.value.map((row, index) => ({ value: row.question_count, itemStyle: { color: index === 0 ? '#0066cc' : '#8db3df' } })),
    itemStyle: { borderRadius: [0, 4, 4, 0] },
    label: { show: true, position: 'right', distance: 10, color: '#454549', fontSize: 12, formatter: ({ value }) => formatNumber(value) },
    emphasis: { itemStyle: { color: '#0066cc' } },
  }],
}))

const compositionOption = computed(() => ({
  color: colors,
  tooltip: createChartTooltip({
    trigger: 'item',
    formatter: ({ dataIndex }) => {
      const item = summary.value.segments[dataIndex]
      return item ? formatChartTooltip([item.name, `${formatNumber(item.value)} 题   占比 ${formatPercent(item.share)}`]) : ''
    },
  }),
  series: [{
    type: 'pie', radius: ['71%', '91%'], center: ['50%', '50%'],
    label: { show: false }, labelLine: { show: false },
    itemStyle: { borderColor: '#ffffff', borderWidth: 3, borderRadius: 3 },
    emphasis: { scaleSize: 3 },
    data: summary.value.segments.map((item) => ({ name: item.name, value: item.value })),
  }],
}))
</script>

<style scoped>
.category-distribution { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(290px, 1fr); gap: 20px; }
.distribution-panel { min-width: 0; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); padding: 24px; }
.panel-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 22px; }
.panel-heading h2 { margin: 0 0 5px; font-size: 16px; line-height: 1.5; letter-spacing: -.02em; }
.panel-heading p { margin: 0; color: var(--app-text-secondary); font-size: 12px; line-height: 1.7; }
.view-switch { display: inline-flex; flex-shrink: 0; gap: 2px; padding: 3px; border-radius: 8px; background: var(--app-bg); }
.view-switch button { border: 0; background: transparent; color: var(--app-text-secondary); border-radius: 6px; padding: 5px 12px; font-size: 12px; line-height: 1.6; }
.view-switch button[aria-pressed="true"] { background: var(--app-surface); color: var(--app-accent); box-shadow: 0 1px 3px #1d1d1f0d; font-weight: 600; }
.view-switch button:hover { color: var(--app-accent); }
.chart-unit { display: flex; justify-content: space-between; color: var(--app-text-secondary); font-size: 11px; margin-bottom: 6px; }
.panel-footer { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; border-top: 1px solid #eff0f3; padding-top: 16px; margin-top: 14px; color: var(--app-text-secondary); font-size: 11px; }
.text-action { display: inline-flex; align-items: center; gap: 5px; background: transparent; border: 0; color: var(--app-accent); padding: 3px 0; font-size: 12px; white-space: nowrap; }
.text-action:hover { color: #0052a3; text-decoration: underline; }
.donut-wrap { position: relative; isolation: isolate; width: 204px; max-width: 100%; margin: 0 auto 22px; }
/* The chart and its tooltip paint above the total, within this donut only. */
.donut-wrap .analytics-chart { position: relative; z-index: 1; }
.donut-label { position: absolute; inset: 0; z-index: 0; display: flex; flex-direction: column; justify-content: center; align-items: center; pointer-events: none; }
.donut-label strong { font-size: 32px; line-height: 1.3; letter-spacing: -.04em; font-weight: 600; font-variant-numeric: tabular-nums; }
.donut-label span { color: var(--app-text-secondary); font-size: 11px; margin-top: 4px; }
.composition-legend { list-style: none; padding: 0; margin: 0; display: grid; gap: 10px; }
.composition-legend li { display: grid; grid-template-columns: minmax(0, 1fr) auto 52px; align-items: center; column-gap: 12px; font-size: 12px; font-variant-numeric: tabular-nums; }
.legend-name { display: block; white-space: nowrap; text-overflow: ellipsis; overflow: hidden; color: #454549; }
.legend-name i { display: inline-block; height: 8px; width: 8px; border-radius: 2px; margin-right: 9px; }
.composition-legend strong { font-weight: 500; }
.composition-legend small { color: var(--app-text-secondary); font-weight: 400; font-size: 11px; }
.legend-share { text-align: right; color: var(--app-text-secondary); }
.composition-note { margin: 18px 0 0; padding-top: 14px; border-top: 1px solid #eff0f3; font-size: 11px; color: var(--app-text-secondary); }
.table-search { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
.table-search .el-input { max-width: 260px; }
.table-search > span { white-space: nowrap; color: var(--app-text-secondary); font-size: 12px; }
.category-table { font-size: 12px; font-variant-numeric: tabular-nums; }
.category-table :deep(.el-table__cell) { padding: 10px 0; }
.category-table strong { font-weight: 600; }
.table-category { overflow-wrap: anywhere; }
.row-rank { display: inline-block; min-width: 20px; margin-right: 8px; color: var(--app-text-secondary); font-size: 11px; }
.table-footer :deep(.el-pagination) { padding: 0; }
.distribution-empty { min-height: 355px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--app-text-secondary); text-align: center; }
.distribution-empty > .el-icon { font-size: 32px; color: #7a93ae; margin-bottom: 16px; }
.distribution-empty h3 { font-size: 14px; color: var(--app-text); margin-bottom: 8px; }
.distribution-empty p { font-size: 12px; margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 1100px) { .category-distribution { grid-template-columns: minmax(0, 1.4fr) minmax(270px, 1fr); gap: 16px; } .distribution-panel { padding: 20px; } }
@media (max-width: 960px) { .category-distribution { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 767px) {
  .distribution-panel { padding: 18px 16px; }
  .panel-heading { flex-wrap: wrap; gap: 12px; margin-bottom: 18px; }
  .panel-heading h2 { font-size: 15px; }
  .panel-heading p { font-size: 11px; }
  .panel-footer { font-size: 10px; }
  .table-search { gap: 10px; }
  .composition-legend { gap: 12px; }
  .donut-wrap { margin-bottom: 20px; }
}
</style>
