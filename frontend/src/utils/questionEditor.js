export function optionLetter(index) {
  return String.fromCharCode(65 + index)
}

export function parseOptionList(raw) {
  if (!raw) return []
  try {
    const values = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(values)) return []
    return values.map((value) => String(value).replace(/^[A-Z]\.\s*/, ''))
  } catch {
    return []
  }
}

export function parseAnswerLetters(answer) {
  if (!answer) return []
  return String(answer)
    .split(',')
    .map((value) => value.trim().toUpperCase())
    .filter((value) => /^[A-Z]$/.test(value))
}

export function remapAnswerLettersAfterRemoval(answers, removedIndex) {
  const removedCode = 65 + removedIndex
  return answers
    .map((answer) => String(answer).toUpperCase().charCodeAt(0))
    .filter((code) => Number.isFinite(code) && code !== removedCode)
    .map((code) => String.fromCharCode(code > removedCode ? code - 1 : code))
    .sort()
}

export function validateChoiceQuestion(type, options, answer) {
  if (!['single', 'multiple'].includes(type)) return null

  const normalizedOptions = options.map((option) => String(option ?? '').trim())
  if (normalizedOptions.length < 2) return '选择题至少需要两个选项'
  if (normalizedOptions.some((option) => !option)) return '选项内容不能为空'

  const lowered = normalizedOptions.map((option) => option.toLocaleLowerCase())
  if (new Set(lowered).size !== lowered.length) return '选项内容不能重复'

  const letters = parseAnswerLetters(answer)
  if (type === 'single' && letters.length !== 1) return '单选题必须设置一个正确答案'
  if (type === 'multiple' && letters.length < 2) return '多选题至少需要两个正确答案'

  const validLetters = new Set(normalizedOptions.map((_, index) => optionLetter(index)))
  if (letters.some((letter) => !validLetters.has(letter))) return '正确答案必须对应现有选项'
  return null
}

export function serializeOptions(options) {
  return JSON.stringify(
    options.map((option, index) => `${optionLetter(index)}. ${String(option).trim()}`),
  )
}
