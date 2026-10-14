<template>
  <div class="responsive-records">
    <table v-if="rows.length" class="responsive-table" :aria-label="label">
      <colgroup><col v-for="column in columns" :key="column.key" :style="{ width: column.width }"></colgroup>
      <thead><tr><th v-for="column in columns" :key="column.key" scope="col">{{ column.label }}</th></tr></thead>
      <tbody><tr v-for="(row, index) in rows" :key="row[rowKey] ?? index">
        <td v-for="column in columns" :key="column.key" :data-label="column.label" :class="{ 'record-primary': column === columns[0], 'record-action': column.key === 'actions' }">
          <div class="record-cell"><slot :name="column.key" :row="row" :index="index">{{ row[column.key] ?? '未提供' }}</slot></div>
        </td>
      </tr></tbody>
    </table>
    <el-empty v-else :description="emptyText" :image-size="64" />
  </div>
</template>
<script setup>
defineProps({ rows: { type: Array, default: () => [] }, columns: { type: Array, required: true }, rowKey: { type: String, default: 'id' }, label: String, emptyText: { type: String, default: '暂无记录' } })
</script>
<style scoped>
.responsive-records { container-type: inline-size; container-name: records; min-width: 0; }
.responsive-table { width: 100%; table-layout: fixed; border-collapse: collapse; font-size: 13px; text-align: left; }
th { padding: 13px 14px; color: var(--app-text-secondary); background: var(--el-fill-color-light); font-size: 12px; font-weight: 400; }
th:first-child { border-radius: 9px 0 0 9px; } th:last-child { border-radius: 0 9px 9px 0; }
td { padding: 18px 14px; border-bottom: 1px solid var(--app-border); vertical-align: middle; overflow-wrap: anywhere; }
tbody tr:last-child td { border-bottom: 0; }
tbody tr:hover { background: #fafbfd; }
.record-cell { min-width: 0; }
@container records (max-width: 720px) {
  .responsive-table, tbody { display: block; }
  colgroup { display: none; }
  thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
  tbody tr { display: grid; gap: 10px; padding: 20px 0; border-bottom: 1px solid var(--app-border); }
  tbody tr:first-child { padding-top: 4px; } tbody tr:last-child { border-bottom: 0; }
  td { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 12px; padding: 0; border: 0; font-size: 12px; }
  td::before { content: attr(data-label); color: var(--app-text-secondary); }
  td.record-primary, td.record-action { display: block; }
  td.record-primary { margin-bottom: 4px; }
  td.record-action { padding-top: 4px; text-align: right; }
  td.record-primary::before, td.record-action::before { display: none; }
}
</style>
