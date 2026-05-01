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
          path: 'resource/vendor',
          name: 'VendorConfig',
          component: () => import('../views/resource/VendorConfig.vue'),
        },
        {
          path: 'resource/model',
          name: 'ModelManage',
          component: () => import('../views/resource/ModelManage.vue'),
        },
      ],
    },
  ],
})

export default router
