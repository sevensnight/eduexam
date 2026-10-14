import { describe, expect, it } from 'vitest'
import { createChartTooltip, formatChartTooltip, summarizeCategories, formatNumber, formatPercent } from './analytics'

describe('statistics presentation data', () => {
  it('uses the total, not the largest category, as the percentage denominator', () => {
    const result = summarizeCategories([
      { id: 1, category_name: 'Web 安全', question_count: 4 },
      { id: 2, category_name: '安全运维', question_count: 2 },
    ])
    expect(result.total).toBe(6)
    expect(formatPercent(result.rows[0].share)).toBe('66.7%')
    expect(formatPercent(result.rows[1].share)).toBe('33.3%')
  })

  it('preserves every category and total when grouping the long tail in a donut', () => {
    const input = Array.from({ length: 12 }, (_, index) => ({ id: index + 1, category_name: `分类 ${index}`, question_count: index + 1 }))
    const result = summarizeCategories(input)
    expect(result.rows).toHaveLength(12)
    expect(result.rows[0].question_count).toBe(12)
    expect(result.segments).toHaveLength(6)
    expect(result.segments.at(-1).value).toBe(28)
    expect(result.segments.reduce((sum, segment) => sum + segment.value, 0)).toBe(result.total)
    expect(input[0].question_count).toBe(1)
  })

  it('keeps different categories with the same name separate', () => {
    const result = summarizeCategories([
      { id: 1, category_name: '基础', question_count: 3 },
      { id: 2, category_name: '基础', question_count: 2 },
    ])
    expect(result.rows.map((row) => row.key)).toEqual([1, 2])
    expect(result.total).toBe(5)
  })

  it('handles empty and invalid counts without misleading or non-finite values', () => {
    const result = summarizeCategories([{ question_count: 0 }, { question_count: -1 }, { question_count: 'bad' }])
    expect(result).toEqual({ rows: [], segments: [], total: 0, average: 0 })
    expect(formatNumber(null)).toBe('-')
    expect(formatPercent(undefined)).toBe('-')
    expect(formatPercent(0)).toBe('0%')
  })

  it('escapes tooltip text without interpreting category names as HTML', () => {
    expect(formatChartTooltip(['<img src=x onerror="alert(1)"> & \'分类\'', '2 题   占比 50%']))
      .toBe('&lt;img src=x onerror=&quot;alert(1)&quot;&gt; &amp; &#39;分类&#39;<br>2 题   占比 50%')
    expect(formatChartTooltip(['得分占比 <60%', null, 0])).toBe('得分占比 &lt;60%<br><br>0')
  })

  it('keeps chart tooltips non-interactive and confined without a global high z-index', () => {
    const tooltip = createChartTooltip({ trigger: 'item' })
    expect(tooltip).toMatchObject({ renderMode: 'html', enterable: false, confine: true, transitionDuration: 0, trigger: 'item' })
    expect(tooltip.extraCssText).toContain('z-index:2;')
    expect(tooltip.extraCssText).toContain('overflow-wrap:anywhere;')
  })
})
