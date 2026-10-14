import { displayQuestionAnswer } from './listPresentation'

export function recordState(record = {}) {
  if (record.status === 'in_progress') return { label: '进行中', tone: 'primary', score: '尚未提交' }
  if (record.status === 'submitted' || !record.essay_graded) return { label: '待批改', tone: 'warning', score: '待批改' }
  if (!record.result_released) return { label: '未公布', tone: 'info', score: '暂未公布' }
  return { label: record.passed === true ? '及格' : record.passed === false ? '不及格' : '已出分', tone: record.passed === false ? 'danger' : 'success', score: record.total_score ?? '未提供' }
}

export function reviewQuestion(row = {}) {
  return {
    ...row,
    title: row.question_title || row.title || '题目 #' + row.question_id,
    question_type: row.question_type || row.type || '',
    correct_answer: row.correct_answer ?? row.answer ?? null,
    max_score: row.max_score ?? row.score ?? null,
  }
}

export function reviewAnswer(answer, type, fallback = '未作答') {
  if (answer == null || String(answer).trim() === '') return fallback
  return displayQuestionAnswer({ question_type: type, answer })
}

export function percentage(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.round(Math.max(0, Math.min(1, number)) * 1000) / 10 : 0
}

export function moveExamQuestion(questions, from, to) {
  if (from < 0 || to < 0 || from >= questions.length || to >= questions.length) return questions
  const result = [...questions]
  result.splice(to, 0, result.splice(from, 1)[0])
  return result.map((question, index) => ({ ...question, order_num: index + 1 }))
}

export function validateExamSettings(form) {
  if (!form.title?.trim()) return '请输入考试名称'
  if (!Number.isInteger(form.duration_minutes) || form.duration_minutes < 1 || form.duration_minutes > 1440) return '考试时长需为 1–1440 分钟的整数'
  if (form.questions.some(q => !Number.isFinite(q.score) || q.score <= 0)) return '请为每道题设置大于 0 的有效分值'
  const total = form.questions.reduce((sum, q) => sum + q.score, 0)
  if (!Number.isFinite(form.pass_score) || form.pass_score < 0 || form.pass_score > total) return '及格分必须在 0 到总分之间'
  if (form.allow_retake && (!Number.isInteger(form.max_attempts) || form.max_attempts < 1 || form.max_attempts > 100)) return '参考次数需为 1–100 次的整数'
  if (form.open_time && form.close_time && new Date(form.close_time).getTime() <= new Date(form.open_time).getTime()) return '关闭时间必须晚于开放时间'
  return ''
}
