import { describe, expect, it } from 'vitest'
import { moveExamQuestion, percentage, recordState, reviewAnswer, reviewQuestion, validateExamSettings } from './examPresentation'

describe('统一考试展示', () => {
  it('未公布及未批改的记录不展示分数', () => {
    expect(recordState({ total_score: 99, status: 'graded', essay_graded: true, result_released: false }).score).toBe('暂未公布')
    expect(recordState({ total_score: 99, status: 'submitted', result_released: true }).score).toBe('待批改')
    expect(recordState({ total_score: 0, status: 'graded', essay_graded: true, result_released: true, passed: false }).score).toBe(0)
  })
  it('统一答卷字段，保留零分与判断题的中文答案', () => {
    expect(reviewQuestion({ title: '题干', answer: 'False', score: 0 }).max_score).toBe(0)
    expect(reviewAnswer('False', 'truefalse')).toBe('错误')
    expect(reviewAnswer('True', 'truefalse')).toBe('正确')
    expect(reviewAnswer(null, 'essay')).toBe('未作答')
  })
  it('移动题目后顺序连续，不改变原数组和分值', () => {
    const questions = [{ question_id: 1, score: 5, order_num: 1 }, { question_id: 2, score: 8, order_num: 2 }]
    expect(moveExamQuestion(questions, 1, 0)).toEqual([{ question_id: 2, score: 8, order_num: 1 }, { question_id: 1, score: 5, order_num: 2 }])
    expect(questions[0].question_id).toBe(1)
    expect(moveExamQuestion(questions, 0, -1)).toBe(questions)
  })
  it('百分比保持有限值并限制在合法范围', () => {
    expect(percentage(0.625)).toBe(62.5)
    expect(percentage(Number.NaN)).toBe(0)
    expect(percentage(1.2)).toBe(100)
  })
  it('考试表单检查分值、时长、次数与时间窗', () => {
    const form = { title: '测试', duration_minutes: 60, pass_score: 3, allow_retake: true, max_attempts: 2, questions: [{ score: 5 }] }
    expect(validateExamSettings(form)).toBe('')
    expect(validateExamSettings({ ...form, pass_score: 6 })).toContain('及格分')
    expect(validateExamSettings({ ...form, duration_minutes: null })).toContain('时长')
    expect(validateExamSettings({ ...form, questions: [{ score: null }] })).toContain('分值')
    expect(validateExamSettings({ ...form, open_time: '2026-10-14T10:00:00', close_time: '2026-10-14T09:00:00' })).toContain('关闭时间')
  })
  it('编辑历史试卷时不额外收紧后端允许的时长、次数与题目分值', () => {
    const form = { title: '长时考试', duration_minutes: 1440, pass_score: 60, allow_retake: true, max_attempts: 100, questions: [{ score: 100 }] }
    expect(validateExamSettings(form)).toBe('')
    expect(validateExamSettings({ ...form, duration_minutes: 1 })).toBe('')
    expect(validateExamSettings({ ...form, duration_minutes: 1441 })).toContain('时长')
    expect(validateExamSettings({ ...form, max_attempts: 101 })).toContain('次数')
    expect(validateExamSettings({ ...form, max_attempts: 1.5 })).toContain('次数')
  })
})
