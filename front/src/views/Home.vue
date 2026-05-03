<template>
  <div class="home-page">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>首页</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <!-- 核心指标 -->
      <div class="section-header">
        <h3 class="section-title">核心指标</h3>
        <span class="section-desc">平台运营关键数据概览</span>
      </div>
      <div class="metric-grid">
        <div v-for="item in coreMetrics" :key="item.key" class="metric-card card-hover-transition">
          <div class="metric-icon-wrap" :style="{ background: item.bgColor }">
            <component :is="item.icon" class="metric-icon" :style="{ color: item.iconColor }" />
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
          <div class="metric-trend" :class="item.trendUp ? 'trend-up' : 'trend-down'">
            <ArrowUpOutlined v-if="item.trendUp" />
            <ArrowDownOutlined v-else />
            <span>{{ item.trend }}</span>
          </div>
        </div>
      </div>

      <!-- 评测指标 -->
      <div class="section-header" style="margin-top: 28px">
        <h3 class="section-title">评测指标</h3>
        <span class="section-desc">模型安全评测运行统计</span>
      </div>
      <div class="metric-grid">
        <div v-for="item in evalMetrics" :key="item.key" class="metric-card card-hover-transition">
          <div class="metric-icon-wrap" :style="{ background: item.bgColor }">
            <component :is="item.icon" class="metric-icon" :style="{ color: item.iconColor }" />
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ item.value }}</div>
            <div class="metric-label">{{ item.label }}</div>
          </div>
          <div class="metric-trend" :class="item.trendUp ? 'trend-up' : 'trend-down'">
            <ArrowUpOutlined v-if="item.trendUp" />
            <ArrowDownOutlined v-else />
            <span>{{ item.trend }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  TeamOutlined,
  MessageOutlined,
  CommentOutlined,
  BranchesOutlined,
  RobotOutlined,
  ThunderboltOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
} from '@ant-design/icons-vue'

const coreMetrics = [
  {
    key: 'active',
    label: '活跃数',
    value: '2847',
    icon: TeamOutlined,
    iconColor: '#1677ff',
    bgColor: '#e6f4ff',
    trend: '12.5%',
    trendUp: true,
  },
  {
    key: 'sessions',
    label: '会话数',
    value: '18432',
    icon: MessageOutlined,
    iconColor: '#722ed1',
    bgColor: '#f9f0ff',
    trend: '8.3%',
    trendUp: true,
  },
  {
    key: 'messages',
    label: '消息数',
    value: '156789',
    icon: CommentOutlined,
    iconColor: '#13c2c2',
    bgColor: '#e6fffb',
    trend: '15.2%',
    trendUp: true,
  },
  {
    key: 'depth',
    label: '会话深度',
    value: '8.5',
    icon: BranchesOutlined,
    iconColor: '#fa8c16',
    bgColor: '#fff7e6',
    trend: '2.1%',
    trendUp: false,
  },
]

const evalMetrics = [
  {
    key: 'models',
    label: '模型数量',
    value: '24',
    icon: RobotOutlined,
    iconColor: '#1677ff',
    bgColor: '#e6f4ff',
    trend: '4 新增',
    trendUp: true,
  },
  {
    key: 'evalCount',
    label: '评测次数',
    value: '3672',
    icon: ThunderboltOutlined,
    iconColor: '#722ed1',
    bgColor: '#f9f0ff',
    trend: '23.6%',
    trendUp: true,
  },
  {
    key: 'evalSuccess',
    label: '评测成功次数',
    value: '3421',
    icon: CheckCircleOutlined,
    iconColor: '#52c41a',
    bgColor: '#f6ffed',
    trend: '93.2%',
    trendUp: true,
  },
  {
    key: 'evalFail',
    label: '评测失败次数',
    value: '251',
    icon: CloseCircleOutlined,
    iconColor: '#ff4d4f',
    bgColor: '#fff2f0',
    trend: '6.8%',
    trendUp: false,
  },
]
</script>

<style scoped>
.home-page {
  padding: 24px;
}

.page-breadcrumb {
  margin-bottom: 20px;
}

.page-breadcrumb :deep(.ant-breadcrumb-link) {
  color: #8c8c8c;
  font-size: 13px;
}

.page-content {
  max-width: 1200px;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.section-desc {
  font-size: 13px;
  color: #8c8c8c;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.metric-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  border: 1px solid #f0f0f0;
}

.metric-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.metric-icon {
  font-size: 20px;
}

.metric-info {
  flex: 1;
  min-width: 0;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: #262626;
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.metric-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-top: 4px;
}

.metric-trend {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}

.trend-up {
  color: #52c41a;
}

.trend-down {
  color: #ff4d4f;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .home-page {
    padding: 16px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .metric-value {
    font-size: 20px;
  }
}
</style>
