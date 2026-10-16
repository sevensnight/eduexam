import { describe, expect, it } from 'vitest'
import { getNotificationTarget } from './notification'

describe('getNotificationTarget', () => {
  it('accepts only internal application paths', () => {
    expect(getNotificationTarget('/exams/8/grade')).toBe('/exams/8/grade')
    expect(getNotificationTarget('  /exams/8/result  ')).toBe('/exams/8/result')
  })

  it('rejects external, protocol-relative and missing destinations', () => {
    expect(getNotificationTarget('https://example.com')).toBe('')
    expect(getNotificationTarget('//example.com')).toBe('')
    expect(getNotificationTarget(null)).toBe('')
  })
})
