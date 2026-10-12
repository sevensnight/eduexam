import { describe, expect, it } from 'vitest'
import {
  parseOptionList,
  remapAnswerLettersAfterRemoval,
  serializeOptions,
  validateChoiceQuestion,
} from './questionEditor'

describe('question editor helpers', () => {
  it('keeps and parses an existing multiple-choice question', () => {
    expect(parseOptionList('["A. Alpha","B. Beta","C. Gamma"]')).toEqual(['Alpha', 'Beta', 'Gamma'])
    expect(validateChoiceQuestion('multiple', ['Alpha', 'Beta', 'Gamma'], 'A,C')).toBeNull()
  })

  it('remaps answer letters when an option is deleted', () => {
    expect(remapAnswerLettersAfterRemoval(['A', 'C', 'D'], 1)).toEqual(['A', 'B', 'C'])
    expect(remapAnswerLettersAfterRemoval(['B', 'D'], 1)).toEqual(['C'])
  })

  it('rejects blank, duplicate, or external answers', () => {
    expect(validateChoiceQuestion('single', ['one', ''], 'A')).toMatch(/不能为空/)
    expect(validateChoiceQuestion('single', ['one', 'ONE'], 'A')).toMatch(/不能重复/)
    expect(validateChoiceQuestion('single', ['one', 'two'], 'C')).toMatch(/现有选项/)
    expect(serializeOptions([' one ', 'two'])).toBe('["A. one","B. two"]')
  })
})
