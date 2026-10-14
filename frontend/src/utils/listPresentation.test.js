import { describe, expect, it } from 'vitest'
import { difficultyLabel, difficultyTone, displayQuestionAnswer, examPrimaryAction, listDateTime, questionTypeLabel } from './listPresentation'

describe('管理列表展示', () => {
  it('判断题答案使用中文展示，不改变原始数据或其他题型的内容', () => {
    const question = { question_type: 'truefalse', answer: 'True' }
    expect(displayQuestionAnswer(question)).toBe('正确')
    expect(question.answer).toBe('True')
    expect(displayQuestionAnswer({ question_type: 'truefalse', answer: ' FALSE ' })).toBe('错误')
    expect(displayQuestionAnswer({ question_type: 'truefalse', answer: false })).toBe('错误')
    expect(displayQuestionAnswer({ question_type: 'essay', answer: 'True' })).toBe('True')
    expect(displayQuestionAnswer({ question_type: 'single', answer: 'A' })).toBe('A')
    expect(displayQuestionAnswer({ question_type: 'essay', answer: 0 })).toBe('0')
    expect(displayQuestionAnswer({ question_type: 'truefalse', answer: '正确' })).toBe('正确')
    expect(displayQuestionAnswer({ question_type: 'truefalse', answer: null })).toBe('暂未设置')
  })

  it('按上海时区显示完整日期和秒，不依赖浏览器所在时区', () => {
    expect(listDateTime('2026-06-18T06:29:04')).toEqual({ date: '2026/06/18', time: '06:29:04' })
    expect(listDateTime('2026-06-17T22:29:04Z')).toEqual({ date: '2026/06/18', time: '06:29:04' })
    expect(listDateTime('2026-06-18T00:00:00')).toEqual({ date: '2026/06/18', time: '00:00:00' })
  })

  it('缺失或无效时间显示明确占位，不生成无效日期', () => {
    expect(listDateTime(null, '不限')).toEqual({ date: '不限', time: '' })
    expect(listDateTime('invalid')).toEqual({ date: '未设置', time: '' })
  })

  it('题型和难度使用中文并兼容未知类型', () => {
    expect(questionTypeLabel('multiple')).toBe('多选')
    expect(questionTypeLabel('new-type')).toBe('new-type')
    expect(difficultyLabel('hard')).toBe('困难')
    expect(difficultyTone('hard')).toBe('danger')
    expect(difficultyTone(null)).toBe('info')
  })

  it('草稿优先编辑，已发布和已关闭考试优先查看统计', () => {
    expect(examPrimaryAction({ status: 'draft' })).toEqual({ command: 'edit', label: '编辑' })
    expect(examPrimaryAction({ status: 'published' }).command).toBe('stats')
    expect(examPrimaryAction({ status: 'closed' }).command).toBe('stats')
  })
})
