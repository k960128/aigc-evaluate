import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '../utils/auth'
import Layout from '../views/Layout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
    },
    {
      path: '/',
      component: Layout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'Home',
          component: () => import('../views/Home.vue'),
        },
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('../views/Dashboard.vue'),
        },
        {
          path: 'task',
          name: 'Task',
          component: () => import('../views/Task.vue'),
        },
        {
          path: 'task/:id',
          name: 'TaskDetail',
          component: () => import('../views/TaskDetail.vue'),
        },
        {
          path: 'report',
          name: 'Report',
          component: () => import('../views/Report.vue'),
        },
        {
          path: 'dataset',
          name: 'DatasetManage',
          component: () => import('../views/DatasetManage.vue'),
        },
        {
          path: 'risk-tags',
          name: 'RiskTagManage',
          component: () => import('../views/RiskTagManage.vue'),
        },
        {
          path: 'user',
          name: 'UserManage',
          component: () => import('../views/UserManage.vue'),
        },
        {
          path: 'knowledge/semantic',
          name: 'SemanticKnowledge',
          component: () => import('../views/resource/KnowledgeManage.vue'),
        },
        {
          path: 'knowledge/risk-features',
          name: 'RiskFeatureLibrary',
          component: () => import('../views/resource/RiskFeatureLibrary.vue'),
        },
        {
          path: 'resource/vendor',
          name: 'VendorConfig',
          component: () => import('../views/resource/VendorConfig.vue'),
        },
        {
          path: 'resource/model',
          name: 'ModelManage',
          component: () => import('../views/resource/ModelManage.vue'),
        },
        {
          path: 'resource/knowledge',
          name: 'KnowledgeManage',
          redirect: '/knowledge/semantic',
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const hasToken = isAuthenticated()

  if (to.path === '/login') {
    return hasToken ? '/home' : true
  }

  if (!hasToken) {
    return '/login'
  }

  return true
})

export default router
