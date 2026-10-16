export function getNotificationTarget(target) {
  if (typeof target !== 'string') return ''
  const normalized = target.trim()
  return normalized.startsWith('/') && !normalized.startsWith('//') ? normalized : ''
}
