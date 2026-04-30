<script setup lang="ts">
import {
  DesktopOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue'
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const selectedKeys = ref<string[]>(['task-list'])
const collapsed = ref(false)

const menuItems = [
  { key: 'task-list', icon: DesktopOutlined, label: '任务管理', path: '/task' },
  { key: 'task-create', icon: PlusOutlined, label: '创建任务', path: '/task/create' },
]

function handleMenuClick({ key }: { key: string | number }) {
  const item = menuItems.find(m => m.key === String(key))
  if (item)
    router.push(item.path)
}

function navigateHome() {
  router.push('/')
}
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible>
      <div class="logo" @click="navigateHome">
        <h2 v-if="!collapsed">
          AIGC Eval
        </h2>
        <h2 v-else>
          AE
        </h2>
      </div>
      <a-menu
        v-model:selected-keys="selectedKeys"
        theme="dark"
        mode="inline"
        @click="handleMenuClick"
      >
        <a-menu-item v-for="item in menuItems" :key="item.key">
          <component :is="item.icon" />
          <span>{{ item.label }}</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header style="background: #fff; padding: 0 24px; display: flex; align-items: center;">
        <span style="font-size: 16px; font-weight: 500;">AIGC 评测平台</span>
      </a-layout-header>
      <a-layout-content style="margin: 24px 16px; padding: 24px; background: #fff; border-radius: 8px; min-height: 280px;">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.logo h2 {
  color: #fff;
  margin: 0;
  font-size: 18px;
  white-space: nowrap;
}
</style>
