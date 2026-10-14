import { parseAppDateTimeMs } from './examTime'

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
})

// Keep the server's Shanghai time zone and exact seconds, even on narrow screens.
export function listDateTime(value, fallback = '未设置') {
  const timestamp = parseAppDateTimeMs(value)
  if (!Number.isFinite(timestamp)) return { date: fallback, time: '' }
  const parts = Object.fromEntries(dateTimeFormatter.formatToParts(timestamp).map(({ type, value: part }) => [type, part]))
  return { date: `${parts.year}/${parts.month}/${parts.day}`, time: `${parts.hour}:${parts.minute}:${parts.second}` }
}

export const questionTypeLabel = (type) => ({ single: '单选', multiple: '多选', truefalse: '判断', essay: '简答' }[type] || type || '未设置')
export const difficultyLabel = (level) => ({ easy: '简单', medium: '中等', hard: '困难' }[level] || level || '未设置')
export const difficultyTone = (level) => ({ easy: 'success', medium: 'warning', hard: 'danger' }[level] || 'info')

export function displayQuestionAnswer(question) {
  const answer = question.answer
  if (answer == null || String(answer).trim() === '') return '暂未设置'
  if (question.question_type === 'truefalse') {
    const normalized = String(answer).trim().toLowerCase()
    if (normalized === 'true') return '正确'
    if (normalized === 'false') return '错误'
  }
  return String(answer)
}

export function examPrimaryAction(exam) {
  return exam.status === 'draft' ? { command: 'edit', label: '编辑' } : { command: 'stats', label: '统计' }
}
