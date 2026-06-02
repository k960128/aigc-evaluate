import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
          path: 'report',
          name: 'Report',
          component: () => import('../views/Report.vue'),
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
          component: () => import('../views/resource/KnowledgeManage.vue'),
        },
      ],
    },
  ],
})

export default router
