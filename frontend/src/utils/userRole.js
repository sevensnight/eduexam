export async function commitRoleChange(row, newRole, updateRole) {
  const previousRole = row.role
  if (newRole === previousRole) return false
  row.role = newRole
  try {
    await updateRole(newRole)
    return true
  } catch (error) {
    row.role = previousRole
    throw error
  }
}
