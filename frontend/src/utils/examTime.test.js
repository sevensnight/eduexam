import { describe, expect, it } from 'vitest'
import { calculateDeadlineMs, parseAppDateTimeMs, secondsUntil } from './examTime'

describe('exam deadline helpers', () => {
  it('uses the earliest of the duration and configured close time', () => {
    const startedAt = '2026-10-12T10:00:00'
    const deadline = calculateDeadlineMs({
      startedAt,
      durationMinutes: 60,
      closeTime: '2026-10-12T10:30:00',
    })
    expect(deadline).toBe(Date.parse('2026-10-12T10:30:00+08:00'))
  })

  it('recomputes from wall-clock time instead of counting interval ticks', () => {
    const deadline = Date.parse('2026-10-12T10:01:00+08:00')
    expect(secondsUntil(deadline, Date.parse('2026-10-12T10:00:40.100+08:00'))).toBe(20)
    expect(secondsUntil(deadline, Date.parse('2026-10-12T10:02:00+08:00'))).toBe(0)
  })

  it('treats zone-less server values as Asia/Shanghai and preserves explicit offsets', () => {
    expect(parseAppDateTimeMs('2026-10-12T10:00:00'))
      .toBe(Date.parse('2026-10-12T10:00:00+08:00'))
    expect(parseAppDateTimeMs('2026-10-12T10:00:00Z'))
      .toBe(Date.parse('2026-10-12T10:00:00Z'))
  })
})
