import { describe, expect, it } from 'vitest'
import { canAddToCategory, canManageTaxonomy, collectCategoryOrder, indexCategoryTree, safeTagColor } from './taxonomy'

describe('分类管理视图数据', () => {
  const tree = [{ id: 1, name: '计算机', question_count: 2, children: [
    { id: 2, name: '数据结构', question_count: 7, children: [] },
    { id: 3, name: '算法', question_count: 5, children: [{ id: 4, name: '排序', question_count: 1 }] },
  ] }]
  it('保留本级题数，另外计算含子分类的题数及完整路径，不修改源数据', () => {
    const snapshot = JSON.stringify(tree)
    const index = indexCategoryTree(tree)
    expect(index.size).toBe(4)
    expect(index.get(1).question_count).toBe(2)
    expect(index.get(1).subtreeCount).toBe(15)
    expect(index.get(3).subtreeCount).toBe(6)
    expect(index.get(4).path.map(item => item.name)).toEqual(['计算机', '算法', '排序'])
    expect(JSON.stringify(tree)).toBe(snapshot)
  })
  it('兼容空目录和未设置题数', () => {
    expect(indexCategoryTree([]).size).toBe(0)
    expect(indexCategoryTree([{ id: 9, name: '空目录' }]).get(9).subtreeCount).toBe(0)
  })
  it('拖动后保留每层父节点与同级顺序', () => {
    expect(collectCategoryOrder(tree)).toEqual([
      { id: 1, parent_id: null, sort_order: 0 }, { id: 2, parent_id: 1, sort_order: 0 },
      { id: 3, parent_id: 1, sort_order: 1 }, { id: 4, parent_id: 3, sort_order: 0 },
    ])
  })
  it('教师能使用共享分类，但只能编辑或删除自己的分类与标签', () => {
    const teacher = { id: 8, role: 'teacher' }
    expect(canManageTaxonomy({ creator_id: 8 }, teacher)).toBe(true)
    expect(canManageTaxonomy({ creator_id: null }, teacher)).toBe(false)
    expect(canManageTaxonomy({ creator_id: 9 }, teacher)).toBe(false)
    expect(canAddToCategory({ creator_id: null }, teacher)).toBe(true)
    expect(canAddToCategory({ creator_id: 9 }, teacher)).toBe(false)
    expect(canManageTaxonomy({}, { role: 'admin' })).toBe(true)
    expect(canManageTaxonomy({ creator_id: 8 }, { id: 8, role: 'student' })).toBe(false)
  })
  it('保留已保存的标签颜色，非法或缺失颜色使用默认色', () => {
    expect(safeTagColor('#44aaBB')).toBe('#44aaBB')
    expect(safeTagColor(null)).toBe('#0066cc')
    expect(safeTagColor('red;display:none')).toBe('#0066cc')
  })
})
