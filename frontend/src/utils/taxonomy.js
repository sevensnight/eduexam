// Direct counts and subtree totals have different meanings in the UI.
export function indexCategoryTree(nodes) {
  const index = new Map()
  function visit(items, ancestors = []) {
    let total = 0
    for (const item of items || []) {
      const path = [...ancestors, { id: item.id, name: item.name }]
      const entry = { ...item, path, subtreeCount: Number(item.question_count) || 0 }
      index.set(item.id, entry)
      entry.subtreeCount += visit(item.children, path)
      total += entry.subtreeCount
    }
    return total
  }
  visit(nodes)
  return index
}

export function canManageTaxonomy(item, user) {
  return user?.role === 'admin' || (user?.role === 'teacher' && item?.creator_id != null && item.creator_id === user.id)
}

export function canAddToCategory(item, user) {
  return user?.role === 'admin' || (user?.role === 'teacher' && (item?.creator_id == null || item.creator_id === user.id))
}

export function collectCategoryOrder(nodes, parentId = null) {
  const result = []
  for (const [index, node] of nodes.entries()) {
    result.push({ id: node.id, parent_id: parentId, sort_order: index })
    result.push(...collectCategoryOrder(node.children || [], node.id))
  }
  return result
}

export function safeTagColor(color) {
  return /^#[0-9a-f]{6}$/i.test(color || '') ? color : '#0066cc'
}
