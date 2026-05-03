<template>
  <div class="dashboard-page">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
      <a-breadcrumb>
        <a-breadcrumb-item>首页</a-breadcrumb-item>
        <a-breadcrumb-item>评测大盘</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <div class="page-content">
      <!-- 页面标题 -->
      <div class="page-header">
        <div>
          <h2 class="page-title">全局安全态势感知</h2>
          <p class="page-desc">实时监控各模型安全评测状态与风险拦截情况</p>
        </div>
        <div class="header-actions">
          <a-button @click="refreshData">
            <template #icon><ReloadOutlined /></template>
            刷新数据
          </a-button>
          <a-button type="primary" @click="goToTaskPage">
            <template #icon><PlusOutlined /></template>
            新建评测任务
          </a-button>
        </div>
      </div>

      <!-- 核心指标卡片 -->
      <div class="section-header">
        <h3 class="section-title">核心指标</h3>
        <span class="section-desc">平台整体安全态势概览</span>
      </div>
      <div class="metric-grid">
        <div v-for="item in coreMetrics" :key="item.key" class="metric-card card-hover-transition">
          <div class="metric-icon-wrap" :style="{ background: item.bgColor }">
            <component :is="item.icon" class="metric-icon" :style="{ color: item.iconColor }" />
          </div>
          <div class="metric-info">
            <div class="metric-label">{{ item.label }}</div>
            <div class="metric-value">
              {{ item.value }}
              <span class="metric-unit" v-if="item.unit">/{{ item.unit }}</span>
            </div>
            <div class="metric-trend" :class="item.trendUp ? 'trend-up' : 'trend-down'">
              <ArrowUpOutlined v-if="item.trendUp" />
              <ArrowDownOutlined v-else />
              <span>{{ item.trend }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 安全维度拦截率 -->
      <div class="section-header" style="margin-top: 28px">
        <h3 class="section-title">安全维度拦截率</h3>
        <span class="section-desc">各安全维度的平均拦截率统计</span>
      </div>
      <div class="dimension-section">
        <div v-for="item in dimensions" :key="item.key" class="dimension-card">
          <div class="dimension-header">
            <div class="dimension-title">
              <component :is="item.icon" class="dimension-icon" :style="{ color: item.iconColor }" />
              <span>{{ item.name }}</span>
            </div>
            <div class="dimension-score" :style="{ color: item.scoreColor }">
              {{ item.score }}%
            </div>
          </div>
          <div class="dimension-progress">
            <div class="progress-bar-bg">
              <div class="progress-bar-fill" :class="item.scoreClass" :style="{ width: item.score + '%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 模型安全榜单 -->
      <div class="section-header" style="margin-top: 28px">
        <h3 class="section-title">模型安全榜单 TOP 5</h3>
        <span class="section-desc">各模型安全评分排名</span>
      </div>
      <div class="ranking-section">
        <div v-for="(model, index) in topModels" :key="model.name" class="ranking-item">
          <div class="ranking-rank">
            <span :class="['rank-number', getRankClass(index)]">{{ index + 1 }}</span>
          </div>
          <div class="ranking-info">
            <div class="model-name">{{ model.name }}</div>
            <div class="model-vendor">{{ model.vendor }}</div>
          </div>
          <div class="ranking-score">
            <span class="score-value">{{ model.score }}</span>
            <span class="score-label">分</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  SafetyCertificateOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  BugOutlined,
  LockOutlined,
  UnlockOutlined,
  ReloadOutlined,
  PlusOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const coreMetrics = [
  {
    key: 'score',
    label: '平台平均安全分',
    value: '86.5',
    unit: '100',
    icon: SafetyCertificateOutlined,
    iconColor: '#52c41a',
    bgColor: '#f6ffed',
    trend: '2.4%',
    trendUp: true,
  },
  {
    key: 'models',
    label: '已接入评测模型',
    value: '12',
    unit: '个',
    icon: TeamOutlined,
    iconColor: '#1677ff',
    bgColor: '#e6f4ff',
    trend: '覆盖 OpenAI, 阿里，智谱等',
    trendUp: true,
  },
  {
    key: 'tasks',
    label: '累计执行任务',
    value: '248',
    unit: '次',
    icon: ThunderboltOutlined,
    iconColor: '#722ed1',
    bgColor: '#f9f0ff',
    trend: '本周新增 15 次全量扫描',
    trendUp: true,
  },
  {
    key: 'risks',
    label: '拦截高危风险',
    value: '1402',
    unit: '个',
    icon: BugOutlined,
    iconColor: '#ff4d4f',
    bgColor: '#fff2f0',
    trend: '主要集中在"越狱攻击"维度',
    trendUp: false,
  },
]

const dimensions = [
  {
    key: 'toxicity',
    name: '有毒内容 (Toxicity)',
    icon: BugOutlined,
    score: 96,
    scoreColor: '#52c41a',
    iconColor: '#52c41a',
    scoreClass: 'progress-success',
  },
  {
    key: 'bias',
    name: '偏见与歧视 (Bias)',
    icon: SafetyCertificateOutlined,
    score: 92,
    scoreColor: '#52c41a',
    iconColor: '#1677ff',
    scoreClass: 'progress-success',
  },
  {
    key: 'privacy',
    name: '隐私泄露 (Privacy)',
    icon: LockOutlined,
    score: 85,
    scoreColor: '#faad14',
    iconColor: '#722ed1',
    scoreClass: 'progress-warning',
  },
  {
    key: 'jailbreak',
    name: '越狱与注入 (Jailbreak)',
    icon: UnlockOutlined,
    score: 68,
    scoreColor: '#ff4d4f',
    iconColor: '#ff4d4f',
    scoreClass: 'progress-danger',
  },
]

const topModels = [
  { name: 'gpt-4o-2024', vendor: 'OpenAI', score: 98.2 },
  { name: 'Claude 3.5 Sonnet', vendor: 'Anthropic', score: 97.5 },
  { name: 'qwen-max', vendor: '阿里云', score: 94.0 },
  { name: 'glm-4', vendor: '智谱 AI', score: 92.1 },
  { name: 'Llama-3-70b', vendor: 'Meta', score: 89.8 },
]

const getRankClass = (index: number) => {
  if (index === 0) return 'rank-gold'
  if (index === 1) return 'rank-silver'
  if (index === 2) return 'rank-bronze'
  return 'rank-normal'
}

const refreshData = () => {
  console.log('刷新数据')
}

const goToTaskPage = () => {
  router.push('/task')
}
</script>

<style scoped>
.dashboard-page {
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

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  flex-wrap: wrap;
  gap: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 13px;
  color: #8c8c8c;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
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

/* 指标卡片网格 */
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

.metric-label {
  font-size: 13px;
  color: #8c8c8c;
  margin-bottom: 4px;
}

.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: #262626;
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.metric-unit {
  font-size: 14px;
  font-weight: 400;
  color: #8c8c8c;
}

.metric-trend {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
  margin-top: 4px;
}

.trend-up {
  color: #52c41a;
}

.trend-down {
  color: #ff4d4f;
}

/* 安全维度拦截率 */
.dimension-section {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  padding: 20px;
}

.dimension-card {
  margin-bottom: 20px;
}

.dimension-card:last-child {
  margin-bottom: 0;
}

.dimension-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.dimension-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #262626;
}

.dimension-icon {
  font-size: 16px;
}

.dimension-score {
  font-size: 18px;
  font-weight: 700;
}

.progress-bar-bg {
  width: 100%;
  height: 8px;
  background: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.progress-success {
  background: #52c41a;
}

.progress-warning {
  background: #faad14;
}

.progress-danger {
  background: #ff4d4f;
}

/* 模型榜单 */
.ranking-section {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f0f0f0;
  padding: 20px;
}

.ranking-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: 10px;
  border-radius: 8px;
  transition: background 0.2s;
}

.ranking-item:hover {
  background: #fafafa;
}

.ranking-item:last-child {
  margin-bottom: 0;
}

.ranking-rank {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rank-number {
  font-size: 16px;
  font-weight: 700;
  font-style: italic;
}

.rank-gold {
  color: #faad14;
}

.rank-silver {
  color: #8c8c8c;
}

.rank-bronze {
  color: #d4880d;
}

.rank-normal {
  color: #d9d9d9;
}

.ranking-info {
  flex: 1;
  min-width: 0;
  margin-left: 12px;
}

.model-name {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  margin-bottom: 2px;
}

.model-vendor {
  font-size: 12px;
  color: #8c8c8c;
}

.ranking-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-value {
  font-size: 16px;
  font-weight: 700;
  color: #52c41a;
}

.score-label {
  font-size: 11px;
  color: #8c8c8c;
}

/* 响应式 */
@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .dashboard-page {
    padding: 16px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions :deep(.ant-btn) {
    flex: 1;
  }
}
</style>
