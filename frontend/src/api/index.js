import request from './request'

export const authApi = {
  register: (data) => request.post('/auth/register', data),
  login: (data) => request.post('/auth/login', data),
  me: () => request.get('/auth/me'),
  updateProfile: (data) => request.put('/auth/profile', data),
  changePassword: (data) => request.put('/auth/password', data),
}

export const categoryApi = {
  list: () => request.get('/categories'),
  create: (data) => request.post('/categories', data),
  update: (id, data) => request.put(`/categories/${id}`, data),
  remove: (id) => request.delete(`/categories/${id}`),
  reorder: (data) => request.put('/categories/reorder', data),
}

export const tagApi = {
  list: () => request.get('/tags'),
  create: (name, color) => request.post(`/tags?name=${encodeURIComponent(name)}&color=${encodeURIComponent(color)}`),
  remove: (id) => request.delete(`/tags/${id}`),
}

export const questionApi = {
  list: (params) => request.get('/questions', { params }),
  export: (params) => request.get('/questions/export', { params, responseType: 'blob' }),
  import: (data) => request.post('/questions/import', data),
  get: (id) => request.get(`/questions/${id}`),
  create: (data) => request.post('/questions', data),
  update: (id, data) => request.put(`/questions/${id}`, data),
  remove: (id) => request.delete(`/questions/${id}`),
}

export const examApi = {
  list: (params) => request.get('/exams', { params }),
  get: (id) => request.get(`/exams/${id}`),
  create: (data) => request.post('/exams', data),
  update: (id, data) => request.put(`/exams/${id}`, data),
  remove: (id) => request.delete(`/exams/${id}`),
  start: (id) => request.post(`/exams/${id}/start`),
  submit: (id, data) => request.post(`/exams/${id}/submit`, data),
  leaderboard: (id) => request.get(`/exams/${id}/leaderboard`),
  myRecord: (id) => request.get(`/exams/${id}/my-record`),
  records: (id) => request.get(`/exams/${id}/records`),
  grade: (id, recordId, data) => request.put(`/exams/${id}/records/${recordId}/grade`, data),
  exportGrades: (id, params) => request.get(`/exams/${id}/grades/export`, { params, responseType: 'blob' }),
  importGrades: (id, data) => request.post(`/exams/${id}/grades/import`, data),
}

export const courseApi = {
  list: () => request.get('/courses'),
  classes: () => request.get('/courses/classes'),
  students: (params) => request.get('/courses/students', { params }),
  get: (id) => request.get(`/courses/${id}`),
  create: (data) => request.post('/courses', data),
  update: (id, data) => request.put(`/courses/${id}`, data),
  enroll: (id, studentIds) => request.put(`/courses/${id}/enrollments`, { student_ids: studentIds }),
  remove: (id) => request.delete(`/courses/${id}`),
}

export const statsApi = {
  categories: () => request.get('/stats/categories'),
  exam: (id) => request.get(`/stats/exam/${id}`),
  insights: (id) => request.get(`/stats/exam/${id}/insights`),
  examCategoryDist: (id) => request.get(`/stats/exam/${id}/category-distribution`),
}

export const notificationApi = {
  unreadCount: () => request.get('/notifications/unread-count'),
  list: () => request.get('/notifications'),
  readAll: () => request.put('/notifications/read-all'),
}

export const studentApi = {
  myRecords: () => request.get('/student/my-records'),
  wrongBook: () => request.get('/student/wrong-book'),
  wrongBookIds: () => request.get('/student/wrong-book/ids'),
  wrongBookCategoryStats: () => request.get('/student/wrong-book/category-stats'),
  addWrong: (qid) => request.post(`/student/wrong-book/${qid}`),
  removeWrong: (qid) => request.delete(`/student/wrong-book/${qid}`),
}

export const userApi = {
  list: (params) => request.get('/users', { params }),
  exportStudents: (params) => request.get('/users/export', { params, responseType: 'blob' }),
  importStudents: (data) => request.post('/users/import/students', data),
  setRole: (id, role) => request.put(`/users/${id}/role?role=${role}`),
  toggleActive: (id) => request.put(`/users/${id}/toggle-active`),
}
