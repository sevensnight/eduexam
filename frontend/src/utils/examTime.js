const APP_TIME_ZONE_OFFSET = '+08:00'
const EXPLICIT_ZONE_RE = /(?:z|[+-]\d{2}:?\d{2})$/i

/** Parse backend LocalDateTime values as Asia/Shanghai, not browser-local time. */
export function parseAppDateTimeMs(value) {
  if (value == null || value === '') return Number.NaN
  if (value instanceof Date) return value.getTime()
  const raw = String(value).trim()
  const timestamp = Date.parse(EXPLICIT_ZONE_RE.test(raw) ? raw : `${raw}${APP_TIME_ZONE_OFFSET}`)
  return Number.isFinite(timestamp) ? timestamp : Number.NaN
}

export function formatAppDateTime(value, fallback = '-') {
  const timestamp = parseAppDateTimeMs(value)
  return Number.isFinite(timestamp)
    ? new Date(timestamp).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
    : fallback
}

export function calculateDeadlineMs({ startedAt, durationMinutes, closeTime }) {
  const startedAtMs = parseAppDateTimeMs(startedAt)
  const duration = Number(durationMinutes)
  let deadline = Number.isFinite(startedAtMs) && Number.isFinite(duration) && duration > 0
    ? startedAtMs + duration * 60_000
    : Number.POSITIVE_INFINITY

  const closeAtMs = parseAppDateTimeMs(closeTime)
  if (Number.isFinite(closeAtMs)) deadline = Math.min(deadline, closeAtMs)
  return deadline
}

export function secondsUntil(deadlineMs, nowMs = Date.now()) {
  if (!Number.isFinite(deadlineMs)) return 0
  return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000))
}
