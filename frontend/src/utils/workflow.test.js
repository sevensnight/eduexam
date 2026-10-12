import { describe, expect, it, vi } from 'vitest'
import { selectRecordForRow } from './recordSelection'
import { commitRoleChange } from './userRole'
import { correctnessForScore } from './grading'

describe('frontend workflow helpers', () => {
  it('selects the exact attempt by record id', () => {
    const records = [
      { id: 11, student_id: 7, total_score: 60 },
      { id: 12, student_id: 7, total_score: 90 },
    ]
    expect(selectRecordForRow(records, { record_id: 12, student_id: 7 })?.total_score).toBe(90)
  })

  it('rolls a role back when persistence fails', async () => {
    const row = { id: 1, role: 'student' }
    await expect(commitRoleChange(row, 'teacher', vi.fn().mockRejectedValue(new Error('no')))).rejects.toThrow('no')
    expect(row.role).toBe('student')
  })

  it('derives grading status from score boundaries', () => {
    expect(correctnessForScore(0, 10)).toBe('N')
    expect(correctnessForScore(4, 10)).toBe('P')
    expect(correctnessForScore(10, 10)).toBe('Y')
  })
})
