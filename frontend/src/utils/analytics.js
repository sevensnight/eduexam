const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 })

export function formatNumber(value) {
  if (value == null || !Number.isFinite(Number(value))) return '-'
  return numberFormatter.format(Number(value))
}

export function formatPercent(ratio) {
  if (ratio == null || !Number.isFinite(Number(ratio))) return '-'
  return `${numberFormatter.format(Number(ratio) * 100)}%`
}

/** HTML tooltips must never treat category names or score ranges as markup. */
export function formatChartTooltip(lines) {
  const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
  return lines.map((line) => String(line ?? '').replace(/[&<>"']/g, (character) => entities[character])).join('<br>')
}

export function createChartTooltip(options) {
  return {
    // Non-interactive HTML lets the pointer reach the next bar/segment even
    // when the previous tooltip covers it. Keep its stacking local to the chart.
    renderMode: 'html', className: 'analytics-tooltip', confine: true, enterable: false,
    transitionDuration: 0, backgroundColor: '#ffffff', borderColor: '#e5e5e9', padding: [10, 14],
    textStyle: { color: '#303641', fontSize: 12 },
    extraCssText: 'z-index:2;max-width:min(260px,100%);white-space:normal;overflow-wrap:anywhere;',
    ...options,
  }
}

/** Counts are direct category membership, not parent-category aggregates. */
export function summarizeCategories(items = [], segmentLimit = 5) {
  const sorted = items
    .map((item, index) => ({
      ...item,
      key: item.id ?? `category-${index}`,
      category_name: item.category_name || '未命名分类',
      question_count: Number(item.question_count),
    }))
    .filter((item) => Number.isFinite(item.question_count) && item.question_count > 0)
    .sort((a, b) => b.question_count - a.question_count)
  const total = sorted.reduce((sum, item) => sum + item.question_count, 0)
  const rows = sorted.map((item, index) => ({ ...item, rank: index + 1, share: item.question_count / total }))
  const limit = Math.max(1, Math.floor(Number(segmentLimit)) || 5)
  const segments = rows.slice(0, limit).map((item) => ({
    key: item.key,
    name: item.category_name,
    value: item.question_count,
    share: item.share,
  }))
  if (rows.length > limit) {
    const value = rows.slice(limit).reduce((sum, item) => sum + item.question_count, 0)
    segments.push({ key: 'remaining-categories', name: `其他 ${rows.length - limit} 个分类`, value, share: value / total })
  }
  return { rows, segments, total, average: rows.length ? total / rows.length : 0 }
}
