<script setup lang="ts">
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

function goPage(path: string) {
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
      <div v-for="card in statsCards" :key="card.title" class="stat-card">
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
          class="quick-card"
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
  padding: 24px;
  max-width: 1200px;
}

.welcome-section {
  margin-bottom: 32px;
}

.welcome-section h2 {
  font-size: 24px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
}

.welcome-section p {
  font-size: 14px;
  color: #8c8c8c;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 32px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid #f0f0f0;
  transition: box-shadow 0.3s;
}

.stat-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
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
  color: #262626;
  line-height: 1.2;
}

.stat-title {
  font-size: 14px;
  color: #8c8c8c;
  margin-top: 4px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 16px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.quick-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  cursor: pointer;
  border: 1px solid #f0f0f0;
  transition: border-color 0.2s, box-shadow 0.3s;
}

.quick-card:hover {
  border-color: #1677ff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.quick-card-title {
  font-size: 16px;
  font-weight: 500;
  color: #262626;
  margin-bottom: 8px;
}

.quick-card-desc {
  font-size: 14px;
  color: #8c8c8c;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .quick-grid {
    grid-template-columns: 1fr;
  }
}
</style>
