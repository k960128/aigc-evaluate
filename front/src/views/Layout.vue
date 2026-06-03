<script setup lang="ts">
import {
  AppstoreOutlined,
  BellOutlined,
  BookOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DownOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TagsOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '../stores/app'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

const collapsed = computed({
  get: () => appStore.collapsed,
  set: (val) => { appStore.collapsed = val },
})

const selectedKeys = ref([route.path])
const openKeys = ref(['resource'])

// 路由变化时同步选中状态
watch(() => route.path, (path) => {
  selectedKeys.value = [path]
  if (path.startsWith('/resource')) {
    openKeys.value = ['resource']
  }
  else if (path.startsWith('/knowledge')) {
    openKeys.value = ['knowledge']
  }
  else if (path.startsWith('/task') || path.startsWith('/report')) {
    openKeys.value = ['evaluation']
  }
  else {
    openKeys.value = []
  }
}, { immediate: true })

function toggleSidebar() {
  appStore.toggleSidebar()
}

function handleMenuClick({ key }: { key: string | number }) {
  router.push(String(key))
}
</script>

<template>
  <a-layout class="app-layout">
    <!-- 顶部导航 -->
    <a-layout-header class="app-header">
      <div class="header-left">
        <div class="logo-area" @click="router.push('/home')">
          <SafetyCertificateOutlined class="logo-icon" />
          <span v-show="!collapsed" class="logo-text">大模型安全评测平台</span>
        </div>
      </div>
      <div class="header-right">
        <a-tooltip title="文档">
          <a-button type="text" class="header-action-btn">
            <QuestionCircleOutlined />
          </a-button>
        </a-tooltip>
        <a-tooltip title="通知">
          <a-badge :count="3" size="small">
            <a-button type="text" class="header-action-btn">
              <BellOutlined />
            </a-button>
          </a-badge>
        </a-tooltip>
        <a-dropdown>
          <div class="user-area">
            <a-avatar :size="30" class="user-avatar">
              A
            </a-avatar>
            <span class="user-name">Admin</span>
            <DownOutlined class="user-arrow" />
          </div>
          <template #overlay>
            <a-menu>
              <a-menu-item key="profile">
                <UserOutlined />
                <span style="margin-left: 8px">个人设置</span>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">
                <LogoutOutlined />
                <span style="margin-left: 8px">退出登录</span>
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>

    <a-layout class="app-body">
      <!-- 左侧侧边栏 -->
      <a-layout-sider
        v-model:collapsed="collapsed"
        :trigger="null"
        collapsible
        :width="220"
        :collapsed-width="72"
        class="app-sidebar"
        theme="light"
      >
        <a-menu
          v-model:selected-keys="selectedKeys"
          v-model:open-keys="openKeys"
          mode="inline"
          :style="{ border: 'none' }"
          @click="handleMenuClick"
        >
          <a-menu-item key="/home">
            <template #icon>
              <HomeOutlined />
            </template>
            <span>首页</span>
          </a-menu-item>

          <a-menu-item key="/dashboard">
            <template #icon>
              <DashboardOutlined />
            </template>
            <span>评测大盘</span>
          </a-menu-item>

          <a-sub-menu key="evaluation">
            <template #icon>
              <SafetyCertificateOutlined />
            </template>
            <template #title>
              安全评测
            </template>
            <a-menu-item key="/task">
              <template #icon>
                <ExperimentOutlined />
              </template>
              <span>评测任务</span>
            </a-menu-item>
            <a-menu-item key="/report">
              <template #icon>
                <FileTextOutlined />
              </template>
              <span>报告中心</span>
            </a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="resource">
            <template #icon>
              <AppstoreOutlined />
            </template>
            <template #title>
              资源中心
            </template>
            <a-menu-item key="/resource/vendor">
              <template #icon>
                <SettingOutlined />
              </template>
              <span>厂商基础配置</span>
            </a-menu-item>
            <a-menu-item key="/resource/model">
              <template #icon>
                <RobotOutlined />
              </template>
              <span>模型管理</span>
            </a-menu-item>
          </a-sub-menu>

          <a-menu-item key="/dataset">
            <template #icon>
              <DatabaseOutlined />
            </template>
            <span>数据集管理</span>
          </a-menu-item>

          <a-menu-item key="/risk-tags">
            <template #icon>
              <TagsOutlined />
            </template>
            <span>风险标签</span>
          </a-menu-item>

          <a-menu-item key="/user">
            <template #icon>
              <TeamOutlined />
            </template>
            <span>用户管理</span>
          </a-menu-item>

          <a-sub-menu key="knowledge">
            <template #icon>
              <BookOutlined />
            </template>
            <template #title>
              知识库管理
            </template>
            <a-menu-item key="/knowledge/semantic">
              <template #icon>
                <BookOutlined />
              </template>
              <span>语义知识库</span>
            </a-menu-item>
          </a-sub-menu>
        </a-menu>

        <!-- 折叠按钮 -->
        <div class="sidebar-trigger" @click="toggleSidebar">
          <MenuFoldOutlined v-if="!collapsed" />
          <MenuUnfoldOutlined v-else />
        </div>
      </a-layout-sider>

      <!-- 主内容区 -->
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

/* 顶部导航 */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px 0 0;
  height: 56px;
  line-height: 56px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  z-index: 100;
  position: sticky;
  top: 0;
}

.header-left {
  display: flex;
  align-items: center;
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  height: 56px;
  cursor: pointer;
  transition: background 0.2s;
}

.logo-area:hover {
  background: #f5f5f5;
}

.logo-icon {
  font-size: 22px;
  color: #1677ff;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
  white-space: nowrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header-action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #595959;
  border-radius: 8px;
}

.header-action-btn:hover {
  background: #f5f5f5;
  color: #1677ff;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 8px;
  margin-left: 8px;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-area:hover {
  background: #f5f5f5;
}

.user-avatar {
  background: #1677ff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
  color: #262626;
}

.user-arrow {
  font-size: 10px;
  color: #8c8c8c;
}

/* 侧边栏 */
.app-sidebar {
  background: #fff !important;
  border-right: 1px solid #f0f0f0;
  position: relative;
}

.app-sidebar :deep(.ant-menu) {
  background: transparent;
}

.app-sidebar :deep(.ant-menu-item-selected) {
  background: #e6f4ff;
  color: #1677ff;
}

.app-sidebar :deep(.ant-menu-item-selected .anticon) {
  color: #1677ff;
}

.app-sidebar :deep(.ant-menu-item:hover) {
  color: #1677ff;
}

.app-sidebar :deep(.ant-menu-submenu-title:hover) {
  color: #1677ff;
}

.app-sidebar :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title) {
  color: #262626;
}

.app-sidebar :deep(.ant-menu-submenu-selected > .ant-menu-submenu-title .anticon) {
  color: #595959;
}

.sidebar-trigger {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  color: #8c8c8c;
  transition: all 0.2s;
}

.sidebar-trigger:hover {
  background: #f5f5f5;
  color: #1677ff;
}

/* 主内容区 */
.app-content {
  background: #f5f5f5;
  overflow-y: auto;
}

.app-body {
  min-height: calc(100vh - 56px);
}

/* 响应式 */
@media (max-width: 768px) {
  .app-sidebar {
    position: fixed !important;
    left: 0;
    top: 56px;
    bottom: 0;
    z-index: 99;
    box-shadow: 4px 0 12px rgba(0, 0, 0, 0.08);
  }

  .user-name {
    display: none;
  }

  .logo-text {
    display: none;
  }
}
</style>
