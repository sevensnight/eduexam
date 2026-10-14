import { describe, expect, it } from 'vitest'
import { matchesKeyword, mergeEnrollmentCandidates, roleLabel, updateVisibleSelection, userDisplayName, userListParams } from './management'

describe('课程与用户管理展示', () => {
  it('使用统一中文角色与姓名回退', () => {
    expect(roleLabel('teacher')).toBe('教师')
    expect(userDisplayName({ real_name: ' ', username: 'student1' })).toBe('student1')
  })
  it('搜索不区分大小写，忽略空字段与首尾空格', () => {
    expect(matchesKeyword([null, 'SQL 数据库'], ' sql ')).toBe(true)
    expect(matchesKeyword(['teacher'], 'student')).toBe(false)
  })
  it('班级筛选使用后端接收的 className 参数，不包含空条件', () => {
    expect(userListParams({ keyword: '  王  ', role: 'student', class_name: '软件1班' })).toEqual({ keyword: '王', role: 'student', className: '软件1班' })
    expect(userListParams({ keyword: ' ', role: undefined, class_name: null })).toEqual({})
  })
  it('切换班级或搜索后，批量勾选只改变当前可见学生，不丢失隐藏选择', () => {
    expect(updateVisibleSelection([1, 3], [2, 3], true)).toEqual([1, 3, 2])
    expect(updateVisibleSelection([1, 2, 3], [2, 3], false)).toEqual([1])
  })
  it('保留已选但不可用的账号供用户显式处理，不自动移除', () => {
    const result = mergeEnrollmentCandidates([{ id: 1, real_name: '可选学生' }], [{ id: 2, real_name: '已停用学生' }], [1, 2, 3])
    expect(result.map(item => [item.id, item.available])).toEqual([[1, true], [2, false], [3, false]])
    expect(result[2].real_name).toBe('不可用账号')
  })
})
