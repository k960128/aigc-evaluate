<script setup>
import {
  CloudServerOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const statsCards = [
  {
    title: '模型厂商',
    value: '-',
    icon: CloudServerOutlined,
    color: '#1677ff',
    bgColor: '#e6f4ff',
  },
  {
    title: '模型数量',
    value: '-',
    icon: RocketOutlined,
    color: '#52c41a',
    bgColor: '#f6ffed',
  },
  {
    title: '评测任务',
    value: '-',
    icon: SafetyCertificateOutlined,
    color: '#722ed1',
    bgColor: '#f9f0ff',
  },
  {
    title: '拦截规则',
    value: '-',
    icon: SettingOutlined,
    color: '#fa8c16',
    bgColor: '#fff7e6',
  },
]

const quickActions = [
  { title: '厂商基础配置', desc: '配置大模型服务商的网络与启用状态', path: '/resource/vendor' },
  { title: '模型管理', desc: '管理大模型实例与参数配置', path: '/resource/model' },
]

function goPage(path) {
  router.push(path)
}
</script>

<template>
  <div class="home-page">
    <div class="welcome-section">
      <h2>欢迎使用大模型安全评测平台</h2>
      <p>一站式大模型安全风险评估与评测管理平台</p>
    </div>

    <div class="stats-grid">
      <div v-for="card in statsCards" :key="card.title" class="stat-card card-hover-transition">
        <div class="stat-icon" :style="{ background: card.bgColor, color: card.color }">
          <component :is="card.icon" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-title">{{ card.title }}</div>
        </div>
      </div>
    </div>

    <div class="quick-section">
      <h3 class="section-title">快捷入口</h3>
      <div class="quick-grid">
        <div
          v-for="action in quickActions"
          :key="action.title"
          class="quick-card card-hover-transition"
          @click="goPage(action.path)"
        >
          <div class="quick-card-title">{{ action.title }}</div>
          <div class="quick-card-desc">{{ action.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  max-width: 1200px;
}

.welcome-section {
  margin-bottom: 32px;
}

.welcome-section h2 {
  font-size: 24px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.welcome-section p {
  font-size: 14px;
  color: var(--gray-500);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--gray-200);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--gray-800);
  line-height: 1.2;
}

.stat-title {
  font-size: 14px;
  color: var(--gray-500);
  margin-top: 4px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--gray-800);
  margin-bottom: 16px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.quick-card {
  background: #fff;
  border-radius: var(--radius-lg);
  padding: 24px;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--gray-200);
}

.quick-card:hover {
  border-color: var(--primary-blue);
}

.quick-card-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-800);
  margin-bottom: 8px;
}

.quick-card-desc {
  font-size: 14px;
  color: var(--gray-500);
}
</style>
