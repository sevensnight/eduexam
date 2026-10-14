import { describe, expect, it } from 'vitest'
import { answerSelectionValues, choiceOptions, reviewOptionRows } from './answerOptions'

const question = { question_type: 'multiple', options: '["A. COUNT(*)","B. COUNT(列名)","C. SUM(列名)","D. AVG(列名)"]', user_answer: 'B,C,D', answer: 'B,C,D' }
const states = (row, reveal) => reviewOptionRows(row, reveal).map(option => option.state)

describe('作答选项的统一状态', () => {
  it('未公开时仅展示已选，不从颜色或文字泄露答案', () => {
    expect(states(question, false)).toEqual(['neutral', 'selected', 'selected', 'selected'])
    expect(reviewOptionRows(question)).toEqual(reviewOptionRows({ ...question, answer: 'A' }))
    const secret = { ...question, get correct_answer() { throw new Error('不得读取未公开的答案键') } }
    expect(() => reviewOptionRows(secret, false)).not.toThrow()
  })
  it('公布后的多选题同时区分选错、漏选和选对', () => {
    expect(states({ ...question, user_answer: 'A,C' }, true)).toEqual(['wrong', 'missed', 'correct', 'missed'])
    expect(states(question, true)).toEqual(['neutral', 'correct', 'correct', 'correct'])
  })
  it('单选题选错时，保留错误选择并标明正确项', () => {
    expect(states({ ...question, question_type: 'single', user_answer: 'A', answer: 'B' }, true))
      .toEqual(['wrong', 'answer', 'neutral', 'neutral'])
  })
  it('未作答时仅标出正确答案，不把所有选项判为错误', () => {
    expect(states({ ...question, user_answer: null }, true)).toEqual(['neutral', 'answer', 'answer', 'answer'])
    expect(states({ ...question, user_answer: '' }, false)).toEqual(['neutral', 'neutral', 'neutral', 'neutral'])
  })
  it('没有有效答案键时不能把已选项误标为选错', () => {
    for (const answer of [null, '', 'Z']) {
      expect(states({ ...question, answer }, true)).toEqual(['neutral', 'selected', 'selected', 'selected'])
    }
    expect(states({ ...question, question_type: 'single', answer: 'A,B' }, true)).toEqual(['neutral', 'selected', 'selected', 'selected'])
  })
  it('判断题支持布尔值、大小写，并使用中文选项', () => {
    const row = { question_type: 'truefalse', user_answer: false, answer: ' TRUE ' }
    expect(states(row, false)).toEqual(['neutral', 'selected'])
    expect(states(row, true)).toEqual(['answer', 'wrong'])
    expect(reviewOptionRows(row).map(option => option.text)).toEqual(['正确', '错误'])
    expect(answerSelectionValues('FALSE', 'truefalse')).toEqual(['False'])
  })
  it('解析选项时保留正文，支持空格、全角标点和无编号选项', () => {
    expect(choiceOptions([' A. 选择', 'B．投影', '连接', 'D、排序'], 'single')).toEqual([
      { value: 'A', marker: 'A', text: '选择' }, { value: 'B', marker: 'B', text: '投影' },
      { value: 'C', marker: 'C', text: '连接' }, { value: 'D', marker: 'D', text: '排序' },
    ])
    expect(answerSelectionValues(' b, C,B ', 'multiple')).toEqual(['B', 'C'])
  })
  it('错误格式、空内容和简答题安全降级，不生成虚假选项', () => {
    for (const options of [null, '', '{', '{}', 2]) expect(choiceOptions(options, 'single')).toEqual([])
    expect(choiceOptions([null, {}, '', 'D. 有效选项'], 'single')).toEqual([{ value: 'D', marker: 'D', text: '有效选项' }])
    expect(reviewOptionRows({ ...question, question_type: 'essay' }, true)).toEqual([])
    expect(answerSelectionValues('A', 'essay')).toEqual([])
  })
  it('统一两种答卷答案字段，且不修改原始数据', () => {
    const original = JSON.stringify(question)
    expect(states({ ...question, answer: 'A', correct_answer: 'B,C,D' }, true)).toEqual(['neutral', 'correct', 'correct', 'correct'])
    expect(JSON.stringify(question)).toBe(original)
  })
})
