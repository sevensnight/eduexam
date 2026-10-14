export const answerOptionLabels = {
  neutral: '', selected: '已选', correct: '选对', wrong: '选错', missed: '漏选', answer: '正确答案',
}

export function answerSelectionValues(answer, type) {
  if (answer == null || String(answer).trim() === '') return []
  if (type === 'truefalse') {
    const value = String(answer).trim().toLowerCase()
    return value === 'true' ? ['True'] : value === 'false' ? ['False'] : []
  }
  if (!['single', 'multiple'].includes(type)) return []
  const values = (Array.isArray(answer) ? answer : String(answer).split(','))
    .map(value => String(value).trim().toUpperCase())
    .filter(value => /^[A-Z]$/.test(value))
  return [...new Set(values)]
}

export function choiceOptions(raw, type) {
  if (type === 'truefalse') return [
    { value: 'True', marker: 'T', text: '正确' },
    { value: 'False', marker: 'F', text: '错误' },
  ]
  if (!['single', 'multiple'].includes(type)) return []
  try {
    const options = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!Array.isArray(options)) return []
    return options.flatMap((option, index) => {
      if (typeof option !== 'string' || !option.trim()) return []
      const prefix = option.match(/^\s*([A-Z])[.．、:：)）]\s*/i)
      const value = prefix ? prefix[1].toUpperCase() : String.fromCharCode(65 + index)
      return [{ value, marker: value, text: prefix ? option.slice(prefix[0].length) : option.trim() }]
    })
  } catch {
    return []
  }
}

// 成绩未公开时，不读取答案键，也不生成任何正确/错误状态或提示。
export function reviewOptionRows(question, revealAnswers = false) {
  const options = choiceOptions(question.options, question.question_type)
  const selected = new Set(answerSelectionValues(question.user_answer, question.question_type))
  const key = revealAnswers
    ? answerSelectionValues(question.correct_answer ?? question.answer, question.question_type) : []
  const available = new Set(options.map(option => option.value))
  const canGrade = key.length > 0 && key.every(value => available.has(value))
    && (question.question_type === 'multiple' || key.length === 1)
  const correct = new Set(canGrade ? key : [])
  return options.map(option => {
    const isSelected = selected.has(option.value)
    let state = isSelected ? 'selected' : 'neutral'
    if (canGrade) {
      if (isSelected) state = correct.has(option.value) ? 'correct' : 'wrong'
      else if (correct.has(option.value)) state = question.question_type === 'multiple' && selected.size ? 'missed' : 'answer'
    }
    return { ...option, selected: isSelected, state, label: answerOptionLabels[state] }
  })
}
