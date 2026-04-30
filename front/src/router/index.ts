import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'Home',
        component: () => import('../views/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'resource/vendor',
        name: 'VendorConfig',
        component: () => import('../views/resource/VendorConfig.vue'),
        meta: { title: '厂商基础配置' },
      },
      {
        path: 'resource/model',
        name: 'ModelManage',
        component: () => import('../views/resource/ModelManage.vue'),
        meta: { title: '模型管理' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
