export const roleLabel = role => ({ admin: '管理员', teacher: '教师', student: '学生' }[role] || role || '未设置')
export const userDisplayName = user => user?.real_name?.trim() || user?.username || '未命名用户'

export function matchesKeyword(values, keyword) {
  const query = (keyword || '').trim().toLocaleLowerCase()
  return !query || values.some(value => String(value ?? '').toLocaleLowerCase().includes(query))
}

// Spring's query parameters use camelCase; JSON response fields use snake_case.
export function userListParams(filters) {
  const params = {}
  if (filters.keyword?.trim()) params.keyword = filters.keyword.trim()
  if (filters.role) params.role = filters.role
  if (filters.class_name) params.className = filters.class_name
  return params
}

export function mergeEnrollmentCandidates(candidates, enrolledStudents, enrolledIds) {
  const available = new Map(candidates.map(student => [student.id, { ...student, available: true }]))
  for (const student of enrolledStudents) {
    if (!available.has(student.id)) available.set(student.id, { ...student, available: false })
  }
  for (const id of enrolledIds) {
    if (!available.has(id)) available.set(id, { id, username: String(id), real_name: '不可用账号', available: false })
  }
  return [...available.values()]
}

export function updateVisibleSelection(selectedIds, visibleIds, checked) {
  const selected = new Set(selectedIds)
  for (const id of visibleIds) {
    if (checked) selected.add(id)
    else selected.delete(id)
  }
  return [...selected]
}
