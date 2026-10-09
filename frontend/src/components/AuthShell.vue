<template>
  <div class="auth-page" :class="{ 'auth-page--register': register }">
    <header class="auth-nav">
      <router-link to="/login" class="brand"><el-icon :size="28"><Reading /></el-icon>EduExam</router-link>
      <span class="auth-nav-caption">在线题库与考试管理系统</span>
    </header>
    <main class="auth-body">
      <section class="auth-intro" aria-label="欢迎使用 EduExam">
        <h2>专注学习。<br><span>从容应考。</span></h2>
        <p>从知识整理到学习检验，将题库、课程与考试，汇集在一处。</p>
        <div class="auth-features">
          <span><el-icon><Document /></el-icon>有序的题库</span>
          <span><el-icon><Notebook /></el-icon>清晰的考试</span>
          <span><el-icon><TrendCharts /></el-icon>可见的进步</span>
        </div>
      </section>
      <section class="auth-panel">
        <h1>{{ register ? '注册账号' : '欢迎回来' }}</h1>
        <p class="auth-panel-subtitle">{{ register ? '创建你的账号，开始教学与学习。' : '登录 EduExam，继续你的学习与教学。' }}</p>
        <slot />
      </section>
    </main>
    <footer class="auth-footer">EduExam · 让知识的每一步，都更清晰。</footer>
  </div>
</template>
<script setup>
defineProps({ register: Boolean })
</script>
