import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '../layouts/default.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: DefaultLayout,
      redirect: '/task',
      children: [
        {
          path: 'task',
          name: 'TaskList',
          component: () => import('../pages/task/index.vue'),
          meta: { title: '任务管理' },
        },
        {
          path: 'task/create',
          name: 'TaskCreate',
          component: () => import('../pages/task/create.vue'),
          meta: { title: '创建任务' },
        },
        {
          path: 'task/:id',
          name: 'TaskDetail',
          component: () => import('../pages/task/[id].vue'),
          meta: { title: '任务进度' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.title)
    document.title = `${to.meta.title} - AIGC Eval`
})

export default router
