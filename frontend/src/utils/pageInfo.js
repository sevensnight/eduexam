// Presentation-only metadata. Existing route paths and access rules stay in router/index.js.
export function getPageInfo(path) {
  const pages = {
    '/dashboard': ['控制台', '教学与学习，从这里开始。'],
    '/questions': ['题库管理', '整理知识，积累每一道好题。'],
    '/questions/create': ['新建题目', '完善题目内容、答案与解析。'],
    '/categories': ['分类管理', '用清晰的分类与标签，组织你的知识库。'],
    '/courses': ['课程管理', '管理课程、班级与学生的学习安排。'],
    '/exams': ['考试列表', '查看考试安排，掌握每一场考试的进展。'],
    '/exams/create': ['创建考试', '从题库选题，设置考试范围与时间。'],
    '/my-records': ['我的成绩', '回顾每一次作答，见证每一步进步。'],
    '/wrong-book': ['错题本', '从错题中找到方向，让知识更扎实。'],
    '/stats': ['统计分析', '从数据中理解学习表现与知识掌握情况。'],
    '/admin/users': ['用户管理', '管理系统账号、角色与班级信息。'],
    '/profile': ['个人资料', '更新个人信息，管理账号安全。'],
  }
  if (pages[path]) return { title: pages[path][0], description: pages[path][1] }
  const end = path.split('/').pop()
  const titles = { edit: '编辑', stats: '考试统计', grade: '试卷批改', take: '参加考试', result: '考试结果', leaderboard: '排行榜' }
  return { title: end === 'edit' ? (path.startsWith('/questions') ? '编辑题目' : '编辑考试') : titles[end] || 'EduExam', description: '' }
}
