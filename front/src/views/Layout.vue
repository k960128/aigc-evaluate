<script setup>
import {
  DashboardOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)

const selectedKeys = computed(() => {
  const path = route.path
  if (path.includes('/resource/vendor'))
    return ['resource-vendor']
  if (path.includes('/resource/model'))
    return ['resource-model']
  return ['home']
})

const openKeys = computed(() => {
  const path = route.path
  if (path.includes('/resource'))
    return ['resource']
  return []
})

function handleMenuClick({ key }) {
  const map = {
    home: '/',
    'resource-vendor': '/resource/vendor',
    'resource-model': '/resource/model',
  }
  if (map[key])
    router.push(map[key])
}

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}
</script>

<template>
  <a-layout class="app-layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="220"
      :collapsed-width="80"
      :trigger="null"
      collapsible
      class="app-sider"
    >
      <div class="sider-logo">
        <div class="logo-icon">
          <DashboardOutlined />
        </div>
        <span v-show="!collapsed" class="logo-text">大模型安全评测平台</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedKeys"
        v-model:openKeys="openKeys"
        mode="inline"
        :inline-collapsed="collapsed"
        class="sider-menu"
        @click="handleMenuClick"
      >
        <a-menu-item key="home">
          <DashboardOutlined />
          <span>首页</span>
        </a-menu-item>
        <a-sub-menu key="resource">
          <template #icon>
            <SettingOutlined />
          </template>
          <template #title>
            <span>资源中心</span>
          </template>
          <a-menu-item key="resource-vendor">
            <span>厂商基础配置</span>
          </a-menu-item>
          <a-menu-item key="resource-model">
            <span>模型管理</span>
          </a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="app-header">
        <div class="header-left">
          <span class="trigger" @click="toggleCollapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </span>
          <span class="header-title">{{ route.meta?.title || '首页' }}</span>
        </div>
        <div class="header-right">
          <span class="header-user">管理员</span>
        </div>
      </a-layout-header>

      <a-layout-content class="app-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-sider {
  background: #fff !important;
  border-right: 1px solid var(--gray-200);
  box-shadow: 2px 0 6px rgba(0, 0, 0, 0.02);
}

.app-sider :deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
}

.sider-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid var(--gray-200);
  gap: 10px;
  flex-shrink: 0;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, var(--primary-blue), var(--primary-blue-active));
  border-radius: 8px;
  color: #fff;
  font-size: 18px;
  flex-shrink: 0;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-800);
  white-space: nowrap;
  overflow: hidden;
}

.sider-menu {
  border-right: none !important;
  flex: 1;
  padding-top: 8px;
}

.sider-menu :deep(.ant-menu-item-selected) {
  background: var(--primary-blue-light) !important;
  color: var(--primary-blue) !important;
}

.sider-menu :deep(.ant-menu-item-selected::after) {
  border-right-color: var(--primary-blue) !important;
}

.app-header {
  background: #fff !important;
  height: 56px;
  line-height: 56px;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--gray-200);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.trigger {
  font-size: 18px;
  cursor: pointer;
  color: var(--gray-600);
  transition: color 0.2s;
  display: flex;
  align-items: center;
}

.trigger:hover {
  color: var(--primary-blue);
}

.header-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-800);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-user {
  font-size: 14px;
  color: var(--gray-600);
}

.app-content {
  padding: 24px;
  background: var(--gray-50);
  min-height: calc(100vh - 56px);
}
</style>
